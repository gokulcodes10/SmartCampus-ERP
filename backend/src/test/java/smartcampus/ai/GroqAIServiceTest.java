package smartcampus.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import smartcampus.entity.AIMessageRole;
import smartcampus.exception.AIUnavailableException;
import smartcampus.service.AICompletion;
import smartcampus.service.AICompletionRequest;
import smartcampus.service.AIChatMessage;
import smartcampus.service.AIModelInfo;
import smartcampus.service.GroqAIService;

/**
 * Unit tests for {@link GroqAIService} against {@link MockRestServiceServer} - no live
 * Groq endpoint, no {@code @SpringBootTest} context, no network. See the class javadoc
 * on {@link GroqAIService} for the honest-failure discipline these tests verify.
 *
 * <p>{@link GroqAIService} exposes a public secondary constructor taking a
 * {@link RestClient.Builder} specifically as a testability seam for this class (it
 * lives outside {@code smartcampus.service}, so a package-private constructor would not
 * be reachable here) - see that constructor's javadoc.
 */
class GroqAIServiceTest {

    private static final String BASE_URL = "http://localhost:9999/openai/v1";
    private static final List<String> DEFAULT_PREFERENCES =
            List.of("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b");

    private record Harness(GroqAIService service, MockRestServiceServer server) {}

    private static Harness harness(String apiKey, String configuredModel, List<String> preferences, long cacheMinutes) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        GroqAIService service =
                new GroqAIService("groq", apiKey, BASE_URL, configuredModel, preferences, cacheMinutes, 0.3, 2048, builder);
        return new Harness(service, server);
    }

    private static Harness harness(String configuredModel) {
        return harness("test-api-key", configuredModel, DEFAULT_PREFERENCES, 30);
    }

    private static List<AIChatMessage> chat() {
        return List.of(
                new AIChatMessage(AIMessageRole.SYSTEM, "You are a helpful assistant."),
                new AIChatMessage(AIMessageRole.USER, "What is 2+2?"));
    }

    private static String modelsBody(String... ids) {
        StringBuilder sb = new StringBuilder("{\"data\":[");
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"id\":\"").append(ids[i]).append("\",\"owned_by\":\"groq\",\"context_window\":8192}");
        }
        sb.append("]}");
        return sb.toString();
    }

    // ------------------------------------------------------------------
    // (a) blank key -> not configured, every call throws, zero HTTP requests
    // ------------------------------------------------------------------

    @Test
    void blankApiKey_isNotConfigured_andEveryCallThrowsWithoutAnyHttpRequest() {
        Harness h = harness("", "", DEFAULT_PREFERENCES, 30);

        assertThat(h.service().isConfigured()).isFalse();

        assertThatThrownBy(h.service()::resolveModel)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("AI_API_KEY");
        assertThatThrownBy(h.service()::listModels)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("AI_API_KEY");
        assertThatThrownBy(() -> h.service().complete(AICompletionRequest.text(chat())))
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("AI_API_KEY");

        h.server().verify();
    }

    // ------------------------------------------------------------------
    // (b) configured id present -> resolved as-is
    // ------------------------------------------------------------------

    @Test
    void resolveModel_configuredIdPresentInLiveList_returnsIt() {
        Harness h = harness("llama-3.1-8b-instant");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andRespond(withSuccess(
                        modelsBody("llama-3.1-8b-instant", "llama-3.3-70b-versatile"), MediaType.APPLICATION_JSON));

        assertThat(h.service().resolveModel()).isEqualTo("llama-3.1-8b-instant");
        h.server().verify();
    }

    // ------------------------------------------------------------------
    // (c) configured id absent -> throws, listing the real available ids
    // ------------------------------------------------------------------

    @Test
    void resolveModel_configuredIdAbsentFromLiveList_throwsAndListsAvailableIds() {
        Harness h = harness("retired-model");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(modelsBody("llama-3.1-8b-instant", "openai/gpt-oss-120b"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(h.service()::resolveModel)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("retired-model")
                .hasMessageContaining("llama-3.1-8b-instant")
                .hasMessageContaining("openai/gpt-oss-120b");
        h.server().verify();
    }

    // ------------------------------------------------------------------
    // (d) blank configured model -> first available PREFERENCE wins (not first in the
    // provider's listing order)
    // ------------------------------------------------------------------

    @Test
    void resolveModel_blankConfiguredModel_picksFirstAvailablePreferenceInPreferenceOrder() {
        Harness real = harness("test-api-key", "", DEFAULT_PREFERENCES, 30);
        // Response lists the third preference before the second, to prove preference
        // order (not response order) decides the winner.
        real.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(
                        modelsBody("openai/gpt-oss-120b", "llama-3.1-8b-instant"), MediaType.APPLICATION_JSON));

        assertThat(real.service().resolveModel()).isEqualTo("llama-3.1-8b-instant");
        real.server().verify();
    }

    // ------------------------------------------------------------------
    // (e) caching - a second resolveModel() call issues no second /models request
    // ------------------------------------------------------------------

    @Test
    void resolveModel_secondCall_usesCache_noSecondModelsRequest() {
        Harness h = harness("llama-3.1-8b-instant");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(modelsBody("llama-3.1-8b-instant"), MediaType.APPLICATION_JSON));

        assertThat(h.service().resolveModel()).isEqualTo("llama-3.1-8b-instant");
        assertThat(h.service().resolveModel()).isEqualTo("llama-3.1-8b-instant");
        assertThat(h.service().getResolvedModelOrNull()).isEqualTo("llama-3.1-8b-instant");

        h.server().verify(); // fails if a second /models request was attempted
    }

    // ------------------------------------------------------------------
    // (f) complete() sends Authorization Bearer, the resolved model, lowercase roles,
    // and maps content + the three usage counters
    // ------------------------------------------------------------------

    @Test
    void complete_sendsAuthAndResolvedModelAndLowercaseRoles_mapsContentAndUsage() {
        Harness h = harness("llama-3.1-8b-instant");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(modelsBody("llama-3.1-8b-instant"), MediaType.APPLICATION_JSON));
        h.server()
                .expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.model").value("llama-3.1-8b-instant"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].role").value("user"))
                .andRespond(withSuccess(
                        "{\"model\":\"llama-3.1-8b-instant\",\"choices\":[{\"message\":{\"role\":\"assistant\","
                                + "\"content\":\"4\"}}],\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2,"
                                + "\"total_tokens\":12}}",
                        MediaType.APPLICATION_JSON));

        AICompletion completion = h.service().complete(AICompletionRequest.text(chat()));

        assertThat(completion.content()).isEqualTo("4");
        assertThat(completion.model()).isEqualTo("llama-3.1-8b-instant");
        assertThat(completion.promptTokens()).isEqualTo(10);
        assertThat(completion.completionTokens()).isEqualTo(2);
        assertThat(completion.totalTokens()).isEqualTo(12);
        assertThat(completion.latencyMs()).isGreaterThanOrEqualTo(0);
        h.server().verify();
    }

    // ------------------------------------------------------------------
    // (g) jsonObject=true adds response_format:json_object, false omits it entirely
    // ------------------------------------------------------------------

    @Test
    void complete_jsonObjectTrue_addsResponseFormat() {
        Harness h = harness("llama-3.1-8b-instant");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(modelsBody("llama-3.1-8b-instant"), MediaType.APPLICATION_JSON));
        h.server()
                .expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.response_format.type").value("json_object"))
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"{}\"}}]}",
                        MediaType.APPLICATION_JSON));

        h.service().complete(AICompletionRequest.json(chat()));

        h.server().verify();
    }

    @Test
    void complete_jsonObjectFalse_omitsResponseFormat() {
        Harness h = harness("llama-3.1-8b-instant");
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withSuccess(modelsBody("llama-3.1-8b-instant"), MediaType.APPLICATION_JSON));
        h.server()
                .expect(requestTo(BASE_URL + "/chat/completions"))
                .andExpect(jsonPath("$.response_format").doesNotExist())
                .andRespond(withSuccess(
                        "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"ok\"}}]}",
                        MediaType.APPLICATION_JSON));

        h.service().complete(AICompletionRequest.text(chat()));

        h.server().verify();
    }

    // ------------------------------------------------------------------
    // (h) 500, 401 and a connection failure each raise AIUnavailableException; the
    // message never contains the API key
    // ------------------------------------------------------------------

    @Test
    void serverError_throwsUnavailable_neverLeaksApiKey() {
        Harness h = harness("secret-groq-key-do-not-leak", "llama-3.1-8b-instant", DEFAULT_PREFERENCES, 30);
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\":\"boom\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(h.service()::resolveModel)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("500")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("secret-groq-key-do-not-leak"));
        h.server().verify();
    }

    @Test
    void unauthorized_throwsUnavailable_mapsRejectedApiKeyMessage_neverLeaksApiKey() {
        Harness h = harness("secret-groq-key-do-not-leak", "llama-3.1-8b-instant", DEFAULT_PREFERENCES, 30);
        h.server()
                .expect(requestTo(BASE_URL + "/models"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .body("{\"error\":\"invalid api key\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(h.service()::resolveModel)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining("401")
                .hasMessageContaining("rejected the API key")
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain("secret-groq-key-do-not-leak"));
        h.server().verify();
    }

    @Test
    void connectionFailure_throwsUnavailable_namesBaseUrl_neverLeaksApiKey() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(800));
        String unreachableUrl = "http://127.0.0.1:1";
        String secretKey = "super-secret-groq-key-do-not-leak";
        GroqAIService service = new GroqAIService(
                "groq",
                secretKey,
                unreachableUrl,
                "some-model",
                DEFAULT_PREFERENCES,
                30,
                0.3,
                2048,
                RestClient.builder().requestFactory(factory));

        assertThatThrownBy(service::listModels)
                .isInstanceOf(AIUnavailableException.class)
                .hasMessageContaining(unreachableUrl)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(secretKey));
    }
}

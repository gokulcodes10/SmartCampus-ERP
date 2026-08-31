package smartcampus.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.LinkedHashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import smartcampus.entity.AIMessageRole;
import smartcampus.exception.AIUnavailableException;

/**
 * The §69/§70 AI provider: talks to Groq's OpenAI-compatible chat-completions API over
 * HTTP. The application server never fabricates an AI response itself - every value
 * returned by {@link #complete} came from a real provider call, or this class threw
 * {@link AIUnavailableException} instead of answering at all. There is no fallback
 * path, no local generation, no cached or default reply: fabricating a completion is
 * precisely the §69 violation this class exists to avoid.
 *
 * <p>Construction NEVER fails and NEVER makes a network call, even with a blank API
 * key - a missing key is a call-time failure ({@link #isConfigured()} is {@code false}
 * and every method throws), not a boot failure, so the whole application (and the test
 * suite) can boot this bean with no key configured.
 *
 * <p>The model id is never guessed: {@link #resolveModel()} always confirms its answer
 * against the provider's live {@code GET /models} listing (model ids retire regularly),
 * caching only a SUCCESSFUL resolution for {@code smartcampus.ai.model-cache-minutes}.
 * A failure to resolve is never cached.
 */
@Service
public class GroqAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(GroqAIService.class);
    private static final int MAX_LISTED_MODEL_IDS = 20;
    private static final int ERROR_BODY_EXCERPT_LENGTH = 300;

    private final String provider;
    private final String apiKey;
    private final String baseUrl;
    private final String configuredModel;
    private final List<String> modelPreferences;
    private final long modelCacheMinutes;
    private final double defaultTemperature;
    private final int defaultMaxTokens;
    private final RestClient client;

    private volatile String resolvedModel;
    private volatile Instant resolvedAt;

    @Autowired
    public GroqAIService(
            @Value("${smartcampus.ai.provider:groq}") String provider,
            @Value("${smartcampus.ai.api-key:}") String apiKey,
            @Value("${smartcampus.ai.base-url:https://api.groq.com/openai/v1}") String baseUrl,
            @Value("${smartcampus.ai.model:}") String configuredModel,
            @Value("#{'${smartcampus.ai.model-preferences:llama-3.3-70b-versatile,llama-3.1-8b-instant,"
                    + "openai/gpt-oss-120b,openai/gpt-oss-20b,meta-llama/llama-4-scout-17b-16e-instruct,"
                    + "qwen/qwen3-32b}'.split(',')}")
                    List<String> modelPreferences,
            @Value("${smartcampus.ai.model-cache-minutes:30}") long modelCacheMinutes,
            @Value("${smartcampus.ai.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${smartcampus.ai.read-timeout-ms:60000}") int readTimeoutMs,
            @Value("${smartcampus.ai.temperature:0.3}") double defaultTemperature,
            @Value("${smartcampus.ai.max-tokens:2048}") int defaultMaxTokens) {
        this(
                provider,
                apiKey,
                baseUrl,
                configuredModel,
                modelPreferences,
                modelCacheMinutes,
                defaultTemperature,
                defaultMaxTokens,
                RestClient.builder().requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs)));
    }

    /**
     * Testability seam: lets {@code smartcampus.ai.GroqAIServiceTest} bind a
     * {@link RestClient.Builder} to
     * {@code org.springframework.test.web.client.MockRestServiceServer} instead of
     * making a real HTTP connection, without going through Spring's config resolution
     * or a {@code @SpringBootTest} context. The builder's base URL is set here (not by
     * the caller) so both constructors keep {@code baseUrl} as the single source of
     * truth used in error messages. Public rather than package-private specifically so
     * the test can reach it from outside this package; the {@code @Autowired} primary
     * constructor above is still the one Spring picks for normal application wiring.
     */
    public GroqAIService(
            String provider,
            String apiKey,
            String baseUrl,
            String configuredModel,
            List<String> modelPreferences,
            long modelCacheMinutes,
            double defaultTemperature,
            int defaultMaxTokens,
            RestClient.Builder builder) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.configuredModel = configuredModel;
        this.modelPreferences = modelPreferences;
        this.modelCacheMinutes = modelCacheMinutes;
        this.defaultTemperature = defaultTemperature;
        this.defaultMaxTokens = defaultMaxTokens;
        this.client = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    // ------------------------------------------------------------------
    // status getters - the status endpoint needs these; NEVER expose the API key
    // ------------------------------------------------------------------

    public String getProvider() {
        return provider;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getConfiguredModel() {
        return configuredModel;
    }

    /** The cached resolved model id, or {@code null} if none has been resolved yet. Never makes a call. */
    public String getResolvedModelOrNull() {
        return resolvedModel;
    }

    // ------------------------------------------------------------------
    // AIService
    // ------------------------------------------------------------------

    @Override
    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public synchronized String resolveModel() {
        if (!isConfigured()) {
            throw notConfigured();
        }
        Instant now = Instant.now();
        if (resolvedModel != null
                && resolvedAt != null
                && now.isBefore(resolvedAt.plus(Duration.ofMinutes(modelCacheMinutes)))) {
            return resolvedModel;
        }

        List<AIModelInfo> models = listModels();
        Set<String> availableIds = new LinkedHashSet<>();
        for (AIModelInfo model : models) {
            if (model.id() != null) {
                availableIds.add(model.id());
            }
        }

        String chosen;
        if (configuredModel != null && !configuredModel.isBlank()) {
            if (!availableIds.contains(configuredModel)) {
                throw unavailable("Configured AI model '" + configuredModel + "' is not available at " + baseUrl
                        + ". Available model ids: " + listIdsForMessage(availableIds) + ".");
            }
            chosen = configuredModel;
        } else {
            chosen = modelPreferences.stream()
                    .map(String::trim)
                    .filter(id -> !id.isEmpty())
                    .filter(availableIds::contains)
                    .findFirst()
                    .orElse(null);
            if (chosen == null) {
                throw unavailable("No usable AI model. Set AI_MODEL to one of the ids available at " + baseUrl
                        + "/models: " + listIdsForMessage(availableIds) + ".");
            }
        }

        resolvedModel = chosen;
        resolvedAt = now;
        return chosen;
    }

    @Override
    public List<AIModelInfo> listModels() {
        if (!isConfigured()) {
            throw notConfigured();
        }
        try {
            GroqModelsResponse response =
                    client.get().uri("/models").headers(this::addAuthHeaders).retrieve().body(GroqModelsResponse.class);
            if (response == null || response.data() == null) {
                throw unavailable("The AI provider returned an empty response.");
            }
            return response.data().stream()
                    .map(m -> new AIModelInfo(m.id(), m.ownedBy(), m.contextWindow()))
                    .toList();
        } catch (RestClientResponseException e) {
            throw unavailable(httpFailureMessage(e));
        } catch (ResourceAccessException e) {
            throw unavailable("The AI provider at " + baseUrl + " could not be reached.", e);
        } catch (RestClientException e) {
            throw unavailable("The AI provider returned a response that could not be parsed.", e);
        }
    }

    @Override
    public AICompletion complete(AICompletionRequest request) {
        String model = resolveModel();

        List<GroqChatMessage> wireMessages = request.messages().stream()
                .map(m -> new GroqChatMessage(roleToWire(m.role()), m.content()))
                .toList();
        double temperature = request.temperature() != null ? request.temperature() : defaultTemperature;
        int maxTokens = request.maxTokens() != null ? request.maxTokens() : defaultMaxTokens;
        GroqResponseFormat responseFormat = request.jsonObject() ? new GroqResponseFormat("json_object") : null;
        GroqChatRequest body = new GroqChatRequest(model, wireMessages, temperature, maxTokens, responseFormat);

        long startNanos = System.nanoTime();
        GroqChatCompletionResponse response;
        try {
            response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::addAuthHeaders)
                    .body(body)
                    .retrieve()
                    .body(GroqChatCompletionResponse.class);
        } catch (RestClientResponseException e) {
            throw unavailable(httpFailureMessage(e));
        } catch (ResourceAccessException e) {
            throw unavailable("The AI provider at " + baseUrl + " could not be reached.", e);
        } catch (RestClientException e) {
            throw unavailable("The AI provider returned a response that could not be parsed.", e);
        }
        long latencyMs = (System.nanoTime() - startNanos) / 1_000_000L;

        if (response == null || response.choices() == null || response.choices().isEmpty()) {
            throw unavailable("The AI provider returned an empty response.");
        }
        GroqChatChoice choice = response.choices().get(0);
        String content = choice.message() == null ? null : choice.message().content();
        if (content == null || content.isBlank()) {
            throw unavailable("The AI provider returned an empty response.");
        }

        GroqUsage usage = response.usage();
        Integer promptTokens = usage == null ? null : usage.promptTokens();
        Integer completionTokens = usage == null ? null : usage.completionTokens();
        Integer totalTokens = usage == null ? null : usage.totalTokens();

        return new AICompletion(content, model, promptTokens, completionTokens, totalTokens, latencyMs);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------

    private void addAuthHeaders(HttpHeaders headers) {
        headers.setBearerAuth(apiKey);
    }

    private static String roleToWire(AIMessageRole role) {
        return switch (role) {
            case SYSTEM -> "system";
            case USER -> "user";
            case ASSISTANT -> "assistant";
        };
    }

    private String httpFailureMessage(RestClientResponseException e) {
        int status = e.getStatusCode().value();
        if (status == 401 || status == 403) {
            return "The AI provider rejected the API key (HTTP " + status + ").";
        }
        if (status == 429) {
            return "The AI provider rate-limited this request (HTTP 429).";
        }
        String body = e.getResponseBodyAsString();
        String excerpt = (body == null || body.isBlank()) ? "" : ": " + truncate(body, ERROR_BODY_EXCERPT_LENGTH);
        return "The AI provider returned HTTP " + status + excerpt;
    }

    private static String truncate(String s, int maxLength) {
        return s.length() <= maxLength ? s : s.substring(0, maxLength) + "...";
    }

    private String listIdsForMessage(Collection<String> ids) {
        List<String> capped = ids.stream().limit(MAX_LISTED_MODEL_IDS).toList();
        String joined = String.join(", ", capped);
        return ids.size() > MAX_LISTED_MODEL_IDS ? joined + ", ..." : joined;
    }

    private AIUnavailableException notConfigured() {
        return unavailable("AI is not configured: set AI_API_KEY in the environment.");
    }

    /**
     * Builds the exception AND logs at WARN with only the base URL, model id and
     * failure reason - never the API key, the Authorization header, or any prompt
     * content (prompts embed the student's academic record).
     */
    private AIUnavailableException unavailable(String reason) {
        log.warn("AI provider call failed (baseUrl={}, model={}): {}", baseUrl, resolvedModel, reason);
        return new AIUnavailableException(reason);
    }

    private AIUnavailableException unavailable(String reason, Throwable cause) {
        log.warn("AI provider call failed (baseUrl={}, model={}): {}", baseUrl, resolvedModel, reason);
        return new AIUnavailableException(reason, cause);
    }

    // ------------------------------------------------------------------
    // Groq/OpenAI wire DTOs. Deliberately NOT in smartcampus.dto, which is the public
    // API package - these shapes are the provider's, not ours, and must never leak into
    // a controller response. @JsonIgnoreProperties(ignoreUnknown = true) because the
    // provider may add fields we do not read.
    // ------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqModel(String id, @JsonProperty("owned_by") String ownedBy, @JsonProperty("context_window") Integer contextWindow) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqModelsResponse(List<GroqModel> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqResponseFormat(String type) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record GroqChatRequest(
            String model,
            List<GroqChatMessage> messages,
            Double temperature,
            @JsonProperty("max_tokens") Integer maxTokens,
            @JsonProperty("response_format") GroqResponseFormat responseFormat) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatChoiceMessage(String role, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatChoice(GroqChatChoiceMessage message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqUsage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens,
            @JsonProperty("total_tokens") Integer totalTokens) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GroqChatCompletionResponse(String model, List<GroqChatChoice> choices, GroqUsage usage) {}
}

package smartcampus.coding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.SubmissionStatus;
import smartcampus.exception.CodeExecutionUnavailableException;
import smartcampus.service.ExecutionCase;
import smartcampus.service.ExecutionResult;
import smartcampus.service.Judge0Service;

/**
 * Unit tests for {@link Judge0Service} against {@link MockRestServiceServer} - no live
 * Judge0 endpoint, no {@code @SpringBootTest} context. Per G10, Judge0 has no reachable
 * endpoint on this build machine, so these tests are the only verification of the
 * request/response contract available here; see the final report for what a reviewer
 * still needs to do against a real Judge0 instance.
 *
 * <p>{@link Judge0Service} exposes a public secondary constructor taking a
 * {@link RestClient.Builder} specifically as a testability seam for this class (it
 * lives outside {@code smartcampus.service}, so a package-private constructor would not
 * be reachable here) - see the constructor's javadoc.
 */
class Judge0ServiceTest {

    private static final String BASE_URL = "http://localhost:2358";
    private static final String POLL_FIELDS =
            "fields=token,stdout,stderr,compile_output,message,time,memory,status_id,status";

    private record Harness(Judge0Service service, MockRestServiceServer server) {}

    private static Harness harness(String apiKey, String authHeaderName, String hostHeader,
            int pollIntervalMs, int pollTimeoutMs) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        Judge0Service service = new Judge0Service(
                BASE_URL, apiKey, authHeaderName, hostHeader, pollIntervalMs, pollTimeoutMs, 62, 54, builder);
        return new Harness(service, server);
    }

    private static Harness harness(int pollIntervalMs, int pollTimeoutMs) {
        return harness("", "X-RapidAPI-Key", "", pollIntervalMs, pollTimeoutMs);
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String pollUri(String tokenCsv) {
        return BASE_URL + "/submissions/batch?tokens=" + tokenCsv + "&base64_encoded=true&" + POLL_FIELDS;
    }

    // ------------------------------------------------------------------
    // outgoing request shape
    // ------------------------------------------------------------------

    @Test
    void createRequest_encodesFieldsAndSendsCorrectShape() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.submissions[0].source_code").value(b64("print(1)")))
                .andExpect(jsonPath("$.submissions[0].stdin").value(b64("in")))
                .andExpect(jsonPath("$.submissions[0].expected_output").value(b64("out")))
                .andExpect(jsonPath("$.submissions[0].language_id").value(62))
                .andExpect(jsonPath("$.submissions[0].cpu_time_limit").value(2.0))
                .andExpect(jsonPath("$.submissions[0].memory_limit").value(131072))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1")))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}",
                        MediaType.APPLICATION_JSON));

        List<ExecutionResult> results = h.service().executeBatch(
                ProgrammingLanguage.JAVA, "print(1)", List.of(new ExecutionCase("in", "out")), 2000, 131072);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).status()).isEqualTo(SubmissionStatus.ACCEPTED);
        h.server().verify();
    }

    @Test
    void createRequest_omitsExpectedOutput_whenCaseHasNone() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andExpect(jsonPath("$.submissions[0].expected_output").doesNotExist())
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1")))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}",
                        MediaType.APPLICATION_JSON));

        h.service().executeBatch(ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", null)), 2000, 131072);

        h.server().verify();
    }

    // ------------------------------------------------------------------
    // happy path, index-aligned, in order
    // ------------------------------------------------------------------

    @Test
    void executeBatch_happyPath_returnsResultsInOrder() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"},{\"token\":\"t2\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1,t2")))
                .andRespond(withSuccess(
                        "{\"submissions\":["
                                + "{\"token\":\"t1\",\"status\":{\"id\":1,\"description\":\"In Queue\"}},"
                                + "{\"token\":\"t2\",\"status\":{\"id\":2,\"description\":\"Processing\"}}]}",
                        MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1,t2")))
                .andRespond(withSuccess(
                        "{\"submissions\":["
                                + "{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"},\"stdout\":\""
                                + b64("out1") + "\"},"
                                + "{\"token\":\"t2\",\"status\":{\"id\":4,\"description\":\"Wrong Answer\"},\"stdout\":\""
                                + b64("bad") + "\"}]}",
                        MediaType.APPLICATION_JSON));

        List<ExecutionResult> results = h.service().executeBatch(
                ProgrammingLanguage.JAVA, "code",
                List.of(new ExecutionCase("in1", "out1"), new ExecutionCase("in2", "out2")), 2000, 131072);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).token()).isEqualTo("t1");
        assertThat(results.get(0).status()).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(results.get(0).stdout()).isEqualTo("out1");
        assertThat(results.get(1).token()).isEqualTo("t2");
        assertThat(results.get(1).status()).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(results.get(1).stdout()).isEqualTo("bad");
        h.server().verify();
    }

    // ------------------------------------------------------------------
    // full status.id -> SubmissionStatus mapping table (private method, via reflection -
    // the loop that calls it only ever sees terminal ids >= 3, so black-box coverage of
    // PENDING/RUNNING/the default branch/null is not reachable through executeBatch)
    // ------------------------------------------------------------------

    @Test
    void mapStatus_coversFullTableIncludingMemoryHeuristic() throws Exception {
        Judge0Service service = harness(1, 5000).service();
        int memoryLimitKb = 131072;

        assertThat(mapStatus(service, 1, null, memoryLimitKb)).isEqualTo(SubmissionStatus.PENDING);
        assertThat(mapStatus(service, 2, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNNING);
        assertThat(mapStatus(service, 3, null, memoryLimitKb)).isEqualTo(SubmissionStatus.ACCEPTED);
        assertThat(mapStatus(service, 4, null, memoryLimitKb)).isEqualTo(SubmissionStatus.WRONG_ANSWER);
        assertThat(mapStatus(service, 5, null, memoryLimitKb)).isEqualTo(SubmissionStatus.TIME_LIMIT_EXCEEDED);
        assertThat(mapStatus(service, 6, null, memoryLimitKb)).isEqualTo(SubmissionStatus.COMPILATION_ERROR);

        // 7 = SIGSEGV: RUNTIME_ERROR unless memory usage is at/above 98% of the limit
        assertThat(mapStatus(service, 7, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 7, memoryLimitKb / 2, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 7, (int) Math.round(memoryLimitKb * 0.99), memoryLimitKb))
                .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);

        assertThat(mapStatus(service, 8, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 9, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);

        // 10 = SIGABRT: same heuristic as 7
        assertThat(mapStatus(service, 10, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 10, memoryLimitKb / 2, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 10, (int) Math.round(memoryLimitKb * 0.99), memoryLimitKb))
                .isEqualTo(SubmissionStatus.MEMORY_LIMIT_EXCEEDED);

        assertThat(mapStatus(service, 11, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 12, null, memoryLimitKb)).isEqualTo(SubmissionStatus.RUNTIME_ERROR);
        assertThat(mapStatus(service, 13, null, memoryLimitKb)).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(mapStatus(service, 14, null, memoryLimitKb)).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(mapStatus(service, 99, null, memoryLimitKb)).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
        assertThat(mapStatus(service, null, null, memoryLimitKb)).isEqualTo(SubmissionStatus.INTERNAL_ERROR);
    }

    private static SubmissionStatus mapStatus(Judge0Service service, Integer statusId, Integer memoryKb, int memoryLimitKb)
            throws Exception {
        Method m = Judge0Service.class.getDeclaredMethod("mapStatus", Integer.class, Integer.class, int.class);
        m.setAccessible(true);
        return (SubmissionStatus) m.invoke(service, statusId, memoryKb, memoryLimitKb);
    }

    // ------------------------------------------------------------------
    // field conversion: time string -> ms, fractional memory -> rounded kb
    // ------------------------------------------------------------------

    @Test
    void fieldConversion_timeStringAndFractionalMemoryRoundCorrectly() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1")))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"},"
                                + "\"time\":\"0.023\",\"memory\":2048.6}]}",
                        MediaType.APPLICATION_JSON));

        List<ExecutionResult> results = h.service().executeBatch(
                ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", null)), 2000, 131072);

        assertThat(results.get(0).executionTimeMs()).isEqualTo(23);
        assertThat(results.get(0).memoryKb()).isEqualTo(2049);
    }

    // ------------------------------------------------------------------
    // failure honesty: every failure mode throws CodeExecutionUnavailableException,
    // never a fabricated or partial result
    // ------------------------------------------------------------------

    @Test
    void connectionFailure_throwsUnavailable_namesBaseUrl_neverLeaksApiKey() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(800));
        factory.setReadTimeout(Duration.ofMillis(800));
        String unreachableUrl = "http://127.0.0.1:1";
        String secretKey = "super-secret-rapidapi-key-do-not-leak";
        Judge0Service service = new Judge0Service(
                unreachableUrl, secretKey, "X-RapidAPI-Key", "", 50, 2000, 62, 54,
                RestClient.builder().requestFactory(factory));

        assertThatThrownBy(() -> service.executeBatch(
                        ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", "out")), 2000, 131072))
                .isInstanceOf(CodeExecutionUnavailableException.class)
                .hasMessageContaining(unreachableUrl)
                .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(secretKey));
    }

    @Test
    void nonSuccessStatus_throwsUnavailable_notAVerdict() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"error\":\"invalid language_id\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.service().executeBatch(
                        ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", "out")), 2000, 131072))
                .isInstanceOf(CodeExecutionUnavailableException.class)
                .hasMessageContaining("400")
                .hasMessageContaining("invalid language_id")
                .hasMessageContaining(BASE_URL);
    }

    @Test
    void tokenCountShorterThanCases_throwsUnavailable_neverPads() {
        Harness h = harness(1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.service().executeBatch(
                        ProgrammingLanguage.JAVA, "code",
                        List.of(new ExecutionCase("in1", "out1"), new ExecutionCase("in2", "out2")), 2000, 131072))
                .isInstanceOf(CodeExecutionUnavailableException.class)
                .hasMessageContaining("1 token")
                .hasMessageContaining("2 submitted");
    }

    @Test
    void pollTimeout_throwsUnavailable_notFabricated() {
        Harness h = harness(5, 30);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(ExpectedCount.manyTimes(), requestTo(pollUri("t1")))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":2,\"description\":\"Processing\"}}]}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> h.service().executeBatch(
                        ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", "out")), 2000, 131072))
                .isInstanceOf(CodeExecutionUnavailableException.class)
                .hasMessageContaining("did not return a verdict")
                .hasMessageContaining(BASE_URL);
    }

    // ------------------------------------------------------------------
    // auth header scheme
    // ------------------------------------------------------------------

    @Test
    void blankApiKey_sendsNoAuthHeaders_evenWhenHostHeaderConfigured() {
        Harness h = harness("", "X-RapidAPI-Key", "some-host.example.com", 1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andExpect(headerDoesNotExist("X-RapidAPI-Key"))
                .andExpect(headerDoesNotExist("X-RapidAPI-Host"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1")))
                .andExpect(headerDoesNotExist("X-RapidAPI-Key"))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}",
                        MediaType.APPLICATION_JSON));

        h.service().executeBatch(ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", "out")), 2000, 131072);

        h.server().verify();
    }

    @Test
    void apiKeyAndHostHeaderSet_sendsBothHeadersWithConfiguredNames() {
        Harness h = harness("secret-key-value", "X-RapidAPI-Key", "judge0.example.com", 1, 5000);
        h.server().expect(requestTo(BASE_URL + "/submissions/batch?base64_encoded=true"))
                .andExpect(header("X-RapidAPI-Key", "secret-key-value"))
                .andExpect(header("X-RapidAPI-Host", "judge0.example.com"))
                .andRespond(withSuccess("[{\"token\":\"t1\"}]", MediaType.APPLICATION_JSON));
        h.server().expect(requestTo(pollUri("t1")))
                .andExpect(header("X-RapidAPI-Key", "secret-key-value"))
                .andExpect(header("X-RapidAPI-Host", "judge0.example.com"))
                .andRespond(withSuccess(
                        "{\"submissions\":[{\"token\":\"t1\",\"status\":{\"id\":3,\"description\":\"Accepted\"}}]}",
                        MediaType.APPLICATION_JSON));

        h.service().executeBatch(ProgrammingLanguage.JAVA, "code", List.of(new ExecutionCase("in", "out")), 2000, 131072);

        h.server().verify();
    }
}

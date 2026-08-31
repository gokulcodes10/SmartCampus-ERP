package smartcampus.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import smartcampus.entity.ProgrammingLanguage;
import smartcampus.entity.SubmissionStatus;
import smartcampus.exception.CodeExecutionUnavailableException;

/**
 * The §28/§70 code-execution backend: talks to a Judge0 CE instance over HTTP. The
 * application server never executes student-submitted source itself - every verdict
 * returned by {@link #executeBatch} came from a real Judge0 run, or this class threw
 * {@link CodeExecutionUnavailableException} instead of answering at all. There is no
 * fallback path, no local execution, no cached or default verdict: fabricating a
 * verdict is precisely the §69 violation this class exists to avoid, and
 * {@code chk_coding_submissions_accepted_is_earned} rejects it at the database anyway.
 *
 * <p><b>G10:</b> Judge0 has no reachable endpoint on the local build machine (its
 * bundled isolate 1.8.1 needs cgroup v1; Docker Desktop's VM here is cgroup-v2 only),
 * so every call this class makes in this environment fails with connection refused.
 * That is the correct, honest behaviour - see {@code CodeExecutionUnavailableException}
 * thrown from every failure branch below.
 */
@Service
public class Judge0Service implements CodeExecutionService {

    private final String baseUrl;
    private final String apiKey;
    private final String authHeaderName;
    private final String hostHeaderValue;
    private final int pollIntervalMs;
    private final int pollTimeoutMs;
    private final int javaLanguageId;
    private final int cppLanguageId;
    private final RestClient client;

    @Autowired
    public Judge0Service(
            @Value("${smartcampus.judge0.url:http://localhost:2358}") String baseUrl,
            @Value("${smartcampus.judge0.api-key:}") String apiKey,
            @Value("${smartcampus.judge0.auth-header:X-RapidAPI-Key}") String authHeaderName,
            @Value("${smartcampus.judge0.host-header:}") String hostHeaderValue,
            @Value("${smartcampus.judge0.connect-timeout-ms:5000}") int connectTimeoutMs,
            @Value("${smartcampus.judge0.read-timeout-ms:15000}") int readTimeoutMs,
            @Value("${smartcampus.judge0.poll-interval-ms:400}") int pollIntervalMs,
            @Value("${smartcampus.judge0.poll-timeout-ms:30000}") int pollTimeoutMs,
            @Value("${smartcampus.judge0.language-id.java:62}") int javaLanguageId,
            @Value("${smartcampus.judge0.language-id.cpp:54}") int cppLanguageId) {
        this(
                baseUrl,
                apiKey,
                authHeaderName,
                hostHeaderValue,
                pollIntervalMs,
                pollTimeoutMs,
                javaLanguageId,
                cppLanguageId,
                RestClient.builder().requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs)));
    }

    /**
     * Testability seam: lets tests (including {@code smartcampus.coding.Judge0ServiceTest},
     * which lives outside this package) bind a {@link RestClient.Builder} to
     * {@code org.springframework.test.web.client.MockRestServiceServer} instead of
     * making a real HTTP connection, without going through Spring's config resolution
     * or a {@code @SpringBootTest} context. The builder's base URL is set here (not by
     * the caller) so both constructors keep {@code baseUrl} as the single source of
     * truth used in error messages. Public rather than package-private specifically so
     * the test can reach it; the {@code @Autowired} primary constructor above is still
     * the one Spring picks for normal application wiring.
     */
    public Judge0Service(
            String baseUrl,
            String apiKey,
            String authHeaderName,
            String hostHeaderValue,
            int pollIntervalMs,
            int pollTimeoutMs,
            int javaLanguageId,
            int cppLanguageId,
            RestClient.Builder builder) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.authHeaderName = authHeaderName;
        this.hostHeaderValue = hostHeaderValue;
        this.pollIntervalMs = pollIntervalMs;
        this.pollTimeoutMs = pollTimeoutMs;
        this.javaLanguageId = javaLanguageId;
        this.cppLanguageId = cppLanguageId;
        this.client = builder.baseUrl(baseUrl).build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        factory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        return factory;
    }

    @Override
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public List<ExecutionResult> executeBatch(
            ProgrammingLanguage language,
            String sourceCode,
            List<ExecutionCase> cases,
            int cpuTimeLimitMs,
            int memoryLimitKb) {
        if (cases.isEmpty()) {
            return List.of();
        }

        int languageId = language == ProgrammingLanguage.JAVA ? javaLanguageId : cppLanguageId;
        double cpuTimeLimit = cpuTimeLimitMs / 1000.0;
        double wallTimeLimit = Math.min(cpuTimeLimit * 3 + 2.0, 20.0);

        List<Judge0Submission> submissions = new ArrayList<>(cases.size());
        for (ExecutionCase c : cases) {
            submissions.add(new Judge0Submission(
                    encode(sourceCode),
                    languageId,
                    encode(c.stdin()),
                    c.expectedOutput() == null ? null : encode(c.expectedOutput()),
                    cpuTimeLimit,
                    wallTimeLimit,
                    memoryLimitKb));
        }

        List<Judge0Token> tokens = createBatch(submissions);
        if (tokens.size() != cases.size()) {
            throw unavailable("Judge0 at " + baseUrl + " returned " + tokens.size()
                    + " token(s) for " + cases.size() + " submitted test case(s).");
        }
        List<String> tokenList = tokens.stream().map(Judge0Token::token).toList();

        Map<String, Judge0Result> resultsByToken = pollUntilDone(tokenList);

        List<ExecutionResult> results = new ArrayList<>(cases.size());
        for (String token : tokenList) {
            results.add(toExecutionResult(resultsByToken.get(token), memoryLimitKb));
        }
        return results;
    }

    @Override
    public ExecutionResult executeOnce(
            ProgrammingLanguage language, String sourceCode, String stdin, int cpuTimeLimitMs, int memoryLimitKb) {
        List<ExecutionResult> results =
                executeBatch(language, sourceCode, List.of(new ExecutionCase(stdin, null)), cpuTimeLimitMs, memoryLimitKb);
        return results.get(0);
    }

    // ------------------------------------------------------------------
    // Judge0 HTTP calls
    // ------------------------------------------------------------------

    private List<Judge0Token> createBatch(List<Judge0Submission> submissions) {
        try {
            Judge0Token[] response = client
                    .post()
                    .uri("/submissions/batch?base64_encoded=true")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(this::addAuthHeaders)
                    .body(new Judge0BatchRequest(submissions))
                    .retrieve()
                    .body(Judge0Token[].class);
            if (response == null) {
                throw unavailable("Judge0 at " + baseUrl + " returned an empty response creating the submission batch.");
            }
            return Arrays.asList(response);
        } catch (RestClientResponseException e) {
            throw unavailable(httpFailureMessage(e));
        } catch (ResourceAccessException e) {
            throw unavailable(
                    "Judge0 at " + baseUrl + " could not be reached (" + rootMessage(e)
                            + "). Set JUDGE0_URL to a reachable instance.",
                    e);
        } catch (RestClientException e) {
            throw unavailable(
                    "Judge0 at " + baseUrl + " returned a response that could not be parsed ("
                            + rootMessage(e) + ").",
                    e);
        }
    }

    /**
     * Polls {@code GET /submissions/batch?tokens=...} until every token has a terminal
     * verdict ({@code status.id >= 3}) or {@link #pollTimeoutMs} has elapsed. Judge0's
     * {@code wait=true} synchronous mode is disabled on many deployments (including
     * RapidAPI), so a poll loop is required. {@code callback_url} is not used either -
     * it requires a publicly reachable URL that neither this machine nor a
     * RapidAPI-hosted judge can call back into.
     */
    private Map<String, Judge0Result> pollUntilDone(List<String> tokens) {
        String csv = String.join(",", tokens);
        long start = System.currentTimeMillis();
        Map<String, Judge0Result> latest = new HashMap<>();

        while (true) {
            sleep();
            Judge0BatchResponse response = pollOnce(csv);
            if (response != null && response.submissions() != null) {
                for (Judge0Result r : response.submissions()) {
                    if (r.token() != null) {
                        latest.put(r.token(), r);
                    }
                }
            }
            if (allTerminal(tokens, latest)) {
                return latest;
            }
            if (System.currentTimeMillis() - start >= pollTimeoutMs) {
                throw unavailable("Judge0 at " + baseUrl + " did not return a verdict for all "
                        + tokens.size() + " test case(s) within " + (pollTimeoutMs / 1000) + "s.");
            }
        }
    }

    private boolean allTerminal(List<String> tokens, Map<String, Judge0Result> latest) {
        for (String token : tokens) {
            Judge0Result r = latest.get(token);
            if (r == null || r.status() == null || r.status().id() == null || r.status().id() < 3) {
                return false;
            }
        }
        return true;
    }

    private Judge0BatchResponse pollOnce(String csv) {
        String uri = "/submissions/batch?tokens=" + csv
                + "&base64_encoded=true"
                + "&fields=token,stdout,stderr,compile_output,message,time,memory,status_id,status";
        try {
            return client.get().uri(uri).headers(this::addAuthHeaders).retrieve().body(Judge0BatchResponse.class);
        } catch (RestClientResponseException e) {
            throw unavailable(httpFailureMessage(e));
        } catch (ResourceAccessException e) {
            throw unavailable(
                    "Judge0 at " + baseUrl + " could not be reached (" + rootMessage(e)
                            + "). Set JUDGE0_URL to a reachable instance.",
                    e);
        } catch (RestClientException e) {
            throw unavailable(
                    "Judge0 at " + baseUrl + " returned a response that could not be parsed ("
                            + rootMessage(e) + ").",
                    e);
        }
    }

    private void addAuthHeaders(HttpHeaders headers) {
        if (apiKey != null && !apiKey.isBlank()) {
            headers.set(authHeaderName, apiKey);
            if (hostHeaderValue != null && !hostHeaderValue.isBlank()) {
                headers.set("X-RapidAPI-Host", hostHeaderValue);
            }
        }
    }

    private String httpFailureMessage(RestClientResponseException e) {
        String base = "Judge0 at " + baseUrl + " returned HTTP " + e.getStatusCode().value();
        if (e.getStatusCode().is4xxClientError()) {
            String body = e.getResponseBodyAsString();
            return base + (body == null || body.isBlank() ? "." : ": " + body);
        }
        return base + ".";
    }

    private String rootMessage(Throwable t) {
        Throwable cause = t.getCause();
        String message = (cause != null ? cause.getMessage() : t.getMessage());
        return message == null ? t.getClass().getSimpleName() : message;
    }

    private void sleep() {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("Code execution was interrupted while waiting for Judge0 at " + baseUrl + ".", e);
        }
    }

    private CodeExecutionUnavailableException unavailable(String reason) {
        return new CodeExecutionUnavailableException("Code execution service is unavailable: " + reason);
    }

    private CodeExecutionUnavailableException unavailable(String reason, Throwable cause) {
        return new CodeExecutionUnavailableException("Code execution service is unavailable: " + reason, cause);
    }

    // ------------------------------------------------------------------
    // Field conversion
    // ------------------------------------------------------------------

    /**
     * Maps a single Judge0 result to our verdict shape, including the field
     * conversions specified in §4: {@code time} is a STRING of seconds ("0.023") ->
     * milliseconds; {@code memory} is already kilobytes, possibly fractional.
     */
    private ExecutionResult toExecutionResult(Judge0Result r, int memoryLimitKb) {
        if (r == null || r.status() == null) {
            throw unavailable("Judge0 at " + baseUrl + " did not return a complete result for one of the submitted test cases.");
        }
        Integer statusId = r.status().id();
        Integer memoryKb = r.memory() == null ? null : (int) Math.round(r.memory());
        Integer executionTimeMs = parseTimeMs(r.time());
        SubmissionStatus status = mapStatus(statusId, memoryKb, memoryLimitKb);
        return new ExecutionResult(
                status,
                statusId,
                r.status().description(),
                decode(r.stdout()),
                decode(r.stderr()),
                decode(r.compileOutput()),
                decode(r.message()),
                executionTimeMs,
                memoryKb,
                r.token());
    }

    /**
     * Judge0 status.id -> {@link SubmissionStatus}, per the table in §4 of the phase
     * contract. Judge0 CE has no dedicated "memory limit exceeded" status; SIGSEGV (7)
     * and SIGABRT (10) are the runtime-error signals most likely to fire when a process
     * is killed for exceeding its memory limit, so those two are reclassified as
     * MEMORY_LIMIT_EXCEEDED only when Judge0 also reported memory usage at or above 98%
     * of the configured limit. This is a heuristic recovery of a status Judge0 CE does
     * not report directly, not a certainty - an unrelated SIGSEGV/SIGABRT that happens
     * to occur near the memory limit would be misclassified, and there is no way to
     * tell the two apart from Judge0 CE's response alone.
     */
    private SubmissionStatus mapStatus(Integer statusId, Integer memoryKb, int memoryLimitKb) {
        if (statusId == null) {
            return SubmissionStatus.INTERNAL_ERROR;
        }
        return switch (statusId) {
            case 1 -> SubmissionStatus.PENDING;
            case 2 -> SubmissionStatus.RUNNING;
            case 3 -> SubmissionStatus.ACCEPTED;
            case 4 -> SubmissionStatus.WRONG_ANSWER;
            case 5 -> SubmissionStatus.TIME_LIMIT_EXCEEDED;
            case 6 -> SubmissionStatus.COMPILATION_ERROR;
            case 7 -> memoryExceeded(memoryKb, memoryLimitKb)
                    ? SubmissionStatus.MEMORY_LIMIT_EXCEEDED
                    : SubmissionStatus.RUNTIME_ERROR;
            case 8, 9, 11, 12 -> SubmissionStatus.RUNTIME_ERROR;
            case 10 -> memoryExceeded(memoryKb, memoryLimitKb)
                    ? SubmissionStatus.MEMORY_LIMIT_EXCEEDED
                    : SubmissionStatus.RUNTIME_ERROR;
            case 13, 14 -> SubmissionStatus.INTERNAL_ERROR;
            default -> SubmissionStatus.INTERNAL_ERROR;
        };
    }

    private boolean memoryExceeded(Integer memoryKb, int memoryLimitKb) {
        return memoryKb != null && memoryKb >= memoryLimitKb * 0.98;
    }

    private Integer parseTimeMs(String time) {
        if (time == null || time.isBlank()) {
            return null;
        }
        return (int) Math.round(Double.parseDouble(time) * 1000);
    }

    private static String encode(String s) {
        return Base64.getEncoder().encodeToString((s == null ? "" : s).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Some Judge0 deployments line-wrap their base64 responses; {@code getMimeDecoder}
     * (not the strict/plain decoder) tolerates that.
     */
    private static String decode(String s) {
        if (s == null) {
            return null;
        }
        return new String(Base64.getMimeDecoder().decode(s), StandardCharsets.UTF_8);
    }

    // ------------------------------------------------------------------
    // Judge0 wire DTOs. Deliberately NOT in smartcampus.dto, which is the public API
    // package - these shapes are Judge0's, not ours, and must never leak into a
    // controller response. @JsonIgnoreProperties(ignoreUnknown = true) because Judge0
    // deployments differ in which fields they return.
    // ------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Judge0Submission(
            @JsonProperty("source_code") String sourceCode,
            @JsonProperty("language_id") Integer languageId,
            String stdin,
            @JsonProperty("expected_output") String expectedOutput,
            @JsonProperty("cpu_time_limit") Double cpuTimeLimit,
            @JsonProperty("wall_time_limit") Double wallTimeLimit,
            @JsonProperty("memory_limit") Integer memoryLimit) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Judge0BatchRequest(List<Judge0Submission> submissions) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Judge0Token(String token) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Judge0Status(Integer id, String description) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Judge0Result(
            String token,
            String stdout,
            String stderr,
            @JsonProperty("compile_output") String compileOutput,
            String message,
            String time,
            Double memory,
            Judge0Status status) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Judge0BatchResponse(List<Judge0Result> submissions) {}
}

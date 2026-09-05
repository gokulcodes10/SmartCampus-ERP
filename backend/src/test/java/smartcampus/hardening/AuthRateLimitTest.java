package smartcampus.hardening;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import smartcampus.security.AuthRateLimitFilter;
import smartcampus.security.AuthRateLimiter;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link AuthRateLimiter} + {@link AuthRateLimitFilter} against a
 * {@link MutableClock} — no Spring context, no {@code Thread.sleep}, and no shared
 * state with the rest of the suite. Follows the same no-Spring-context, fixed-Clock
 * pattern as {@code smartcampus.ai.AIRateLimiterTest}, extended with a settable clock
 * because this limiter (unlike {@code AIRateLimiter}, which reads counts from the
 * database) keeps its own in-memory window and genuinely needs time to move to prove
 * the window expires.
 *
 * <p>Runs the real filter's {@code doFilter} against Spring's {@link
 * MockHttpServletRequest} / {@link MockHttpServletResponse} — not mocks of the filter
 * itself — so the assertions below exercise the actual key-building, actual JSON body
 * parsing, and the actual §47-shaped 429 body the filter writes, exactly as it would
 * for a real {@code POST /api/auth/login}.
 */
class AuthRateLimitTest {

    private static final int LIMIT = 5;
    private static final int WINDOW_SECONDS = 60;

    private MutableClock clock;
    private AuthRateLimiter authRateLimiter;
    private AuthRateLimitFilter filter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-08-28T10:00:00Z"), ZoneOffset.UTC);
        authRateLimiter = new AuthRateLimiter(clock);
        ReflectionTestUtils.setField(authRateLimiter, "limit", LIMIT);
        ReflectionTestUtils.setField(authRateLimiter, "windowSeconds", WINDOW_SECONDS);
        objectMapper = new ObjectMapper();
        filter = new AuthRateLimitFilter(authRateLimiter, objectMapper);
    }

    private MockHttpServletRequest loginRequest(String remoteAddr, String email) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        request.setContentType("application/json");
        request.setContent(
                ("{\"email\":\"" + email + "\",\"password\":\"whatever-it-does-not-matter\"}")
                        .getBytes(StandardCharsets.UTF_8));
        return request;
    }

    private CountingFilterChain doFilterNTimes(String remoteAddr, String email, int n) throws Exception {
        CountingFilterChain chain = new CountingFilterChain();
        MockHttpServletResponse lastResponse = null;
        for (int i = 0; i < n; i++) {
            MockHttpServletRequest request = loginRequest(remoteAddr, email);
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            lastResponse = response;
        }
        chain.lastResponse = lastResponse;
        return chain;
    }

    @Test
    void nPlusOneRapidLoginsProduce429WithTheScope47Envelope() throws Exception {
        CountingFilterChain chain = doFilterNTimes("198.51.100.7", "attacker@example.com", LIMIT);
        assertThat(chain.timesReached).isEqualTo(LIMIT);
        assertThat(chain.lastResponse.getStatus()).isEqualTo(200);

        // Request LIMIT + 1: must be rejected before the downstream chain runs.
        MockHttpServletRequest overLimitRequest = loginRequest("198.51.100.7", "attacker@example.com");
        MockHttpServletResponse overLimitResponse = new MockHttpServletResponse();
        filter.doFilter(overLimitRequest, overLimitResponse, chain);

        assertThat(chain.timesReached).isEqualTo(LIMIT); // downstream did NOT run again
        assertThat(overLimitResponse.getStatus()).isEqualTo(429);
        assertThat(overLimitResponse.getContentType()).contains("application/json");

        var envelope = objectMapper.readTree(overLimitResponse.getContentAsByteArray());
        assertThat(envelope.get("status").asInt()).isEqualTo(429);
        assertThat(envelope.get("error").asString()).isEqualTo("TOO_MANY_REQUESTS");
        assertThat(envelope.get("path").asString()).isEqualTo("/api/auth/login");
        assertThat(envelope.get("timestamp")).isNotNull();
        // §61: never reveal whether the attempted email exists.
        assertThat(envelope.get("message").asString())
                .doesNotContainIgnoringCase("attacker@example.com")
                .doesNotContainIgnoringCase("exist")
                .doesNotContainIgnoringCase("account");
    }

    @Test
    void aDifferentClientKeyIsUnaffectedByAnotherCallersLimit() throws Exception {
        doFilterNTimes("198.51.100.7", "attacker@example.com", LIMIT);

        // Same limit already exhausted for (ip=198.51.100.7, email=attacker@example.com).
        MockHttpServletRequest sameKeyRequest = loginRequest("198.51.100.7", "attacker@example.com");
        MockHttpServletResponse sameKeyResponse = new MockHttpServletResponse();
        filter.doFilter(sameKeyRequest, sameKeyResponse, new CountingFilterChain());
        assertThat(sameKeyResponse.getStatus()).isEqualTo(429);

        // A different IP, same email: unaffected.
        MockHttpServletRequest differentIpRequest = loginRequest("203.0.113.9", "attacker@example.com");
        MockHttpServletResponse differentIpResponse = new MockHttpServletResponse();
        CountingFilterChain differentIpChain = new CountingFilterChain();
        filter.doFilter(differentIpRequest, differentIpResponse, differentIpChain);
        assertThat(differentIpChain.timesReached).isEqualTo(1);
        assertThat(differentIpResponse.getStatus()).isEqualTo(200);

        // Same IP, different email: also unaffected.
        MockHttpServletRequest differentEmailRequest = loginRequest("198.51.100.7", "someone-else@example.com");
        MockHttpServletResponse differentEmailResponse = new MockHttpServletResponse();
        CountingFilterChain differentEmailChain = new CountingFilterChain();
        filter.doFilter(differentEmailRequest, differentEmailResponse, differentEmailChain);
        assertThat(differentEmailChain.timesReached).isEqualTo(1);
        assertThat(differentEmailResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void windowExpiryReAllowsRequests_usingTheMovedClockNeverSleeping() throws Exception {
        doFilterNTimes("198.51.100.7", "attacker@example.com", LIMIT);

        MockHttpServletRequest stillInWindow = loginRequest("198.51.100.7", "attacker@example.com");
        MockHttpServletResponse stillInWindowResponse = new MockHttpServletResponse();
        filter.doFilter(stillInWindow, stillInWindowResponse, new CountingFilterChain());
        assertThat(stillInWindowResponse.getStatus()).isEqualTo(429);

        // Move the clock past the window boundary - no Thread.sleep anywhere.
        clock.advance(Duration.ofSeconds(WINDOW_SECONDS + 1));

        MockHttpServletRequest afterWindow = loginRequest("198.51.100.7", "attacker@example.com");
        MockHttpServletResponse afterWindowResponse = new MockHttpServletResponse();
        CountingFilterChain afterWindowChain = new CountingFilterChain();
        filter.doFilter(afterWindow, afterWindowResponse, afterWindowChain);

        assertThat(afterWindowChain.timesReached).isEqualTo(1);
        assertThat(afterWindowResponse.getStatus()).isEqualTo(200);
    }

    @Test
    void nonAuthPathsAreNeverRateLimited() throws Exception {
        // Same IP+email pair, but a path this filter must not touch, hammered well past
        // the configured limit.
        CountingFilterChain chain = new CountingFilterChain();
        for (int i = 0; i < LIMIT + 10; i++) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/departments");
            request.setRemoteAddr("198.51.100.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertThat(response.getStatus()).isEqualTo(200);
        }
        assertThat(chain.timesReached).isEqualTo(LIMIT + 10);
    }

    /** A {@link Clock} whose instant can be moved forward, for window-expiry tests. */
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> instant;
        private final ZoneId zone;

        MutableClock(Instant start, ZoneId zone) {
            this.instant = new AtomicReference<>(start);
            this.zone = zone;
        }

        void advance(Duration duration) {
            instant.updateAndGet(current -> current.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant.get(), newZone);
        }

        @Override
        public Instant instant() {
            return instant.get();
        }
    }

    /** A minimal {@link FilterChain} that just counts how many times it was actually reached. */
    private static final class CountingFilterChain implements FilterChain {
        private final AtomicInteger reached = new AtomicInteger(0);
        private volatile MockHttpServletResponse lastResponse;

        private int timesReached;

        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
            reached.incrementAndGet();
            timesReached = reached.get();
            if (response instanceof MockHttpServletResponse mockResponse) {
                mockResponse.setStatus(200);
            }
        }
    }
}

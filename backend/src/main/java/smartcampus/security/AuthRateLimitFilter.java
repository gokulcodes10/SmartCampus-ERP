package smartcampus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import smartcampus.exception.ErrorResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * §61 "Rate limiting for sensitive endpoints where appropriate", applied to the three
 * unauthenticated auth endpoints that are the classic credential-stuffing /
 * registration-spam / OTP-guessing surface: {@code POST /api/auth/login}, {@code POST
 * /api/auth/register} and every {@code POST /api/auth/password-reset/**} step. Before
 * this filter, only AI endpoints ({@code smartcampus.service.AIRateLimiter}) were
 * rate limited at all.
 *
 * <p>Registered by {@code SecurityConfig} with {@code
 * .addFilterBefore(authRateLimitFilter, JwtAuthenticationFilter.class)} — it MUST run
 * for unauthenticated requests, so it cannot sit behind JWT authentication the way an
 * {@code @PreAuthorize} check would.
 *
 * <p><strong>Keying:</strong> the counter key is the caller's IP address, combined
 * with the {@code email} field from the JSON body when the endpoint's DTO carries one
 * (all three do: {@code LoginRequest}, {@code RegisterRequest}, and the three
 * password-reset DTOs). Keying on IP+email rather than IP alone means one attacker
 * hammering a single known account cannot hide inside the traffic of everyone else
 * briefly sharing that IP (a NAT'd office, a campus wifi) — the classic reason a
 * global per-IP cap is too blunt for a login endpoint. It also means the existing
 * backend test suite, which mints a fresh unique email per test case, does not
 * collide with itself even though every {@code MockMvc} call arrives from the same
 * loopback address.
 *
 * <p>Reads the request body once via {@link CachedBodyHttpServletRequest} so the
 * body is still readable by {@code @RequestBody} downstream (see that class's
 * Javadoc) — the wrapped request, not the original, is what gets passed further down
 * the chain.
 *
 * <p>On rejection this returns HTTP 429 rendered in the exact same §47 {@link
 * ErrorResponse} envelope {@code GlobalExceptionHandler} uses everywhere else — never
 * a stack trace, never a bespoke shape. The message is deliberately generic ("Too
 * many requests...") and never mentions whether the attempted email exists: Phase 2
 * made the wrong-password and unknown-email responses byte-identical specifically so
 * a caller cannot enumerate accounts, and a rate-limit message that said e.g. "too
 * many attempts for this account" would undo that.
 */
@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AuthRateLimitFilter.class);

    private static final Set<String> EXACT_LIMITED_PATHS = Set.of("/api/auth/login", "/api/auth/register");
    private static final String PASSWORD_RESET_PREFIX = "/api/auth/password-reset/";

    private final AuthRateLimiter authRateLimiter;
    private final ObjectMapper objectMapper;

    public AuthRateLimitFilter(AuthRateLimiter authRateLimiter, ObjectMapper objectMapper) {
        this.authRateLimiter = authRateLimiter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isLimitedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String key = buildKey(cachedRequest);

        if (!authRateLimiter.tryConsume(key)) {
            log.warn("Auth rate limit exceeded on {} {}", request.getMethod(), request.getRequestURI());
            writeTooManyRequests(cachedRequest, response);
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    private boolean isLimitedRequest(HttpServletRequest request) {
        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = requestPath(request);
        return EXACT_LIMITED_PATHS.contains(path) || path.startsWith(PASSWORD_RESET_PREFIX);
    }

    private String requestPath(HttpServletRequest request) {
        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        return path;
    }

    private String buildKey(CachedBodyHttpServletRequest request) {
        String ip = clientIp(request);
        String email = extractEmail(request);
        return email == null ? ip : ip + "|" + email;
    }

    /**
     * {@code getRemoteAddr()} rather than {@code X-Forwarded-For}: trusting a
     * client-supplied header as the rate-limit key would let any caller reset their
     * own limit by sending a new value on every request. A deployment that sits
     * behind a reverse proxy must terminate/rewrite that header at the proxy (e.g. via
     * a trusted {@code ForwardedHeaderFilter}) for {@code getRemoteAddr()} to reflect
     * the real client — that is a deployment concern, not something this filter can
     * safely infer on its own.
     */
    private String clientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr == null ? "unknown" : remoteAddr;
    }

    private String extractEmail(CachedBodyHttpServletRequest request) {
        byte[] body = request.getCachedBody();
        if (body.length == 0) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode emailNode = root.get("email");
            if (emailNode == null || !emailNode.isString()) {
                return null;
            }
            String email = emailNode.asString();
            return email == null || email.isBlank() ? null : email.trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ex) {
            // Malformed JSON is not this filter's concern - GlobalExceptionHandler /
            // HttpMessageNotReadableException handles that downstream. Fall back to
            // keying on IP alone rather than failing the request here.
            log.debug("AuthRateLimitFilter could not parse request body for keying: {}", ex.getMessage());
            return null;
        }
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter()
                .write(
                        objectMapper.writeValueAsString(
                                ErrorResponse.of(
                                        HttpStatus.TOO_MANY_REQUESTS.value(),
                                        "TOO_MANY_REQUESTS",
                                        "Too many requests. Please wait before trying again.",
                                        request.getRequestURI())));
    }
}

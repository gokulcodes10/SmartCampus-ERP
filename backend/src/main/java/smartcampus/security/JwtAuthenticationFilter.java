package smartcampus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the {@link SecurityContextHolder} from a {@code Bearer} JWT on every
 * request.
 *
 * <p>Registered as a {@code @Component} but <strong>not</strong> wired into the
 * filter chain here - {@code SecurityConfig} (owned by the integrator) must add it
 * with {@code .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)}.
 *
 * <p>A missing, malformed, expired, or tampered token is never an error at this
 * layer: it simply leaves the request unauthenticated and lets the chain continue, so
 * downstream {@code authorizeHttpRequests} rules (and
 * {@code JwtAuthenticationEntryPoint}) produce the normal 401 instead of this filter
 * throwing a 500. The same is true for a token whose subject no longer resolves to an
 * enabled user.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsServiceImpl userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsServiceImpl userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                authenticate(token, request);
            }
        } catch (RuntimeException ex) {
            // Belt and braces: nothing thrown while establishing authentication should
            // ever turn into a 500. Log and continue unauthenticated instead.
            log.debug("JWT authentication skipped: {}", ex.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(String token, HttpServletRequest request) {
        String email = jwtService.extractEmail(token).orElse(null);
        if (email == null) {
            return;
        }

        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(email);
        } catch (UsernameNotFoundException ex) {
            return;
        }

        if (!userDetails.isEnabled()) {
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}

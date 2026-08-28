package smartcampus.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import smartcampus.entity.Role;
import smartcampus.entity.User;

/**
 * Issues and validates the application's HS256 JWTs.
 *
 * <p>Subject is the user's email; the role is carried as a claim so
 * {@code JwtAuthenticationFilter} and any other consumer can read it without a
 * database round trip. Configuration comes from {@code smartcampus.jwt.secret} /
 * {@code smartcampus.jwt.expiration} (see {@code application.properties}), which map
 * to the {@code JWT_SECRET} / {@code JWT_EXPIRATION} environment variables with no
 * default secret - the app is meant to fail to start rather than sign tokens with a
 * guessable key.
 *
 * <p>Depends on {@code io.jsonwebtoken:jjwt-api} (compile) plus {@code jjwt-impl} and
 * {@code jjwt-jackson} (runtime), which are not yet in {@code pom.xml} - see this
 * agent's final report for the exact coordinates. This class will not compile until
 * they are added.
 */
@Component
public class JwtService {

    private static final String ROLE_CLAIM = "role";
    private static final String FULL_NAME_CLAIM = "fullName";

    private final SecretKey signingKey;
    private final long expirationMillis;

    public JwtService(
            @Value("${smartcampus.jwt.secret}") String secret,
            @Value("${smartcampus.jwt.expiration}") long expirationMillis) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "smartcampus.jwt.secret is not set. Provide JWT_SECRET in the environment "
                            + "(see .env.example) before starting the application.");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        // HS256 requires at least 256 bits (32 bytes) of key material. Fail fast at
        // startup rather than at first token issuance if the configured secret is too short.
        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "smartcampus.jwt.secret must be at least 32 bytes (256 bits) for HS256; the "
                            + "configured secret is " + keyBytes.length + " bytes.");
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationMillis = expirationMillis;
    }

    /** Issues a signed token for {@code user}: subject = email, plus role and display-name claims. */
    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(expirationMillis);
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(ROLE_CLAIM, user.getRole().name())
                .claim(FULL_NAME_CLAIM, user.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Parses and verifies {@code token}, returning its claims if it is well-formed,
     * correctly signed, and not expired - empty otherwise. Never throws: malformed,
     * tampered, and expired tokens are all reported the same way, as "not valid",
     * which is exactly what {@code JwtAuthenticationFilter} needs to fall through to
     * an unauthenticated request instead of a 500.
     */
    public Optional<Claims> tryParse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException covers expired, malformed, unsupported, and bad-signature
            // (tampered) tokens; IllegalArgumentException covers a blank/garbage string.
            return Optional.empty();
        }
    }

    public Optional<String> extractEmail(String token) {
        return tryParse(token).map(Claims::getSubject);
    }

    public Optional<Role> extractRole(String token) {
        return tryParse(token)
                .map(claims -> claims.get(ROLE_CLAIM, String.class))
                .map(Role::valueOf);
    }
}

package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.AuthResponse;
import smartcampus.dto.LoginRequest;
import smartcampus.dto.RegisterRequest;
import smartcampus.dto.UserResponse;
import smartcampus.entity.User;
import smartcampus.service.AuthService;

/**
 * Registration, login, and "who am I" for the JWT auth scheme.
 *
 * <p>{@code /register} and {@code /login} must be reachable without a token — that
 * permit rule lives in {@code smartcampus.config.SecurityConfig} (owned by the
 * integrator), not here. {@code /me} relies on {@code JwtAuthenticationFilter} having
 * already populated the {@code SecurityContextHolder} with the {@link User} principal
 * for the request to reach this controller authenticated at all.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** Self-registration — always creates a {@code STUDENT} account (clarification G1). */
    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse created = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    /** Verifies credentials and returns a signed JWT plus the caller's safe profile. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** The account the caller's JWT resolves to. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(authService.currentUser(currentUser));
    }
}

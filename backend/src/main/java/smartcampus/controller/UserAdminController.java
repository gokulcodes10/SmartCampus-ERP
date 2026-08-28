package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.ProvisionUserRequest;
import smartcampus.dto.UserResponse;
import smartcampus.service.UserProvisioningService;

/**
 * Admin-only user administration.
 *
 * <p>The {@code ADMIN} restriction on {@code /api/users/**} is declared in
 * {@code smartcampus.config.SecurityConfig}, not with a method-security annotation
 * here, so that a non-admin is rejected by the filter chain before any controller code
 * runs. A {@code STUDENT} or {@code FACULTY} token against this route gets a 403 in the
 * §47 envelope via {@code JwtAccessDeniedHandler}.
 */
@RestController
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserProvisioningService userProvisioningService;

    public UserAdminController(UserProvisioningService userProvisioningService) {
        this.userProvisioningService = userProvisioningService;
    }

    /**
     * Provisions a {@code FACULTY}, {@code ADMIN} or {@code STUDENT} account
     * (clarification G1 — staff accounts exist only by an admin creating them).
     */
    @PostMapping
    public ResponseEntity<UserResponse> provision(
            @Valid @RequestBody ProvisionUserRequest request) {
        UserResponse created = userProvisioningService.provision(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}

package smartcampus.controller;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import smartcampus.dto.PasswordResetConfirmDto;
import smartcampus.dto.PasswordResetRequestDto;
import smartcampus.dto.PasswordResetResponse;
import smartcampus.dto.PasswordResetVerifyDto;
import smartcampus.service.PasswordResetService;

/**
 * The forgot-password flow (PROJECT_PLAN.md Phase 2). All three endpoints must stay
 * publicly reachable - see this agent's final report for the exact {@code
 * SecurityConfig} rule the integrator needs to add, since that file is owned
 * elsewhere.
 *
 * <p>Every response is {@link PasswordResetResponse}, a bare message, and every
 * failure surfaces as the standard §47 error envelope via {@code
 * smartcampus.exception.GlobalExceptionHandler} - see {@link PasswordResetService}
 * for why the message is identical whether or not the account exists.
 */
@RestController
@RequestMapping("/api/auth/password-reset")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** Step 1: request an OTP be emailed to {@code email}, if an account owns it. */
    @PostMapping("/request")
    public ResponseEntity<PasswordResetResponse> requestReset(
            @Valid @RequestBody PasswordResetRequestDto request) {
        return ResponseEntity.ok(passwordResetService.requestReset(request.email()));
    }

    /** Step 2: check an OTP is currently valid, without consuming it. */
    @PostMapping("/verify")
    public ResponseEntity<PasswordResetResponse> verifyOtp(
            @Valid @RequestBody PasswordResetVerifyDto request) {
        return ResponseEntity.ok(passwordResetService.verifyOtp(request.email(), request.otp()));
    }

    /** Step 3: validate the OTP one more time, set the new password, and consume it. */
    @PostMapping("/reset")
    public ResponseEntity<PasswordResetResponse> resetPassword(
            @Valid @RequestBody PasswordResetConfirmDto request) {
        return ResponseEntity.ok(
                passwordResetService.resetPassword(
                        request.email(), request.otp(), request.newPassword()));
    }
}

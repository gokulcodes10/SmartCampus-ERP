package smartcampus.service;

import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import smartcampus.dto.PasswordResetResponse;
import smartcampus.entity.PasswordResetToken;
import smartcampus.entity.User;
import smartcampus.exception.BadRequestException;
import smartcampus.repository.PasswordResetTokenRepository;
import smartcampus.repository.UserRepository;
import smartcampus.util.OtpUtils;

/**
 * The forgot-password flow: request an OTP, verify it, and use it once to set a new
 * password.
 *
 * <p><b>Non-enumerating by construction.</b> Every public method returns the exact
 * same {@link PasswordResetResponse} (or throws the exact same {@link
 * BadRequestException}) whether or not {@code email} belongs to a real account -
 * {@link #requestReset} never reveals whether it sent mail, and {@link #verifyOtp} /
 * {@link #resetPassword} route "no such account" through the identical generic
 * failure used for "wrong OTP", "expired", "already used", and "too many attempts". A
 * caller with no access to the mailbox that received the code has no way to tell any
 * of those apart.
 *
 * <p><b>Single-use.</b> Only {@link #resetPassword} ever flips {@link
 * PasswordResetToken#isUsed()} to {@code true} on success; {@link #verifyOtp} is a
 * read-only pre-check the frontend can use before showing the "set new password"
 * form. A token is also flipped to used the moment its attempt cap is reached, so it
 * cannot be retried into after that point even if the correct code is later supplied.
 *
 * <p>At most one OTP is ever live per user: issuing a new one invalidates whatever was
 * previously outstanding (see {@link PasswordResetTokenRepository#invalidateActiveTokensForUser}).
 */
@Service
public class PasswordResetService {

    private static final int OTP_DIGITS = 6;

    /** Identical for every non-enumerating failure - see class Javadoc. */
    private static final String GENERIC_INVALID_MESSAGE = "Invalid or expired verification code.";

    private static final String REQUEST_RESPONSE_MESSAGE =
            "If an account exists for that email address, a verification code has been sent to it.";

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final long otpExpirationMinutes;
    private final int maxAttempts;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository tokenRepository,
            EmailService emailService,
            PasswordEncoder passwordEncoder,
            @Value("${smartcampus.password-reset.otp-expiration-minutes:15}") long otpExpirationMinutes,
            @Value("${smartcampus.password-reset.max-attempts:5}") int maxAttempts) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailService = emailService;
        this.passwordEncoder = passwordEncoder;
        this.otpExpirationMinutes = otpExpirationMinutes;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Issues a fresh OTP and emails it, if - and only if - {@code email} belongs to an
     * account. Always returns the same message regardless.
     */
    @Transactional
    public PasswordResetResponse requestReset(String email) {
        userRepository.findByEmail(email).ifPresent(this::issueAndSendOtp);
        return new PasswordResetResponse(REQUEST_RESPONSE_MESSAGE);
    }

    private void issueAndSendOtp(User user) {
        tokenRepository.invalidateActiveTokensForUser(user.getId());

        String otp = OtpUtils.generate(OTP_DIGITS);
        PasswordResetToken token = PasswordResetToken.builder()
                .user(user)
                .tokenHash(OtpUtils.hash(otp))
                .expiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes))
                .build();
        tokenRepository.save(token);

        String subject = "SmartCampus ERP password reset code";
        String body =
                "Your SmartCampus ERP password reset code is "
                        + otp
                        + ". It expires in "
                        + otpExpirationMinutes
                        + " minute(s). If you did not request a password reset, you can safely"
                        + " ignore this email.";
        emailService.sendPlainTextEmail(user.getEmail(), subject, body);
    }

    /** Read-only check: is {@code otp} currently valid for {@code email}? Does not consume it. */
    @Transactional(noRollbackFor = BadRequestException.class)
    public PasswordResetResponse verifyOtp(String email, String otp) {
        validateOtp(email, otp);
        return new PasswordResetResponse("Verification code is valid.");
    }

    /** Validates {@code otp}, sets {@code newPassword}, and consumes the token. */
    @Transactional(noRollbackFor = BadRequestException.class)
    public PasswordResetResponse resetPassword(String email, String otp, String newPassword) {
        PasswordResetToken token = validateOtp(email, otp);

        User user = token.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.setUsed(true);
        tokenRepository.save(token);

        return new PasswordResetResponse("Password has been reset successfully.");
    }

    /**
     * Shared validation for {@link #verifyOtp} and {@link #resetPassword}. Every
     * failure path - unknown email, no active token, cap already reached, wrong code -
     * throws the identical {@link BadRequestException}; only success returns
     * normally, with the matched, still-unused token.
     *
     * <p><b>The callers must declare {@code noRollbackFor = BadRequestException.class}.</b>
     * The wrong-code path below increments {@code attemptCount} and then throws, and
     * {@link BadRequestException} is unchecked - under Spring's default rollback rule the
     * throw would roll back the very same transaction that just saved the increment, so
     * the counter would never persist and the attempt cap would silently never engage.
     * That is a brute-force protection bypass, not a cosmetic issue; it was caught by
     * live verification after the cap was observed accepting a correct code following
     * five wrong guesses. Do not remove {@code noRollbackFor} from {@link #verifyOtp} or
     * {@link #resetPassword} without moving this counter into its own transaction.
     */
    private PasswordResetToken validateOtp(String email, String otp) {
        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            throw new BadRequestException(GENERIC_INVALID_MESSAGE);
        }

        PasswordResetToken token = tokenRepository
                .findFirstByUser_IdAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(
                        user.getId(), LocalDateTime.now())
                .orElse(null);
        if (token == null) {
            throw new BadRequestException(GENERIC_INVALID_MESSAGE);
        }

        if (token.getAttemptCount() >= maxAttempts) {
            token.setUsed(true);
            tokenRepository.save(token);
            throw new BadRequestException(GENERIC_INVALID_MESSAGE);
        }

        String candidateHash = OtpUtils.hash(otp);
        if (!OtpUtils.matches(candidateHash, token.getTokenHash())) {
            token.setAttemptCount(token.getAttemptCount() + 1);
            if (token.getAttemptCount() >= maxAttempts) {
                token.setUsed(true);
            }
            tokenRepository.save(token);
            throw new BadRequestException(GENERIC_INVALID_MESSAGE);
        }

        return token;
    }
}

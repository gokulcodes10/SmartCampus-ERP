package smartcampus.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

/**
 * Generates and hashes one-time passcodes for the forgot-password flow.
 *
 * <p>Stateless and free of Spring context, per {@code smartcampus.util}'s package
 * contract. {@link #hash(String)} is a plain SHA-256 digest, not a slow password
 * hash: an OTP is a short-lived, single-use, six-digit secret whose brute-force
 * resistance comes from {@code password_reset_tokens.attempt_count} and its
 * expiry, not from the hash function, and the digest must stay deterministic so a
 * resubmitted code can be looked up by equality (see {@code
 * PasswordResetTokenRepository}). It is still never stored or transmitted as
 * plaintext, satisfying "store it hashed, never plaintext".
 */
public final class OtpUtils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private OtpUtils() {}

    /** A zero-padded, {@code digits}-digit numeric OTP, e.g. {@code "042917"} for digits=6. */
    public static String generate(int digits) {
        if (digits < 1) {
            throw new IllegalArgumentException("digits must be positive");
        }
        int bound = (int) Math.pow(10, digits);
        int value = RANDOM.nextInt(bound);
        return String.format("%0" + digits + "d", value);
    }

    /** Deterministic SHA-256 hex digest of the plaintext OTP. */
    public static String hash(String plainOtp) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(plainOtp.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a JDK-guaranteed algorithm (every conforming implementation
            // provides it) - this can never actually happen.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Constant-time comparison of two hashes, avoiding a timing side-channel on guesses. */
    public static boolean matches(String candidateHash, String storedHash) {
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}

package smartcampus.service;

/**
 * Outbound transactional email, abstracted from the delivery mechanism (§70).
 *
 * <p>Callers - {@code PasswordResetService} today, notification/announcement mail in
 * later phases - depend only on this interface, never on {@code JavaMailSender}, SMTP
 * host/port properties, or any other transport detail. That keeps swapping the
 * implementation (e.g. a provider API instead of SMTP) a config-and-one-class change,
 * the same shape as the {@code AIService} and {@code CodeExecutionService}
 * abstractions elsewhere in the plan. {@code smartcampus.service.SmtpEmailService} is
 * the only implementation in this phase, delivering to Mailpit in development.
 */
public interface EmailService {

    /**
     * Sends a plain-text email. Implementations decide how (and whether) to retry or
     * log a delivery failure; callers that must stay non-enumerating (e.g. password
     * reset) should not let a delivery failure change the response they send back to
     * the caller.
     *
     * @param to recipient address
     * @param subject email subject line
     * @param body plain-text body
     */
    void sendPlainTextEmail(String to, String subject, String body);
}

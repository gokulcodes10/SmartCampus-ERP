package smartcampus.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * {@link EmailService} backed by Spring Mail / SMTP. In development this talks to
 * Mailpit ({@code smartcampus.mail.host}/{@code -port}, defaulting to
 * {@code localhost:1025}); in any other environment it is whatever real SMTP relay the
 * deployment configures. Nothing outside this class or {@code application.properties}
 * knows that SMTP is involved - see {@link EmailService}'s §70 contract.
 *
 * <p>A send failure is logged and swallowed rather than propagated: the password-reset
 * flow that is this service's only caller today must return the same non-enumerating
 * response to the client regardless of whether the mail relay is reachable, so it must
 * never learn about a delivery failure through a thrown exception.
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${smartcampus.mail.from:noreply@smartcampus.local}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    @Override
    public void sendPlainTextEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
        } catch (MailException ex) {
            // Deliberately not rethrown - see class Javadoc. The failure is still
            // visible operationally via this log line.
            log.error("Failed to send email to {}: {}", to, ex.getMessage(), ex);
        }
    }
}

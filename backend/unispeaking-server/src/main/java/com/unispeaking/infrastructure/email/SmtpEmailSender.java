package com.unispeaking.infrastructure.email;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Sends transactional messages through the configured SMTP provider. */
@Component
@ConditionalOnProperty(name = "unispeaking.auth.email.enabled", havingValue = "true")
public final class SmtpEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final EmailProperties properties;

    public SmtpEmailSender(JavaMailSender mailSender, EmailProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Override
    public void sendVerificationCode(String recipient, String code, int ttlSeconds) {
        if (!StringUtils.hasText(recipient) || !StringUtils.hasText(code) || ttlSeconds <= 0) {
            throw new IllegalArgumentException("recipient, code and ttlSeconds are required");
        }
        try {
            var message = mailSender.createMimeMessage();
            var helper = new MimeMessageHelper(message, false, StandardCharsets.UTF_8.name());
            helper.setFrom(properties.fromAddress(), properties.fromName());
            helper.setTo(recipient.trim());
            helper.setSubject("UniSpeaking 邮箱验证码");
            helper.setText("你的 UniSpeaking 验证码是：" + code
                    + "\n验证码 " + (ttlSeconds / 60) + " 分钟内有效，请勿向他人透露。", false);
            mailSender.send(message);
        } catch (Exception exception) {
            throw new EmailDeliveryException("Unable to send verification email", exception);
        }
    }

    public static final class EmailDeliveryException extends RuntimeException {
        public EmailDeliveryException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

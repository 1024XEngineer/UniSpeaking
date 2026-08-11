package com.unispeaking.infrastructure.email;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Properties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpEmailSenderTest {

    @Test
    void sendsPlainVerificationEmailWithConfiguredSender() throws Exception {
        var mailSender = mock(JavaMailSender.class);
        var message = new MimeMessage(Session.getInstance(new Properties()));
        org.mockito.Mockito.when(mailSender.createMimeMessage()).thenReturn(message);
        var properties = new EmailProperties(
                true,
                "smtpdm.aliyun.com",
                465,
                "no-reply@mail.unispeaking.cn",
                "ignored-in-unit-test",
                "no-reply@mail.unispeaking.cn",
                "UniSpeaking",
                true);

        new SmtpEmailSender(mailSender, properties)
                .sendVerificationCode("person@example.com", "123456", 600);

        verify(mailSender).send(any(MimeMessage.class));
        assertThat(message.getFrom()[0].toString()).contains("no-reply@mail.unispeaking.cn");
        assertThat(message.getRecipients(MimeMessage.RecipientType.TO)[0].toString())
                .isEqualTo("person@example.com");
        assertThat(message.getSubject()).isEqualTo("UniSpeaking 邮箱验证码");
        assertThat(message.getContent().toString()).contains("123456");
        assertThat(message.getContent().toString()).contains("10 分钟");
    }
}

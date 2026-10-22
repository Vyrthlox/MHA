package com.blog.aispringboot.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 走 SMTP 真实投递。
 *
 * <p>由 {@code config/MailConfig} 在 {@code spring.mail.host} 存在时装配。
 */
@Slf4j
@RequiredArgsConstructor
public class SmtpMailDelivery implements MailDelivery {

    private final JavaMailSender mailSender;
    private final String from;

    @Override
    public void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
        log.info("邮件已发送: to={}, subject={}", to, subject);
    }
}

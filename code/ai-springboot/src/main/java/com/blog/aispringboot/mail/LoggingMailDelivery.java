package com.blog.aispringboot.mail;

import lombok.extern.slf4j.Slf4j;

/**
 * 未配置 SMTP 时的兜底实现：只把邮件内容打到日志，不真的发信。
 *
 * <p>由 {@code config/MailConfig} 在 {@code spring.mail.host} 缺失时装配。
 *
 * <p>用 WARN 级别是刻意的——它需要在满屏 INFO 里跳出来，因为开发时验证码就藏在这行日志里；
 * 同时也提醒「当前没有真实投递」这件事。
 */
@Slf4j
public class LoggingMailDelivery implements MailDelivery {

    @Override
    public void send(String to, String subject, String text) {
        log.warn("""

                ========= 邮件未真实投递（未配置 spring.mail.host） =========
                  收件人 : {}
                  主题   : {}
                  正文   : {}
                ============================================================
                """, to, subject, text);
    }
}

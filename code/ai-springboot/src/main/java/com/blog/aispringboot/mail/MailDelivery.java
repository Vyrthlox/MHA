package com.blog.aispringboot.mail;

/**
 * 邮件投递抽象。
 *
 * <p>有两个实现，运行时二选一（装配逻辑见 {@code config/MailConfig}）：
 * <ul>
 *   <li>{@link SmtpMailDelivery} —— 配了 {@code spring.mail.host} 时真发邮件</li>
 *   <li>{@link LoggingMailDelivery} —— 没配时把内容打到日志</li>
 * </ul>
 *
 * <p>引入这层抽象是为了让「没有 SMTP 凭证」不成为联调的阻塞点：验证码的生成、冷却、
 * 过期、作废、校验这些真正的业务逻辑与「怎么把信送出去」无关，可以在打日志的模式下
 * 完整验证。将来拿到授权码，只改配置文件，业务代码零改动。
 */
public interface MailDelivery {

    /**
     * 发送一封纯文本邮件。
     *
     * @param to      收件人
     * @param subject 主题
     * @param text    正文
     */
    void send(String to, String subject, String text);
}

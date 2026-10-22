package com.blog.aispringboot.config;

import com.blog.aispringboot.mail.LoggingMailDelivery;
import com.blog.aispringboot.mail.MailDelivery;
import com.blog.aispringboot.mail.SmtpMailDelivery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

/**
 * 邮件投递的装配：配了 SMTP 就真发，没配就退化成打日志。
 *
 * <p><b>为什么用一个 {@code @Bean} 方法里的 if，而不是 {@code @ConditionalOnProperty}
 * 加 {@code @ConditionalOnMissingBean} 两个 bean：</b>
 * <ul>
 *   <li>{@code @ConditionalOnMissingBean} 要求「先注册真实现、再判断兜底实现」，而这依赖
 *       {@code @Bean} 方法的处理顺序。Spring 官方文档明确说它只应该用在自动配置类里，
 *       在用户自己的 {@code @Configuration} 中顺序没有保证。</li>
 *   <li>想用两个 {@code @ConditionalOnProperty} 表达「有 host / 无 host」也不行：
 *       {@code matchIfMissing=true} 在属性<b>存在</b>时同样会匹配，两个 bean 会同时生效。</li>
 * </ul>
 * 这里的 if 虽然少了点声明式的味道，但把判断放在一处、行为完全确定，也没有顺序陷阱。
 */
@Slf4j
@Configuration
public class MailConfig {

    @Bean
    public MailDelivery mailDelivery(Environment env,
                                     ObjectProvider<JavaMailSender> mailSenderProvider,
                                     @Value("${mha.mail.from:no-reply@mha.local}") String from) {
        String host = env.getProperty("spring.mail.host");
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        // 两个条件都要判：host 没配时 Boot 的 MailSenderAutoConfiguration 本来就不会建
        // JavaMailSender；反过来若 bean 因别的原因缺失，也不该在这里 NPE。
        if (host == null || host.isBlank() || mailSender == null) {
            log.warn("未配置 spring.mail.host —— 邮件不会真实投递，验证码将输出到本日志中");
            return new LoggingMailDelivery();
        }

        log.info("邮件投递已启用: host={}", host);
        return new SmtpMailDelivery(mailSender, from);
    }
}

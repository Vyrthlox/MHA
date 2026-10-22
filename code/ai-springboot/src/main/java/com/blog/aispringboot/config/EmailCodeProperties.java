package com.blog.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 邮箱验证码策略，对应 application.properties 中的 {@code mha.email-code.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mha.email-code")
public class EmailCodeProperties {

    /** 验证码有效期（秒） */
    private int expireSeconds = 300;

    /** 两次发送之间的最小间隔（秒），服务端强制 */
    private int cooldownSeconds = 60;

    /** 同一个验证码允许的最大校验失败次数，超过就作废 */
    private int maxAttempts = 5;

    /** 验证码位数 */
    private int length = 6;
}

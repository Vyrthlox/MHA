package com.blog.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置，对应 application.properties 中的 {@code jwt.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** HS256 密钥，长度必须 >= 32 字节 */
    private String secret;

    /** token 有效期（秒） */
    private long expireSeconds = 7200;

    /** 签发者 */
    private String issuer = "ai-springboot";
}

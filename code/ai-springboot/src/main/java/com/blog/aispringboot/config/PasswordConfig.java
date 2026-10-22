package com.blog.aispringboot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>只用到 {@code spring-security-crypto} 这一个 jar（无 Spring Security 过滤器链），
 * 因此不会出现「所有接口被默认锁死」的情况。
 */
@Configuration
public class PasswordConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 自动生成随机盐并内嵌在哈希串中，无需外部盐值
        return new BCryptPasswordEncoder();
    }
}

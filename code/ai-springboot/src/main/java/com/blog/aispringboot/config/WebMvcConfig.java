package com.blog.aispringboot.config;

import com.blog.aispringboot.security.JwtAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册 JWT 鉴权拦截器。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtAuthInterceptor jwtAuthInterceptor;

    /**
     * 白名单是「<b>全量减去放行项</b>」而不是黑名单：以后新增的接口默认就是受保护的。
     * 请不要把它改成「只拦截某几个路径」的形式。
     *
     * <p>注意这里的路径是 <b>context-path 相对</b>的：配置了
     * {@code server.servlet.context-path=/api} 之后要写 {@code /user/login}，
     * 而不是 {@code /api/user/login}。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",      // 登录
                        "/user/register",   // 注册
                        "/user/email-code", // 发送注册验证码（注册前就要用，必须匿名可访问）
                        "/error",           // 异常转发（context-path 下实际为 /api/error）
                        "/favicon.ico",
                        "/assets/**",
                        "/static/**",
                        "/index.html",
                        "/"
                );
    }
}

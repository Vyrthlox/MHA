package com.blog.aispringboot.security;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.common.ResultCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 鉴权拦截器。
 *
 * <p><b>为什么用 HandlerInterceptor 而不是 OncePerRequestFilter：</b>
 * {@code preHandle} 抛出的异常会进入 {@code DispatcherServlet#processHandlerException}，
 * 由同一个 {@code @RestControllerAdvice} 处理，因此能精确产出前端约定的
 * {@code {"code":-1,...}}。而过滤器在 DispatcherServlet <b>之外</b>运行，
 * 异常会走 Servlet 容器 → Boot 的 BasicErrorController → 完全不同的 JSON 结构，
 * 前端拦截器就认不出来了。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // CORS 预检请求不带 Authorization 头，直接放行
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            return true;
        }

        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(PREFIX)) {
            throw new BusinessException(ResultCode.LOGIN_EXPIRED, "登录已过期，请重新登录");
        }

        try {
            Claims claims = jwtUtil.parseToken(header.substring(PREFIX.length()));
            UserContext.set(new LoginUser(
                    jwtUtil.getUserId(claims),
                    claims.get("username", String.class),
                    claims.get("status", Integer.class)));
            return true;
        } catch (ExpiredJwtException e) {
            throw new BusinessException(ResultCode.LOGIN_EXPIRED, "登录已过期，请重新登录");
        } catch (JwtException | IllegalArgumentException e) {
            throw new BusinessException(ResultCode.LOGIN_EXPIRED, "登录状态无效，请重新登录");
        }
    }

    /**
     * 请求结束（含处理器抛异常的情况）时清理 ThreadLocal。
     *
     * <p>这一步是必须的：Tomcat 线程会被复用，不清理会导致另一个请求读到上一个
     * 用户的身份。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}

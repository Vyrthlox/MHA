package com.blog.aispringboot.controller;

import com.blog.aispringboot.common.Result;
import com.blog.aispringboot.dto.LoginRequest;
import com.blog.aispringboot.dto.LoginResponse;
import com.blog.aispringboot.dto.RegisterRequest;
import com.blog.aispringboot.dto.SendCodeRequest;
import com.blog.aispringboot.dto.SendCodeResponse;
import com.blog.aispringboot.dto.UserInfo;
import com.blog.aispringboot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户认证接口。
 *
 * <p>{@code /api} 前缀由 {@code server.servlet.context-path} 提供，这里不重复写。
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 发送注册邮箱验证码。
     *
     * <p>注册前调用，因此是匿名可访问的（在 {@code WebMvcConfig} 的白名单里）。
     * 两个时间约束都由服务端强制：60 秒内不能重发，验证码 5 分钟有效，
     * 且 5 分钟内重发会让旧验证码立即失效。
     */
    @PostMapping("/email-code")
    public Result<SendCodeResponse> emailCode(@Valid @RequestBody SendCodeRequest req) {
        return Result.success("验证码已发送", userService.sendRegisterCode(req.getEmail()));
    }

    /** 注册 */
    @PostMapping("/register")
    public Result<Map<String, Object>> register(@Valid @RequestBody RegisterRequest req) {
        Long userId = userService.register(req);
        return Result.success("注册成功", Map.of("userId", userId));
    }

    /** 登录 */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return Result.success("登录成功", userService.login(req));
    }

    /**
     * 当前登录用户信息。
     *
     * <p>这是第一个受保护的接口，用来端到端验证 {@code JwtAuthInterceptor} 与
     * {@code UserContext} 是否工作正常。
     */
    @GetMapping("/info")
    public Result<UserInfo> info() {
        return Result.success(userService.currentUserInfo());
    }
}

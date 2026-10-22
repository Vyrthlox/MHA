package com.blog.aispringboot.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求。
 *
 * <p>字段名沿用前端的 {@code username}，但实际接受<b>用户名或邮箱</b>
 * （前端表单项的 label 就是「用户名或邮箱」）。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "请输入用户名或邮箱")
    private String username;

    @NotBlank(message = "请输入密码")
    private String password;
}

package com.blog.aispringboot.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "请输入用户名")
    @Size(min = 3, max = 50, message = "用户名长度需在 3-50 之间")
    private String username;

    @NotBlank(message = "请输入邮箱")
    @Email(message = "邮箱格式不正确")
    @Size(max = 100, message = "邮箱长度不能超过 100")
    private String email;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 64, message = "密码长度需在 6-64 之间")
    private String password;

    /**
     * 邮箱验证码，由 {@code POST /user/email-code} 下发。
     *
     * <p>这里只做「6 位数字」的形状校验，真正是否有效（存在、未过期、未超失败次数）
     * 由 {@code EmailCodeService#verifyAndConsume} 判断。
     */
    @NotBlank(message = "请输入验证码")
    @Pattern(regexp = "\\d{6}", message = "验证码为 6 位数字")
    private String code;

    /**
     * 前端 register.vue 会把整个表单（含确认密码）一起提交。
     *
     * <p>这里显式声明并校验，不依赖「未知字段自动忽略」的默认行为。
     * 两次密码是否一致在服务层判断，而不是用类级的 {@code @AssertTrue}——
     * 后者会引入一个合成属性并牵扯序列化配置，收益为零。
     */
    @NotBlank(message = "请再次输入密码")
    private String confirmPassword;
}

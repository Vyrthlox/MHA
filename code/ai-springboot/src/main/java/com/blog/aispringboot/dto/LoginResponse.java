package com.blog.aispringboot.dto;

/**
 * 登录成功响应。
 *
 * <p>前端契约：{@code data.token} 与 {@code data.userInfo} 缺一不可
 * （见 {@code ai-vue/src/views/login.vue}）。
 */
public record LoginResponse(String token, UserInfo userInfo) {
}

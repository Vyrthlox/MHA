package com.blog.aispringboot.dto;

/**
 * 发送验证码的响应。
 *
 * <p>把两个时间都回给前端，让前端不必硬编码：倒计时的起点（cooldownSeconds）和验证码的
 * 有效期（expireSeconds）都由后端说了算，将来改配置前端不用跟着改。
 *
 * @param expireSeconds   验证码有效期（秒）
 * @param cooldownSeconds 距离下次可发送的秒数
 */
public record SendCodeResponse(int expireSeconds, int cooldownSeconds) {
}

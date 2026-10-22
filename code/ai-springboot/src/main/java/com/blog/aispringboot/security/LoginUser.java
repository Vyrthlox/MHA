package com.blog.aispringboot.security;

/**
 * 当前登录用户的轻量视图，由 JWT 解析得到。
 */
public record LoginUser(Long userId, String username, Integer status) {
}

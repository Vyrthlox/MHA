package com.blog.aispringboot.dto;

/**
 * 返回给前端的用户信息。
 *
 * <p><b>绝不包含</b> passwordHash 或 salt。
 *
 * @param userId     用户 id
 * @param username   用户名（前端 Navbar 显示这个）
 * @param email      邮箱
 * @param status     1=管理员，2=普通用户
 * @param statusText 状态的中文描述
 */
public record UserInfo(Long userId, String username, String email, Integer status, String statusText) {
}

package com.blog.aispringboot.common;

/**
 * 统一状态码常量。
 *
 * <p>注意：本项目所有接口都返回 HTTP 200，业务状态放在响应体的 {@code code} 字段里。
 * 前端 {@code ai-vue/src/utils/request.js} 的响应拦截器依赖这些取值。
 */
public final class ResultCode {

    private ResultCode() {
    }

    /** 成功 */
    public static final int SUCCESS = 200;

    /**
     * 登录过期 / 未认证。
     *
     * <p><b>这个值是前端契约的一部分</b>：拦截器看到 {@code code === -1} 就会清空
     * localStorage 里的 token 并跳转登录页。改动它必须同步改前端。
     */
    public static final int LOGIN_EXPIRED = -1;

    /** 通用业务错误 */
    public static final int ERROR = 500;
}

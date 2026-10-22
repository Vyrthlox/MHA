package com.blog.aispringboot.common;

import lombok.Data;

/**
 * 统一响应体。
 *
 * <p>前端约定：{@code code === 200} 为成功；{@code code === -1} 为登录过期。
 */
@Data
public class Result<T> {

    private Integer code;
    private String msg;
    private T data;

    private static <T> Result<T> build(int code, String msg, T data) {
        Result<T> r = new Result<>();
        r.setCode(code);
        r.setMsg(msg);
        r.setData(data);
        return r;
    }

    public static <T> Result<T> success(T data) {
        return build(ResultCode.SUCCESS, "操作成功", data);
    }

    /**
     * 成功并自定义提示语。
     *
     * <p><b>不要新增单参数的 {@code success(String msg)} 重载</b>：在泛型下
     * {@code Result.success("注册成功")} 会绑定到 {@code success(T data)}，
     * 静默产出 {@code {"code":200,"msg":"操作成功","data":"注册成功"}}。
     */
    public static <T> Result<T> success(String msg, T data) {
        return build(ResultCode.SUCCESS, msg, data);
    }

    public static <T> Result<T> error(String msg) {
        return build(ResultCode.ERROR, msg, null);
    }

    public static <T> Result<T> error(int code, String msg) {
        return build(code, msg, null);
    }
}

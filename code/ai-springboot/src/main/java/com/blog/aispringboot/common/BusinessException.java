package com.blog.aispringboot.common;

import lombok.Getter;

/**
 * 业务异常，由 {@code GlobalExceptionHandler} 转成统一响应体。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final int code;

    public BusinessException(String msg) {
        this(ResultCode.ERROR, msg);
    }

    public BusinessException(int code, String msg) {
        super(msg);
        this.code = code;
    }
}

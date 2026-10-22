package com.blog.aispringboot.exception;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.common.Result;
import com.blog.aispringboot.common.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 全局异常处理。
 *
 * <p><b>刻意让所有分支都返回 HTTP 200</b>，错误信息放在响应体的 {@code code} 字段里。
 * 原因是前端的响应拦截器把 {@code code === -1} 的登录过期逻辑放在<b>成功分支</b>里，
 * 若这里返回 HTTP 401，axios 会走 error 分支，前端就认不出这个约定的哨兵值了。
 * 代价是真实的服务端错误也是 HTTP 200 + {@code code:500}，因此前端必须按 {@code code}
 * 而非 HTTP 状态码分支判断。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 业务异常：自定义 code */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.error(e.getCode(), e.getMessage());
    }

    /** @Valid @RequestBody 校验失败：取第一条字段错误的信息 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数校验失败");
        return Result.error(msg);
    }

    /** 方法级校验（Spring Framework 6.1+ 引入的类型） */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public Result<Void> handleMethodValidation(HandlerMethodValidationException e) {
        return Result.error("参数校验失败");
    }

    /** 请求体不是合法 JSON */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleUnreadable(HttpMessageNotReadableException e) {
        return Result.error("请求参数格式不正确");
    }

    /** 路径不存在 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.error(404, "请求的接口不存在");
    }

    /**
     * 唯一索引冲突的兜底。
     *
     * <p>注册时的 {@code existsByUsername} 检查与插入之间不是原子的，并发注册同一用户名
     * 可能两个请求都通过检查，此时由数据库的 UNIQUE 约束拦下，在这里转成友好提示。
     */
    @ExceptionHandler(DuplicateKeyException.class)
    public Result<Void> handleDuplicate(DuplicateKeyException e) {
        log.warn("唯一索引冲突: {}", e.getMessage());
        return Result.error("用户名或邮箱已被注册");
    }

    /** 兜底：不把堆栈暴露给前端 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return Result.error(ResultCode.ERROR, "服务器内部错误");
    }
}

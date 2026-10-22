package com.blog.aispringboot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 发送一条用户消息。
 *
 * <p>长度上限 2000 字符：既避免一次请求把上下文撑爆，也防止误粘贴大段内容
 * 造成不必要的模型费用。前端输入框同样限制了 2000。
 */
@Data
public class SendMessageRequest {

    @NotBlank(message = "消息内容不能为空")
    @Size(max = 2000, message = "单条消息不能超过 2000 个字符")
    private String content;
}

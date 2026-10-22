package com.blog.aispringboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 一次问答的结果。
 *
 * <p>字段与 {@link ChatMessageVO} 保持同名，前端可以直接把 {@code data}
 * 当作一条助手消息推进消息列表；{@code messageCount} 用于刷新会话列表上的计数。
 */
public record ChatReplyVO(
        String sessionId,
        String id,
        String role,
        String content,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt,
        int messageCount) {
}

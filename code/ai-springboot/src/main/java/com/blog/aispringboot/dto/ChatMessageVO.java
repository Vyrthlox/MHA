package com.blog.aispringboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 单条消息，字段名与 home.vue 的 {@code messagesMap} 保持一致。
 */
public record ChatMessageVO(
        String id,
        String role,
        String content,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime createdAt) {
}

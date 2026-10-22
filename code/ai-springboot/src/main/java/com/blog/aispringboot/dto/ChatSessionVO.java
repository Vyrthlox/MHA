package com.blog.aispringboot.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.LocalDateTime;

/**
 * 会话列表项。
 *
 * <p>{@code title/preview} 与 {@code sessionTitle/lastMessageContent} 是同一份数据的两个别名：
 * 对话页（home.vue）用前者，管理端咨询记录页（consultations.vue）用后者。
 * 前端两处都已写死字段名，这里同时返回，避免为了一期记忆功能改动已完成的页面。
 */
public record ChatSessionVO(
        String id,
        String title,
        String preview,
        String sessionTitle,
        String lastMessageContent,
        Integer messageCount,
        String userNickname,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime startedAt,
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime updatedAt) {
}

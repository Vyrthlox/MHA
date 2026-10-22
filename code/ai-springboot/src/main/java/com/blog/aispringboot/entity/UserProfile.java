package com.blog.aispringboot.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户长期画像，对应 {@code user_profiles}。
 *
 * <p>这里不使用 Spring Data JDBC 的实体映射：本表是显式的 upsert，
 * 且 {@code user_id} 是外部赋值的主键，用 {@code JdbcTemplate} 更好控制 SQL。
 */
@Data
public class UserProfile {

    private Long userId;

    private String summary;

    /** JSON 字符串 */
    private String traitsJson;

    /** JSON 字符串 */
    private String mentalStatusJson;

    /** 0~5，只升不降 */
    private Integer riskLevel;

    /** important / normal；画像固定 important，不参与自动清理 */
    private String sensitivity;

    private Integer memoryCount;

    private LocalDateTime lastExtractedAt;
}

package com.blog.aispringboot.repository;

import com.blog.aispringboot.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * {@code user_profiles} 的读写。
 *
 * <p>用 {@link JdbcTemplate} 而不是 Spring Data JDBC：这里需要
 * {@code INSERT ... ON DUPLICATE KEY UPDATE}，且只更新模型实际给出的字段。
 */
@Repository
@RequiredArgsConstructor
public class UserProfileRepository {

    private static final String SELECT_COLUMNS = """
            SELECT user_id, summary, traits_json, mental_status_json,
                   risk_level, sensitivity, memory_count, last_extracted_at
            FROM user_profiles
            """;

    private final JdbcTemplate jdbcTemplate;

    public Optional<UserProfile> findByUserId(Long userId) {
        List<UserProfile> rows = jdbcTemplate.query(SELECT_COLUMNS + " WHERE user_id = ?",
                this::mapRow, userId);
        return rows.stream().findFirst();
    }

    /**
     * 只覆盖传入的非 null 字段；风险等级取历史最大值，避免模型一次“看起来好些了”
     * 就把高风险标记抹掉。
     */
    public void upsert(Long userId, String summary, String traitsJson, String mentalStatusJson,
                       Integer riskLevel, LocalDateTime extractedAt) {
        String insertSummary = summary == null ? "" : summary;
        String insertTraits = traitsJson == null ? "{}" : traitsJson;
        String insertMental = mentalStatusJson == null ? "{}" : mentalStatusJson;
        int insertRisk = riskLevel == null ? 0 : riskLevel;

        StringBuilder sql = new StringBuilder("""
                INSERT INTO user_profiles
                    (user_id, summary, traits_json, mental_status_json, risk_level,
                     sensitivity, last_extracted_at)
                VALUES (?, ?, ?, ?, ?, 'important', ?)
                ON DUPLICATE KEY UPDATE
                """);
        List<Object> args = new ArrayList<>(List.of(
                userId, insertSummary, insertTraits, insertMental, insertRisk, extractedAt));

        boolean hasUpdate = false;
        if (summary != null) {
            sql.append("summary = ?, ");
            args.add(summary);
            hasUpdate = true;
        }
        if (traitsJson != null) {
            sql.append("traits_json = ?, ");
            args.add(traitsJson);
            hasUpdate = true;
        }
        if (mentalStatusJson != null) {
            sql.append("mental_status_json = ?, ");
            args.add(mentalStatusJson);
            hasUpdate = true;
        }
        if (riskLevel != null) {
            sql.append("risk_level = GREATEST(risk_level, ?), ");
            args.add(riskLevel);
            hasUpdate = true;
        }
        if (!hasUpdate) {
            // 语法上必须至少有一个赋值
            sql.append("user_id = user_id, ");
        }
        // 画像始终是重要数据，不参与“删除会话后 30 天清理”
        sql.append("sensitivity = 'important', ");
        sql.append("last_extracted_at = VALUES(last_extracted_at)");

        jdbcTemplate.update(sql.toString(), args.toArray());
    }

    /** 用向量台账里的已写入条数刷新画像计数，避免重试时累加错位 */
    public void refreshMemoryCount(Long userId) {
        jdbcTemplate.update("""
                UPDATE user_profiles p
                SET memory_count = (
                    SELECT COUNT(*) FROM memory_vectors v
                    WHERE v.user_id = p.user_id AND v.status = 1
                )
                WHERE p.user_id = ?
                """, userId);
    }

    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", userId);
    }

    private UserProfile mapRow(ResultSet rs, int rowNum) throws SQLException {
        UserProfile profile = new UserProfile();
        profile.setUserId(rs.getLong("user_id"));
        profile.setSummary(rs.getString("summary"));
        profile.setTraitsJson(rs.getString("traits_json"));
        profile.setMentalStatusJson(rs.getString("mental_status_json"));
        profile.setRiskLevel(rs.getInt("risk_level"));
        profile.setSensitivity(rs.getString("sensitivity"));
        profile.setMemoryCount(rs.getInt("memory_count"));
        if (rs.getTimestamp("last_extracted_at") != null) {
            profile.setLastExtractedAt(rs.getTimestamp("last_extracted_at").toLocalDateTime());
        }
        return profile;
    }
}

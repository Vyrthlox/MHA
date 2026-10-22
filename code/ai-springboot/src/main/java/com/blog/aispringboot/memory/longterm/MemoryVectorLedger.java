package com.blog.aispringboot.memory.longterm;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * 长期记忆的向量台账。
 *
 * <p>Milvus 的写入与 MySQL 不在同一个事务里，台账记录“哪些 memory_id 属于谁、
 * 是哪个提炼批次写的、是否写成功”。记忆正文仍以 Milvus 为准，台账不复制原文。
 */
@Repository
@RequiredArgsConstructor
public class MemoryVectorLedger {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 登记/覆盖一条记忆。
     *
     * <p>同一条记忆被重新提炼时视为“重新确认”：清空会话删除留下的保留期，
     * 避免它刚被重新写入就进入待清理状态。
     */
    public void markPending(String memoryId, Long userId, String sessionId, String runId,
                            String sensitivity) {
        jdbcTemplate.update("""
                INSERT INTO memory_vectors
                    (memory_id, user_id, session_id, extract_run_id, status, sensitivity)
                VALUES (?, ?, ?, ?, 0, ?)
                ON DUPLICATE KEY UPDATE
                    user_id = VALUES(user_id),
                    session_id = VALUES(session_id),
                    extract_run_id = VALUES(extract_run_id),
                    status = 0,
                    sensitivity = VALUES(sensitivity),
                    retention_until = NULL
                """, memoryId, userId, sessionId, runId, sensitivity);
    }

    public void markWritten(Collection<String> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        for (String id : memoryIds) {
            jdbcTemplate.update("UPDATE memory_vectors SET status = 1 WHERE memory_id = ?", id);
        }
    }

    /** 某会话上次失败/中断留下的半成品，重试前要清掉 */
    public List<String> findPendingBySession(String sessionId) {
        return jdbcTemplate.queryForList("""
                SELECT memory_id FROM memory_vectors
                WHERE session_id = ? AND status = 0
                """, String.class, sessionId);
    }

    public void deleteBySessionAndPending(String sessionId) {
        jdbcTemplate.update("DELETE FROM memory_vectors WHERE session_id = ? AND status = 0", sessionId);
    }

    /**
     * 记录一次召回。normal 记忆如果已进入删除会话后的保留期，则把截止时间顺延，
     * 实现“30 天未被召回才删除”；important 记忆只记使用情况，不设截止时间。
     */
    public void markRecalled(Collection<String> memoryIds, int retentionDays) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(Math.max(1, retentionDays));
        args.addAll(memoryIds);
        jdbcTemplate.update("""
                UPDATE memory_vectors
                SET last_recalled_at = NOW(3),
                    recall_count = recall_count + 1,
                    retention_until = CASE
                        WHEN retention_until IS NULL THEN NULL
                        ELSE DATE_ADD(NOW(3), INTERVAL ? DAY)
                    END
                WHERE status = 1 AND memory_id IN (%s)
                """.formatted(placeholders(memoryIds.size())), args.toArray());
    }

    /**
     * 会话删除时给该会话产生的 normal 记忆设置保留截止时间。
     *
     * <p>important 记忆与用户画像不参与，永远不会被这个机制删掉。
     */
    public int armRetentionBySession(String sessionId, int retentionDays) {
        return jdbcTemplate.update("""
                UPDATE memory_vectors
                SET retention_until = DATE_ADD(NOW(3), INTERVAL ? DAY)
                WHERE session_id = ?
                  AND status = 1
                  AND sensitivity = 'normal'
                """, Math.max(1, retentionDays), sessionId);
    }

    /** 到期可清理的 normal 记忆 */
    public List<DueMemory> findDueRetention(int limit) {
        return jdbcTemplate.query("""
                SELECT memory_id, user_id FROM memory_vectors
                WHERE sensitivity = 'normal'
                  AND status = 1
                  AND retention_until IS NOT NULL
                  AND retention_until <= NOW(3)
                ORDER BY retention_until
                LIMIT ?
                """, (rs, rowNum) -> new DueMemory(rs.getString("memory_id"), rs.getLong("user_id")),
                Math.max(1, limit));
    }

    /** Milvus 删除成功后再标记台账，失败时保留 status=1 等待下次重试 */
    public void markPurged(Collection<String> memoryIds) {
        if (memoryIds == null || memoryIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>(memoryIds);
        jdbcTemplate.update("""
                UPDATE memory_vectors SET status = 2
                WHERE status = 1 AND memory_id IN (%s)
                """.formatted(placeholders(memoryIds.size())), args.toArray());
    }

    public void deleteByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM memory_vectors WHERE user_id = ?", userId);
    }

    private static String placeholders(int size) {
        return String.join(",", Collections.nCopies(size, "?"));
    }

    /** 清理任务用的最小字段集，不复制记忆正文 */
    public record DueMemory(String memoryId, Long userId) {
    }
}

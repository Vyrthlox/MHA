package com.blog.aispringboot.memory.extract;

import com.blog.aispringboot.config.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * outbox 仓储。
 *
 * <p>领取任务用 {@code SELECT ... FOR UPDATE SKIP LOCKED}，允许多实例/多线程安全地
 * 抢任务，不会互相阻塞。状态机：0 待处理 → 1 处理中 → 2 完成 / 3 失败。
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class MemoryExtractTaskRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, session_id, user_id, status, due_at, attempts,
                   last_extracted_count, locked_at, last_error
            FROM memory_extract_tasks
            """;

    private final JdbcTemplate jdbcTemplate;
    private final MemoryProperties properties;

    /**
     * 登记/刷新一次提炼。
     *
     * <p>同一会话只有一行，后到的消息把 due_at 往后推，实现“停止说话 90 秒后才提炼”的防抖。
     * 正在处理中的任务不会被改成待处理，避免并发重复提炼。
     */
    public void schedule(String sessionId, Long userId) {
        int debounce = Math.max(1, properties.getExtract().getDebounceSeconds());
        jdbcTemplate.update("""
                INSERT INTO memory_extract_tasks
                    (session_id, user_id, status, due_at)
                VALUES (?, ?, 0, DATE_ADD(NOW(3), INTERVAL ? SECOND))
                ON DUPLICATE KEY UPDATE
                    due_at = IF(status = 1, due_at, VALUES(due_at)),
                    attempts = IF(status = 1, attempts, 0),
                    last_error = IF(status = 1, last_error, NULL),
                    user_id = VALUES(user_id),
                    status = IF(status = 1, 1, 0)
                """, sessionId, userId, debounce);
    }

    public void cancel(String sessionId) {
        jdbcTemplate.update("DELETE FROM memory_extract_tasks WHERE session_id = ?", sessionId);
    }

    public void cancelByUserId(Long userId) {
        jdbcTemplate.update("DELETE FROM memory_extract_tasks WHERE user_id = ?", userId);
    }

    /** 进程崩溃后 RUNNING 一直不结束，超时后重新入队 */
    public int resetStale() {
        int staleMinutes = Math.max(1, properties.getExtract().getStaleMinutes());
        return jdbcTemplate.update("""
                UPDATE memory_extract_tasks
                SET status = 0, due_at = NOW(3), locked_at = NULL
                WHERE status = 1
                  AND locked_at < DATE_SUB(NOW(3), INTERVAL ? MINUTE)
                """, staleMinutes);
    }

    /**
     * 领取到期任务。方法返回时事务已提交（调用方是另一个 bean，走代理），
     * 因此调度器可以安全地把任务丢给异步线程。
     */
    @Transactional
    public List<MemoryExtractTask> claimDue(int limit) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT id FROM memory_extract_tasks
                WHERE status = 0 AND due_at <= NOW(3)
                ORDER BY due_at
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """, Long.class, limit);
        if (ids.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<Object> args = new ArrayList<>(ids);
        jdbcTemplate.update("""
                UPDATE memory_extract_tasks
                SET status = 1, attempts = attempts + 1, locked_at = NOW(3)
                WHERE id IN (%s)
                """.formatted(placeholders), args.toArray());

        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE id IN (" + placeholders + ") ORDER BY due_at",
                this::mapRow, ids.toArray());
    }

    /**
     * 标记完成；带 status/attempts 条件做 fencing：旧 worker 晚到也不能覆盖新任务。
     *
     * @return true 表示本次更新生效
     */
    public boolean markDone(Long id, int attempts, long lastExtractedCount) {
        return jdbcTemplate.update("""
                UPDATE memory_extract_tasks
                SET status = 2, last_extracted_count = ?, locked_at = NULL, last_error = NULL
                WHERE id = ? AND status = 1 AND attempts = ?
                """, Math.min(lastExtractedCount, Integer.MAX_VALUE), id, attempts) > 0;
    }

    /**
     * 失败：未超过上限则退避重试，否则置为 DEAD。
     */
    public void markFailed(Long id, int attempts, String error) {
        String message = truncate(error, 500);
        int maxAttempts = Math.max(1, properties.getExtract().getMaxAttempts());
        if (attempts < maxAttempts) {
            int delay = Math.max(1, properties.getExtract().getRetryBaseSeconds())
                    * (1 << Math.max(0, attempts - 1));
            jdbcTemplate.update("""
                    UPDATE memory_extract_tasks
                    SET status = 0, due_at = DATE_ADD(NOW(3), INTERVAL ? SECOND),
                        locked_at = NULL, last_error = ?
                    WHERE id = ? AND status = 1 AND attempts = ?
                    """, delay, message, id, attempts);
        } else {
            jdbcTemplate.update("""
                    UPDATE memory_extract_tasks
                    SET status = 3, locked_at = NULL, last_error = ?
                    WHERE id = ? AND status = 1 AND attempts = ?
                    """, message, id, attempts);
            log.error("记忆提炼超过最大重试次数，已置为失败: taskId={}, attempts={}", id, attempts);
        }
    }

    public List<MemoryExtractTask> findBySessionId(String sessionId) {
        return jdbcTemplate.query(SELECT_COLUMNS + " WHERE session_id = ?", this::mapRow, sessionId);
    }

    private MemoryExtractTask mapRow(ResultSet rs, int rowNum) throws SQLException {
        MemoryExtractTask task = new MemoryExtractTask();
        task.setId(rs.getLong("id"));
        task.setSessionId(rs.getString("session_id"));
        task.setUserId(rs.getLong("user_id"));
        task.setStatus(rs.getInt("status"));
        if (rs.getTimestamp("due_at") != null) {
            task.setDueAt(rs.getTimestamp("due_at").toLocalDateTime());
        }
        task.setAttempts(rs.getInt("attempts"));
        task.setLastExtractedCount(rs.getInt("last_extracted_count"));
        if (rs.getTimestamp("locked_at") != null) {
            task.setLockedAt(rs.getTimestamp("locked_at").toLocalDateTime());
        }
        task.setLastError(rs.getString("last_error"));
        return task;
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return null;
        }
        return text.length() <= max ? text : text.substring(0, max);
    }
}

package com.blog.aispringboot.memory.working;

import com.blog.aispringboot.config.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Redis 工作记忆读写。
 *
 * <p><b>所有异常都在这里吞掉并降级</b>：工作记忆只是增强上下文，Redis 挂掉时
 * 聊天仍必须可用。日志只记录会话/用户 id 和异常类型，不打印记忆正文（可能包含敏感信息）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkingMemoryStore {

    private static final String KEY_PREFIX = "mha:wm:";

    private final StringRedisTemplate redisTemplate;
    private final JsonMapper jsonMapper;
    private final MemoryProperties properties;

    public Optional<WorkingMemory> get(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        try {
            String json = redisTemplate.opsForValue().get(key(userId, sessionId));
            if (!StringUtils.hasText(json)) {
                return Optional.empty();
            }
            return Optional.ofNullable(jsonMapper.readValue(json, WorkingMemory.class));
        } catch (Exception e) {
            log.warn("读取工作记忆失败，按空处理: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    public void save(WorkingMemory memory) {
        if (memory == null || memory.getUserId() == null
                || !StringUtils.hasText(memory.getSessionId())) {
            return;
        }
        try {
            memory.setUpdatedAt(System.currentTimeMillis());
            String json = jsonMapper.writeValueAsString(memory);
            redisTemplate.opsForValue().set(key(memory.getUserId(), memory.getSessionId()), json,
                    Duration.ofDays(Math.max(1, properties.getWorkingTtlDays())));
        } catch (Exception e) {
            log.warn("写入工作记忆失败，跳过: userId={}, sessionId={}, error={}",
                    memory.getUserId(), memory.getSessionId(), e.getClass().getSimpleName());
        }
    }

    public void delete(Long userId, String sessionId) {
        if (userId == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        try {
            redisTemplate.delete(key(userId, sessionId));
        } catch (Exception e) {
            log.warn("删除工作记忆失败: userId={}, sessionId={}, error={}",
                    userId, sessionId, e.getClass().getSimpleName());
        }
    }

    /** 取该用户最近更新的一条工作记忆（调试接口未指定 sessionId 时使用） */
    public Optional<WorkingMemory> findLatest(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        String pattern = KEY_PREFIX + userId + ":*";
        WorkingMemory latest = null;
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            while (cursor.hasNext()) {
                String json = redisTemplate.opsForValue().get(cursor.next());
                if (!StringUtils.hasText(json)) {
                    continue;
                }
                WorkingMemory candidate = jsonMapper.readValue(json, WorkingMemory.class);
                if (latest == null || candidate.getUpdatedAt() > latest.getUpdatedAt()) {
                    latest = candidate;
                }
            }
        } catch (Exception e) {
            log.warn("读取最近工作记忆失败: userId={}, error={}", userId, e.getClass().getSimpleName());
            return Optional.empty();
        }
        return Optional.ofNullable(latest);
    }

    /**
     * 清空某个用户的全部工作记忆（“清空我的记忆”接口）。
     *
     * <p>用 SCAN 而不是 KEYS：KEYS 会阻塞 Redis 主线程，数据量大时是事故级操作。
     */
    public void deleteAll(Long userId) {
        if (userId == null) {
            return;
        }
        String pattern = KEY_PREFIX + userId + ":*";
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(pattern).count(100).build())) {
            List<String> batch = new ArrayList<>(100);
            while (cursor.hasNext()) {
                batch.add(cursor.next());
                if (batch.size() >= 100) {
                    redisTemplate.delete(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                redisTemplate.delete(batch);
            }
        } catch (Exception e) {
            log.warn("清空工作记忆失败: userId={}, error={}", userId, e.getClass().getSimpleName());
        }
    }

    private static String key(Long userId, String sessionId) {
        return KEY_PREFIX + userId + ":" + sessionId;
    }
}

package com.blog.aispringboot.memory.longterm;

import com.blog.aispringboot.config.AsyncConfig;
import com.blog.aispringboot.config.MemoryProperties;
import com.blog.aispringboot.memory.extract.MemoryDigest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 长期记忆：用户画像之外的关键信息，写入 Milvus 并通过“先过滤、再向量检索”召回。
 *
 * <p>召回失败（Milvus / Ollama 不可用）时返回空列表，绝不阻断聊天主流程。
 */
@Slf4j
@Service
public class LongTermMemoryService {

    private static final String META_USER_ID = "userId";
    private static final String META_SESSION_ID = "sessionId";
    private static final String META_TYPE = "type";
    private static final String META_CATEGORY = "category";
    private static final String META_STABILITY = "stability";
    private static final String META_IMPORTANCE = "importance";
    private static final String META_SENSITIVITY = "sensitivity";
    private static final String META_KEYWORDS = "keywords";
    private static final String META_CREATED_AT = "createdAt";
    private static final String META_UPDATED_AT = "updatedAt";
    private static final String META_EXPIRE_AT = "expireAt";
    private static final String META_SOURCE_MESSAGE_ID = "sourceMessageId";
    private static final String META_EXTRACT_RUN_ID = "extractRunId";

    private final VectorStore vectorStore;
    private final MemoryVectorLedger ledger;
    private final MemoryProperties properties;
    private final ThreadPoolTaskExecutor vectorExecutor;

    /**
     * 熔断截止时间。Milvus 不可用时直接降级，避免每条消息都卡一次 SDK 重试。
     * 0 表示正常。
     */
    private final AtomicLong unavailableUntil = new AtomicLong();

    public LongTermMemoryService(VectorStore vectorStore,
                                 MemoryVectorLedger ledger,
                                 MemoryProperties properties,
                                 @Qualifier(AsyncConfig.VECTOR_EXECUTOR)
                                 ThreadPoolTaskExecutor vectorExecutor) {
        this.vectorStore = vectorStore;
        this.ledger = ledger;
        this.properties = properties;
        this.vectorExecutor = vectorExecutor;
    }

    /**
     * 写入一批长期记忆。
     *
     * <p>memory_id 由「用户 + 规范化正文」哈希得到，因此同一句话重复提炼时是覆盖而不是叠加；
     * 写入前先按 id 删除旧文档，抵消 Milvus insert 不是 upsert 的行为。
     */
    public void rememberAll(Long userId, String sessionId, String fallbackType,
                            String runId, List<MemoryDigest.MemoryItem> items) {
        if (userId == null || items == null || items.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        List<Document> documents = new ArrayList<>();
        List<String> ids = new ArrayList<>();

        for (MemoryDigest.MemoryItem item : items) {
            String content = normalize(item.getContent());
            if (!StringUtils.hasText(content)) {
                continue;
            }
            content = truncate(content, properties.getMemoryMaxChars());
            String id = stableId(userId, content);
            boolean stable = "stable".equalsIgnoreCase(item.getStability());
            String type = normalizeType(item.getType(), fallbackType);
            String sensitivity = normalizeSensitivity(item.getSensitivity());
            long expireAt = stable
                    ? 0L
                    : now + Math.max(1, properties.getTemporaryTtlDays()) * 86_400_000L;

            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put(META_USER_ID, userId);
            metadata.put(META_SESSION_ID, sessionId);
            metadata.put(META_TYPE, type);
            metadata.put(META_CATEGORY, defaultIfBlank(item.getCategory(), "other"));
            metadata.put(META_STABILITY, stable ? "stable" : "temporary");
            metadata.put(META_IMPORTANCE, clampImportance(item.getImportance()));
            metadata.put(META_SENSITIVITY, sensitivity);
            metadata.put(META_KEYWORDS, joinKeywords(item.getKeywords()));
            metadata.put(META_CREATED_AT, now);
            metadata.put(META_UPDATED_AT, now);
            metadata.put(META_EXPIRE_AT, expireAt);
            metadata.put(META_SOURCE_MESSAGE_ID, "");
            metadata.put(META_EXTRACT_RUN_ID, defaultIfBlank(runId, ""));

            documents.add(new Document(id, content, metadata));
            ids.add(id);
        }

        if (documents.isEmpty()) {
            return;
        }

        for (Document document : documents) {
            String sensitivity = String.valueOf(
                    document.getMetadata().getOrDefault(META_SENSITIVITY, "important"));
            ledger.markPending(document.getId(), userId, sessionId, runId, sensitivity);
        }
        try {
            // Milvus insert 不做 upsert：先删同 id 的旧版本，保证重复提炼是覆盖语义。
            // 整体放在带超时的线程里，Milvus 故障时不会长期占住提炼线程。
            callVector("write", properties.getVectorWriteTimeoutMs(), userId, () -> {
                vectorStore.delete(ids);
                vectorStore.add(documents);
                return null;
            });
            ledger.markWritten(ids);
            log.info("长期记忆写入完成: userId={}, sessionId={}, runId={}, count={}",
                    userId, sessionId, runId, documents.size());
        } catch (RuntimeException e) {
            // 台账保留 status=0，下一次提炼开始前会按会话清理
            log.warn("长期记忆写入失败，等待重试: userId={}, sessionId={}, runId={}, error={}",
                    userId, sessionId, runId, e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * 先按 userId / 过期时间 / 时间窗口（或 stable）/ 类型过滤，再做相似度检索。
     */
    public List<Document> recall(Long userId, String type, String query) {
        if (userId == null || !StringUtils.hasText(query)) {
            return List.of();
        }
        long now = System.currentTimeMillis();
        long cutoff = now - Math.max(1, properties.getRecallWindowDays()) * 86_400_000L;

        FilterExpressionBuilder b = new FilterExpressionBuilder();
        // 注意：MilvusFilterExpressionConverter 不会为嵌套的 Expression 自动加括号，
        // 只会为 Filter.Group 输出括号。若直接写 and(userId, or(...))，转义结果是
        // `userId == 10 && a == 0 || b > now`，按 && 优先级会变成
        // `(userId == 10 && a == 0) || (b > now)`，导致其他用户的记忆被召回。
        // 因此所有 OR 子表达式都必须显式 group。
        FilterExpressionBuilder.Op notExpired = b.or(
                b.eq(META_EXPIRE_AT, 0L),
                b.gt(META_EXPIRE_AT, now));
        FilterExpressionBuilder.Op recentOrStable = b.or(
                b.gte(META_CREATED_AT, cutoff),
                b.eq(META_STABILITY, "stable"));
        FilterExpressionBuilder.Op expression = b.and(
                b.eq(META_USER_ID, userId),
                b.and(b.group(notExpired), b.group(recentOrStable)));
        if (StringUtils.hasText(type)) {
            expression = b.and(expression, b.eq(META_TYPE, type));
        }

        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(Math.max(1, properties.getRecallTopK()))
                .similarityThreshold(properties.getRecallMinScore())
                .filterExpression(expression.build())
                .build();
        List<Document> documents;
        try {
            documents = callVector("recall", properties.getVectorReadTimeoutMs(), userId,
                    () -> vectorStore.similaritySearch(request));
        } catch (Exception e) {
            // callVector 已经记录 op / userId / 原因；这里只负责降级
            return List.of();
        }

        // 召回即“被使用”：normal 记忆的 30 天保留期会顺延。
        // 台账更新失败不能影响聊天，本地的记忆仍然可以正常返回。
        if (documents != null && !documents.isEmpty()) {
            try {
                ledger.markRecalled(documents.stream().map(Document::getId).toList(),
                        properties.getRetentionDays());
            } catch (Exception e) {
                log.debug("记录记忆召回失败，忽略: userId={}, error={}",
                        userId, e.getClass().getSimpleName());
            }
        }
        return documents == null ? List.of() : documents;
    }

    /** 清理到期记忆时按 id 删除向量；失败会抛出，由调用方保留台账等待重试。 */
    public void deleteByIds(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        callVector("purge", properties.getVectorWriteTimeoutMs(), null, () -> {
            vectorStore.delete(ids);
            return null;
        });
    }

    /**
     * 清空某个用户的长期记忆（按 metadata.userId 过滤兜底，再清台账）。
     *
     * <p>向量删除失败会抛异常，调用方应保留台账并提示用户重试，不能假装已清空。
     */
    public void forget(Long userId) {
        if (userId == null) {
            return;
        }
        Filter.Expression byUser = new FilterExpressionBuilder().eq(META_USER_ID, userId).build();
        callVector("forget", properties.getVectorWriteTimeoutMs(), userId, () -> {
            vectorStore.delete(byUser);
            return null;
        });
        ledger.deleteByUserId(userId);
        log.info("已清空用户长期记忆: userId={}", userId);
    }

    /** 重试前清理该会话上次留下的半成品 */
    public void cleanupPending(String sessionId) {
        List<String> pending = ledger.findPendingBySession(sessionId);
        if (!pending.isEmpty()) {
            try {
                callVector("cleanup", properties.getVectorWriteTimeoutMs(), null,
                        () -> {
                            vectorStore.delete(pending);
                            return null;
                        });
                // 只有 Milvus 删除成功才清台账，否则保留 status=0 以便下次重试
                ledger.deleteBySessionAndPending(sessionId);
            } catch (Exception e) {
                log.warn("清理半成品记忆失败: sessionId={}, count={}, error={}",
                        sessionId, pending.size(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * 在隔离线程里执行一次向量库调用，并施加超时与熔断。
     *
     * <p>Milvus SDK 的默认重试（最多 75 次、退避到 3 秒）会让调用线程阻塞数分钟；
     * 这里由调用方限时等待，超时/失败后进入冷却期，期间不再触达 Milvus。
     * 被取消的线程可能仍在 SDK 内部重试，但不会拖住聊天或提炼线程。
     */
    private <T> T callVector(String operation, long timeoutMs, Long userId, Callable<T> action) {
        long now = System.currentTimeMillis();
        long until = unavailableUntil.get();
        if (until > now) {
            throw new IllegalStateException("向量库熔断中，剩余 "
                    + (until - now) / 1000 + " 秒");
        }

        Future<T> future;
        try {
            future = vectorExecutor.submit(action);
        } catch (RuntimeException e) {
            // 线程池已满或已关闭：直接降级，不排队，避免故障时请求堆积
            trip(operation, userId, "rejected");
            throw new IllegalStateException("向量库繁忙: " + operation, e);
        }

        try {
            T result = future.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
            unavailableUntil.set(0);
            return result;
        } catch (TimeoutException e) {
            future.cancel(true);
            trip(operation, userId, "timeout(" + timeoutMs + "ms)");
            throw new IllegalStateException("向量库调用超时: " + operation, e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            trip(operation, userId, cause.getClass().getSimpleName());
            throw new IllegalStateException("向量库调用失败: " + operation, cause);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("向量库调用被中断: " + operation, e);
        }
    }

    /** 记录一次失败并开启冷却期；日志只带操作名、用户 id 和异常类型，不含记忆内容 */
    private void trip(String operation, Long userId, String reason) {
        long cooldownMs = Math.max(0, properties.getVectorCooldownSeconds()) * 1000L;
        if (cooldownMs > 0) {
            unavailableUntil.set(System.currentTimeMillis() + cooldownMs);
        }
        log.warn("向量库不可用，{}s 内降级: op={}, userId={}, reason={}",
                cooldownMs / 1000, operation, userId, reason);
    }

    private static String normalize(String text) {
        return text == null ? "" : text.strip().replaceAll("\\s+", " ");
    }

    private static String truncate(String text, int max) {
        int limit = Math.max(1, max);
        if (text.codePointCount(0, text.length()) <= limit) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, limit));
    }

    /** SHA-256 前 32 位十六进制，长度 32，满足 Milvus VARCHAR(36) 主键限制 */
    private static String stableId(Long userId, String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((userId + "|" + content).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 32);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成记忆 ID", e);
        }
    }

    /** 未知值按 important 处理：宁可多保留，也不能误删用户的重要信息 */
    private static String normalizeSensitivity(String value) {
        return "normal".equalsIgnoreCase(value == null ? "" : value.strip())
                ? "normal" : "important";
    }

    private static String normalizeType(String value, String fallback) {
        if ("treatment".equalsIgnoreCase(value)) {
            return "treatment";
        }
        if ("chat".equalsIgnoreCase(value)) {
            return "chat";
        }
        return "treatment".equalsIgnoreCase(fallback) ? "treatment" : "chat";
    }

    private static int clampImportance(Integer value) {
        if (value == null) {
            return 3;
        }
        return Math.max(1, Math.min(5, value));
    }

    private static String joinKeywords(List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return "";
        }
        String joined = keywords.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .limit(10)
                .reduce((a, b) -> a + "," + b)
                .orElse("");
        return joined.length() > 200 ? joined.substring(0, 200) : joined;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return StringUtils.hasText(value) ? value.strip() : fallback;
    }

    /** 调试/展示用：把 epoch millis 转成日期字符串 */
    public static String formatDate(Object epochMillis) {
        if (!(epochMillis instanceof Number number)) {
            return "";
        }
        return Instant.ofEpochMilli(number.longValue())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .toString();
    }

    /** 供装配层使用的过期判断（文档可能在召回后才被删除） */
    public static boolean isExpired(Object expireAt) {
        if (!(expireAt instanceof Number number)) {
            return false;
        }
        long value = number.longValue();
        return value > 0 && value < System.currentTimeMillis();
    }

    /** 供装配层计算“最近 N 天” */
    public static Instant cutoff(int days) {
        return Instant.now().minus(Math.max(1, days), ChronoUnit.DAYS);
    }
}

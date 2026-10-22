package com.blog.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 三层记忆与异步提炼配置，对应 {@code mha.memory.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mha.memory")
public class MemoryProperties {

    /** 长期记忆召回条数 */
    private int recallTopK = 5;

    /** 召回的最低余弦相似度（0~1） */
    private double recallMinScore = 0.55;

    /** 只召回最近多少天的记忆；稳定信息不受此限制 */
    private int recallWindowDays = 30;

    /** 工作记忆在 Redis 中的 TTL（天） */
    private int workingTtlDays = 7;

    /** 临时记忆的过期天数 */
    private int temporaryTtlDays = 7;

    /**
     * 删除会话后，normal 记忆的保留天数。
     *
     * <p>会话删除时不会立刻删除长期记忆，而是给 normal 记忆设置截止时间；
     * 期间被召回会顺延，important 记忆不参与。
     */
    private int retentionDays = 30;

    /** 是否启用到期记忆清理 */
    private boolean retentionEnabled = true;

    /** 到期记忆清理的扫描周期（毫秒），默认 1 小时 */
    private long retentionPollIntervalMs = 3_600_000L;

    /** 每次清理最多处理多少条记忆 */
    private int retentionBatchSize = 200;

    /** 单条记忆正文的最大字符数 */
    private int memoryMaxChars = 500;

    /** 装配提示词时画像摘要的最大字符数 */
    private int profileMaxChars = 500;

    /** 装配提示词时单条历史记忆的最大字符数 */
    private int memorySnippetMaxChars = 160;

    /** 装配提示词时当前任务区块的最大字符数 */
    private int taskMaxChars = 600;

    /** 一次提炼最多接受多少条记忆 */
    private int maxMemoriesPerTurn = 6;

    /**
     * 向量召回超时（毫秒）。
     *
     * <p>Milvus SDK 默认会重试 75 次、每次间隔最多 3 秒；Milvus 挂掉时一次检索
     * 可能阻塞数分钟。召回必须放在带超时的线程里，超时按“无记忆”处理。
     */
    private long vectorReadTimeoutMs = 5_000;

    /** 向量写入超时（毫秒）。写入在异步提炼线程里，可以比召回宽松一些。 */
    private long vectorWriteTimeoutMs = 15_000;

    /** 向量库调用失败后的熔断时间（秒），期间直接降级，避免每条消息都等超时 */
    private int vectorCooldownSeconds = 30;

    private Extract extract = new Extract();

    @Data
    public static class Extract {

        /** 关闭后不再扫描 outbox；已有数据不受影响 */
        private boolean enabled = true;

        /** 最后一条回复后等待多久再提炼，合并连续对话 */
        private int debounceSeconds = 90;

        /** 最大尝试次数，达到后置为失败（3） */
        private int maxAttempts = 3;

        /** 扫描周期（毫秒） */
        private long pollIntervalMs = 15_000;

        /** 每次扫描最多领取多少条任务 */
        private int batchSize = 5;

        /** 一次提炼最多读取多少条消息 */
        private int maxMessages = 40;

        /** 一次提炼最多读取多少字符，防止提示词过长 */
        private int maxChars = 12_000;

        /** RUNNING 超过多少分钟视为进程崩溃，重新入队 */
        private int staleMinutes = 10;

        /** 失败重试的基准秒数，按 30 * 2^(attempts-1) 退避 */
        private int retryBaseSeconds = 30;
    }
}

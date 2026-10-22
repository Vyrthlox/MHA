package com.blog.aispringboot.memory.extract;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code memory_extract_tasks} 的一行：一次待执行的记忆提炼。
 *
 * <p>本质是 MySQL outbox：接口线程只负责“登记”，后台线程负责真正调用模型。
 */
@Data
public class MemoryExtractTask {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_DONE = 2;
    public static final int STATUS_DEAD = 3;

    private Long id;

    private String sessionId;

    private Long userId;

    private Integer status;

    private LocalDateTime dueAt;

    private Integer attempts;

    private Integer lastExtractedCount;

    private LocalDateTime lockedAt;

    private String lastError;
}

package com.blog.aispringboot.memory.retention;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时清理到期且未被召回的 normal 记忆。
 *
 * <p>默认每小时扫描一次；对话主流程不会等待这个任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryRetentionScheduler {

    private final MemoryRetentionService retentionService;

    @Scheduled(fixedDelayString = "${mha.memory.retention-poll-interval-ms:3600000}")
    public void purge() {
        try {
            retentionService.purgeDue();
        } catch (Exception e) {
            log.error("到期记忆清理调度失败", e);
        }
    }
}

package com.blog.aispringboot.memory.extract;

import com.blog.aispringboot.config.MemoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 定时扫描记忆提炼 outbox。
 *
 * <p>只做“领取 + 投递”，真正的模型调用在 {@link MemoryExtractionService} 的
 * 异步线程里完成，避免定时任务线程被大模型请求长期占用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryExtractionScheduler {

    private final MemoryExtractTaskRepository taskRepository;
    private final MemoryExtractionService extractionService;
    private final MemoryProperties properties;

    /**
     * 对外入口：每条助手回复落库后调用，登记一次防抖任务。
     * 失败只记日志，不能影响聊天主流程。
     */
    public void schedule(String sessionId, Long userId) {
        if (!properties.getExtract().isEnabled()) {
            return;
        }
        try {
            taskRepository.schedule(sessionId, userId);
        } catch (Exception e) {
            log.warn("登记记忆提炼任务失败: sessionId={}, userId={}, error={}",
                    sessionId, userId, e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelayString = "${mha.memory.extract.poll-interval-ms:15000}")
    public void poll() {
        if (!properties.getExtract().isEnabled()) {
            return;
        }
        try {
            int reclaimed = taskRepository.resetStale();
            if (reclaimed > 0) {
                log.warn("回收了 {} 个卡在 RUNNING 的记忆提炼任务", reclaimed);
            }

            int batchSize = Math.max(1, properties.getExtract().getBatchSize());
            while (true) {
                List<MemoryExtractTask> claimed = taskRepository.claimDue(batchSize);
                if (claimed.isEmpty()) {
                    return;
                }
                for (MemoryExtractTask task : claimed) {
                    // 事务已在 claimDue 返回时提交，这里可以安全地异步执行
                    extractionService.extract(task);
                }
                if (claimed.size() < batchSize) {
                    return;
                }
            }
        } catch (Exception e) {
            log.error("记忆提炼调度失败", e);
        }
    }
}

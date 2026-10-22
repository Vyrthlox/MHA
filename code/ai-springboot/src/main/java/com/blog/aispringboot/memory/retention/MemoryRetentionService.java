package com.blog.aispringboot.memory.retention;

import com.blog.aispringboot.config.MemoryProperties;
import com.blog.aispringboot.memory.longterm.LongTermMemoryService;
import com.blog.aispringboot.memory.longterm.MemoryVectorLedger;
import com.blog.aispringboot.service.UserProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 长期记忆的保留期与到期清理。
 *
 * <p>删除会话时不再把长期记忆当成“永久数据”：
 * <ul>
 *   <li>该会话产生的 {@code normal} 记忆进入保留期（默认 30 天）；</li>
 *   <li>保留期内被召回会顺延截止时间；</li>
 *   <li>截止时间到了仍未召回，才真正从 Milvus 删除并标记台账。</li>
 * </ul>
 *
 * <p>{@code important} 记忆与 {@code user_profiles} 不参与，只有用户显式调用
 * {@code DELETE /psychological-chat/memory} 才会被删除。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryRetentionService {

    private final MemoryVectorLedger ledger;
    private final LongTermMemoryService longTermMemoryService;
    private final UserProfileService profileService;
    private final MemoryProperties properties;

    /**
     * 会话删除后，给该会话产生的 normal 记忆设置保留截止时间。
     *
     * @return 进入保留期的记忆条数
     */
    public int armForSession(String sessionId) {
        if (!properties.isRetentionEnabled() || !StringUtils.hasText(sessionId)) {
            return 0;
        }
        int days = Math.max(1, properties.getRetentionDays());
        int armed = ledger.armRetentionBySession(sessionId, days);
        log.info("会话删除后记忆进入保留期: sessionId={}, days={}, count={}", sessionId, days, armed);
        return armed;
    }

    /**
     * 删除已到期且仍未被召回的 normal 记忆。
     *
     * <p>Milvus 删除失败时保留台账（status 仍为 1），下一轮继续重试，
     * 不能出现“台账已删、向量还在”的假删除。
     *
     * @return 本轮实际清理的条数
     */
    public int purgeDue() {
        if (!properties.isRetentionEnabled()) {
            return 0;
        }
        int batchSize = Math.max(1, properties.getRetentionBatchSize());
        int total = 0;

        while (true) {
            List<MemoryVectorLedger.DueMemory> due = ledger.findDueRetention(batchSize);
            if (due.isEmpty()) {
                break;
            }
            List<String> ids = due.stream().map(MemoryVectorLedger.DueMemory::memoryId).toList();
            try {
                longTermMemoryService.deleteByIds(ids);
            } catch (RuntimeException e) {
                log.warn("到期记忆清理失败，等待下次重试: count={}, error={}",
                        ids.size(), e.getClass().getSimpleName());
                break;
            }

            ledger.markPurged(ids);
            due.stream()
                    .map(MemoryVectorLedger.DueMemory::userId)
                    .distinct()
                    .forEach(profileService::refreshMemoryCount);
            total += ids.size();

            if (due.size() < batchSize) {
                break;
            }
        }

        if (total > 0) {
            log.info("到期记忆清理完成: count={}", total);
        }
        return total;
    }
}

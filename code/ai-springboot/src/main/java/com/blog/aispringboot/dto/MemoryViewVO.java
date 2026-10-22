package com.blog.aispringboot.dto;

import com.blog.aispringboot.memory.working.WorkingMemory;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调试/演示用的记忆视图，只返回当前登录用户自己的数据。
 */
@Data
@AllArgsConstructor
public class MemoryViewVO {

    private Long userId;

    /** 用户画像摘要；没有提炼过时为 null */
    private String summary;

    /** 0~5 */
    private Integer riskLevel;

    /** 已写入 Milvus 的长期记忆条数 */
    private Integer memoryCount;

    private LocalDateTime lastExtractedAt;

    /** 未指定 sessionId 时返回最近更新的一条工作记忆 */
    private WorkingMemory workingMemory;
}

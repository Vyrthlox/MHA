package com.blog.aispringboot.memory.extract;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 一次记忆提炼的结构化结果，对应 {@code prompt/memory_extract.md} 要求模型输出的 JSON。
 *
 * <p>字段刻意保持扁平、简单：DeepSeek 等 OpenAI 兼容接口对复杂 JSON Schema 的遵循度
 * 不稳定，字段越少越容易解析成功。
 */
@Data
@NoArgsConstructor
public class MemoryDigest {

    /** 本轮对话整体类型：chat / treatment */
    private String type;

    /** 新增的长期记忆，单轮最多 6 条 */
    private List<MemoryItem> memories = new ArrayList<>();

    /** 当前会话的工作记忆（覆盖写） */
    private Working workingMemory;

    /** 用户画像增量（覆盖写，null 字段表示不修改） */
    private ProfilePatch profilePatch;

    @Data
    @NoArgsConstructor
    public static class MemoryItem {

        /** 自包含的一句陈述，不能是“他/她/这个”这类依赖上下文的指代 */
        private String content;

        /** background / preference / symptom / trigger / goal / relationship / event / other */
        private String category;

        /** stable：稳定信息；temporary：临时表达 */
        private String stability;

        /** 1~5 */
        private Integer importance;

        /**
         * important：身份、健康、风险、核心关系等需要长期保留的信息；
         * normal：一般偏好、一次性事件。服务端会按 importance/category 兜底纠正。
         */
        private String sensitivity;

        private List<String> keywords = new ArrayList<>();

        /** chat / treatment；为空时沿用会话类型 */
        private String type;
    }

    @Data
    @NoArgsConstructor
    public static class Working {

        private String activeTask;

        private List<String> todos = new ArrayList<>();

        private List<String> openQuestions = new ArrayList<>();

        /** 0~5 */
        private Integer riskLevel;

        /** chat / treatment */
        private String type;
    }

    @Data
    @NoArgsConstructor
    public static class ProfilePatch {

        /** 更新后的完整画像摘要（不是增量） */
        private String summary;

        /** 性格、沟通偏好等 */
        private Map<String, Object> traits;

        /** 心理状况、压力源等 */
        private Map<String, Object> mentalStatus;

        /** 0~5，服务端只会取历史最大值 */
        private Integer riskLevel;
    }
}

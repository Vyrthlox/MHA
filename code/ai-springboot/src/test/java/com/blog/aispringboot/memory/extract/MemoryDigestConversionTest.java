package com.blog.aispringboot.memory.extract;

import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 Spring AI 2.0.1 的 BeanOutputConverter（Jackson 3）能解析 DeepSeek 常见的
 * “带 Markdown 代码围栏的 JSON”。
 */
class MemoryDigestConversionTest {

    @Test
    void convertsJsonWrappedInMarkdownFence() {
        BeanOutputConverter<MemoryDigest> converter = new BeanOutputConverter<>(MemoryDigest.class);
        String raw = """
                ```json
                {
                  "type": "treatment",
                  "memories": [
                    {
                      "content": "用户是独居研究生，最近两周因论文进度失眠。",
                      "category": "background",
                      "stability": "stable",
                      "importance": 4,
                      "keywords": ["独居", "失眠"],
                      "type": "treatment"
                    }
                  ],
                  "workingMemory": {
                    "activeTask": "梳理论文压力",
                    "todos": ["记录睡眠"],
                    "openQuestions": ["白天是否疲惫"],
                    "riskLevel": 2,
                    "type": "treatment"
                  },
                  "profilePatch": {
                    "summary": "独居研究生，近期论文压力大并失眠。",
                    "traits": {"沟通偏好": "具体建议"},
                    "mentalStatus": {"睡眠": "近两周失眠"},
                    "riskLevel": 2
                  }
                }
                ```
                """;

        MemoryDigest digest = converter.convert(raw);

        assertThat(digest).isNotNull();
        assertThat(digest.getType()).isEqualTo("treatment");
        assertThat(digest.getMemories()).hasSize(1);
        assertThat(digest.getMemories().get(0).getStability()).isEqualTo("stable");
        assertThat(digest.getWorkingMemory().getTodos()).containsExactly("记录睡眠");
        assertThat(digest.getProfilePatch().getTraits()).containsEntry("沟通偏好", "具体建议");
    }
}

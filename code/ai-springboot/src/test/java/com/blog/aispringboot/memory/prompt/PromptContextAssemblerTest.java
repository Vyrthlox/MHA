package com.blog.aispringboot.memory.prompt;

import com.blog.aispringboot.config.MemoryProperties;
import com.blog.aispringboot.entity.UserProfile;
import com.blog.aispringboot.memory.longterm.LongTermMemoryService;
import com.blog.aispringboot.memory.working.WorkingMemory;
import com.blog.aispringboot.memory.working.WorkingMemoryStore;
import com.blog.aispringboot.service.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PromptContextAssemblerTest {

    private final UserProfileService profileService = mock(UserProfileService.class);
    private final WorkingMemoryStore workingMemoryStore = mock(WorkingMemoryStore.class);
    private final LongTermMemoryService longTermMemoryService = mock(LongTermMemoryService.class);

    @Test
    void assemblesProfileMemoryAndWorkingTaskWithUntrustedDelimiters() {
        MemoryProperties properties = new MemoryProperties();
        PromptContextAssembler assembler = new PromptContextAssembler(
                profileService, workingMemoryStore, longTermMemoryService, properties);

        UserProfile profile = new UserProfile();
        profile.setSummary("独居研究生，近期因论文压力失眠。");
        when(profileService.find(1L)).thenReturn(Optional.of(profile));

        WorkingMemory working = new WorkingMemory();
        working.setActiveTask("梳理论文压力");
        working.setTodos(List.of("记录一周睡眠"));
        working.setOpenQuestions(List.of("白天是否疲惫"));
        working.setRiskLevel(2);
        working.setType("treatment");
        when(workingMemoryStore.get(1L, "s1")).thenReturn(Optional.of(working));

        Document memory = new Document("m1", "用户最近两周持续失眠，白天精力下降。",
                Map.of("createdAt", System.currentTimeMillis(), "type", "treatment", "importance", 4));
        when(longTermMemoryService.recall(1L, "treatment", "我最近睡不好"))
                .thenReturn(List.of(memory));

        String context = assembler.assemble(1L, "s1", "我最近睡不好");

        assertThat(context).contains("[用户画像]")
                .contains("独居研究生")
                .contains("[相关历史记忆]")
                .contains("<<<MEMORY")
                .contains("持续失眠")
                .contains("[当前任务状态]")
                .contains("梳理论文压力");
    }

    @Test
    void returnsEmptyWhenNothingIsKnown() {
        PromptContextAssembler assembler = new PromptContextAssembler(
                profileService, workingMemoryStore, longTermMemoryService, new MemoryProperties());
        when(profileService.find(2L)).thenReturn(Optional.empty());
        when(workingMemoryStore.get(2L, "s2")).thenReturn(Optional.empty());
        when(longTermMemoryService.recall(2L, null, "你好")).thenReturn(List.of());

        assertThat(assembler.assemble(2L, "s2", "你好")).isEmpty();
    }
}

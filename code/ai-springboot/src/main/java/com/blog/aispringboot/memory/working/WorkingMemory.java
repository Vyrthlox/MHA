package com.blog.aispringboot.memory.working;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 工作记忆：当前会话正在追踪的任务、待办和未解决问题。
 *
 * <p>以 JSON 存在 Redis，key 为 {@code mha:wm:{userId}:{sessionId}}。
 * 它只服务于“当前这个用户 + 当前这个会话”，跨会话的用户信息放在长期记忆里。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkingMemory {

    public static final String TYPE_CHAT = "chat";
    public static final String TYPE_TREATMENT = "treatment";

    private String sessionId;

    private Long userId;

    /** chat / treatment */
    private String type;

    /** 正在进行的一件主要事情，例如“缓解考前焦虑” */
    private String activeTask;

    private List<String> todos = new ArrayList<>();

    private List<String> openQuestions = new ArrayList<>();

    /** 0~5，越高越需要关注 */
    private Integer riskLevel = 0;

    /** epoch millis */
    private long updatedAt;

    public boolean isEmpty() {
        return (activeTask == null || activeTask.isBlank())
                && (todos == null || todos.isEmpty())
                && (openQuestions == null || openQuestions.isEmpty());
    }
}

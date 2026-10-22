package com.blog.aispringboot.document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 对话消息（MongoDB {@code chat_messages} 集合）。
 *
 * <p>这里的记录是<b>完整历史</b>，不受「记忆窗口」限制：窗口只决定每次请求
 * 带给模型多少条，前端翻看会话时仍能看到全部消息。
 *
 * <p>复合索引按 {@code sessionId + createdAt} 建立：查询单会话消息、以及
 * 取最近 N 条记忆都走这个索引。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "chat_messages")
@CompoundIndex(name = "idx_chat_message_session_created", def = "{'sessionId': 1, 'createdAt': 1}")
public class ChatMessageDocument {

    /** 与前端约定的角色值 */
    public static final String ROLE_USER = "user";
    public static final String ROLE_ASSISTANT = "assistant";
    public static final String ROLE_SYSTEM = "system";

    @Id
    private String id;

    private String sessionId;

    /** user / assistant / system */
    private String role;

    private String content;

    private LocalDateTime createdAt;
}

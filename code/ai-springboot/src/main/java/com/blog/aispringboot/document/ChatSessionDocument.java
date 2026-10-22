package com.blog.aispringboot.document;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * 对话会话（MongoDB {@code chat_sessions} 集合）。
 *
 * <p>一条记录代表左侧会话列表里的一项。消息正文不放在这里，
 * 而是按 {@code sessionId} 拆到 {@link ChatMessageDocument}，避免文档无限增长。
 *
 * <p>{@code username} 是创建时的快照：管理端「咨询记录」要显示用户昵称，
 * 若每次列表都去 MySQL 关联 {@code sys_user_auths} 会产生 N+1 查询。
 * 用户名目前不可修改，快照不会失真。
 */
@Data
@NoArgsConstructor
@Document(collection = "chat_sessions")
@CompoundIndex(name = "idx_chat_session_user_updated", def = "{'userId': 1, 'updatedAt': -1}")
public class ChatSessionDocument {

    /** 新建会话的默认标题，与前端 home.vue 中的文案保持一致 */
    public static final String DEFAULT_TITLE = "新的对话";

    @Id
    private String id;

    /** 归属用户，来自 JWT 的 {@code sub}。所有访问都必须校验它。 */
    private Long userId;

    /** 创建时的用户名快照，供管理端展示 */
    private String username;

    private String title;

    /** 最后一条消息的摘要，用于会话列表的预览行 */
    private String lastMessageContent;

    /** 消息条数缓存，避免列表页对每条会话都 count 一次 */
    private Integer messageCount;

    private LocalDateTime startedAt;

    private LocalDateTime updatedAt;
}

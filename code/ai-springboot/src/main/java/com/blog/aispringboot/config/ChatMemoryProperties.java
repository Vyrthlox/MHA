package com.blog.aispringboot.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 对话记忆配置，对应 {@code application.properties} 中的 {@code mha.chat-memory.*}。
 */
@Data
@Component
@ConfigurationProperties(prefix = "mha.chat-memory")
public class ChatMemoryProperties {

    /**
     * 每次请求带给模型的最大历史消息条数（含本轮用户消息之前的历史）。
     *
     * <p>这里数的是「消息」不是「轮次」：20 条约等于 10 个来回。
     * 完整历史不会丢，始终保存在 MongoDB 里；窗口只影响模型上下文。
     */
    private int maxMessages = 20;

    /** 会话标题取首条用户消息的前多少个字符 */
    private int titleMaxLength = 20;

    /** 会话列表预览取最后一条消息的前多少个字符 */
    private int previewMaxLength = 60;
}

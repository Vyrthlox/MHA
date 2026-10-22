package com.blog.aispringboot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 对话与记忆提炼两个 {@link ChatClient}。
 *
 * <ul>
 *   <li>{@code chatClient}：面向用户，挂系统人设 + 对话记忆 advisor；</li>
 *   <li>{@code digestChatClient}：只给记忆提炼用，不挂记忆 advisor——否则提炼的
 *       “请输出 JSON”会被写进用户的对话历史，污染后续上下文。</li>
 * </ul>
 *
 * <p>业务层发送消息时会用 {@code .system(静态人设 + 动态画像/记忆/任务)} 覆盖
 * 这里的 {@code defaultSystem}（Spring AI 的 {@code .system()} 优先于默认值）。
 */
@Configuration
public class ChatClientConfig {

    private final SystemPromptProvider promptProvider;

    public ChatClientConfig(SystemPromptProvider promptProvider) {
        this.promptProvider = promptProvider;
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder, ChatMemory chatMemory) {
        return builder.clone()
                .defaultSystem(promptProvider.chatSystemPrompt())
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .build();
    }

    @Bean("digestChatClient")
    public ChatClient digestChatClient(ChatClient.Builder builder) {
        return builder.clone().build();
    }
}

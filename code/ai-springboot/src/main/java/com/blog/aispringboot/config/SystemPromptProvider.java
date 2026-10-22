package com.blog.aispringboot.config;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * 系统提示词加载器。
 *
 * <p>聊天人设和记忆提炼提示词都放在工程根目录 {@code prompt/} 下，由 Maven 资源拷贝
 * 打进 classpath。这里在启动时读一次，避免每轮对话都做 IO。
 */
@Component
public class SystemPromptProvider {

    private static final String CHAT_PROMPT = "prompt/sys_chat.md";
    private static final String EXTRACT_PROMPT = "prompt/memory_extract.md";

    private final String chatSystemPrompt;
    private final String extractionSystemPrompt;

    public SystemPromptProvider() {
        this.chatSystemPrompt = read(CHAT_PROMPT);
        this.extractionSystemPrompt = read(EXTRACT_PROMPT);
    }

    public String chatSystemPrompt() {
        return chatSystemPrompt;
    }

    public String extractionSystemPrompt() {
        return extractionSystemPrompt;
    }

    /**
     * 优先 classpath（打包后），读不到时退回工程目录（IDE 未跑资源拷贝的场景）。
     */
    private static String read(String location) {
        Resource classpath = new ClassPathResource(location);
        Resource resource = classpath.exists() ? classpath : new FileSystemResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("提示词不存在: " + location + "（应在工程根目录的 prompt/ 下）");
        }
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("读取提示词失败: " + location, e);
        }
    }
}

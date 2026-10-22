package com.blog.aispringboot.memory;

import com.blog.aispringboot.config.ChatMemoryProperties;
import com.blog.aispringboot.document.ChatMessageDocument;
import com.blog.aispringboot.document.ChatSessionDocument;
import com.blog.aispringboot.repository.ChatMessageRepository;
import com.blog.aispringboot.repository.ChatSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * 基于 MongoDB 的对话记忆。
 *
 * <p><b>为什么直接实现 {@link ChatMemory}，而不是 {@code ChatMemoryRepository}：</b>
 * Spring AI 自带的 {@code MessageWindowChatMemory} 每次调用都会把「裁剪后的整个窗口」
 * 交给仓储的 {@code saveAll}，仓储无法区分哪些是新消息。若直接照做，要么整段覆盖、
 * 要么产生重复。本项目的 MongoDB 是<b>只追加的完整日志</b>（前端要回看全部历史），
 * 所以这里按「收到什么就追加什么」实现 {@link #add}，窗口只在 {@link #get} 读取时裁剪。
 *
 * <p>与 {@code MessageChatMemoryAdvisor} 的配合方式（已核对 Spring AI 2.0.1 字节码）：
 * 请求前 {@code before()} 调一次 {@code add} 存用户消息，响应后 {@code after()} 再调一次
 * 存助手回复，因此一条消息只会入账一次，不会重复。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MongoChatMemory implements ChatMemory {

    private final ChatMessageRepository messageRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMemoryProperties properties;

    /**
     * 追加消息。适配器传入的 {@code List<Message>} 只包含本轮新增的消息。
     */
    @Override
    public void add(String conversationId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || messages == null || messages.isEmpty()) {
            return;
        }

        ChatSessionDocument session = sessionRepository.findById(conversationId).orElse(null);
        if (session == null) {
            // 会话可能刚被用户删除。记忆落库失败不应把已经生成的 AI 回复变成 500，
            // 这里只记日志并跳过（下一次对话会自然从空记忆开始）。
            log.warn("会话不存在，跳过记忆写入: sessionId={}", conversationId);
            return;
        }

        long existingCount = messageRepository.countBySessionId(conversationId);
        LocalDateTime now = LocalDateTime.now();
        List<ChatMessageDocument> docs = new ArrayList<>(messages.size());
        String firstUserText = null;

        for (Message message : messages) {
            String role = toRole(message.getMessageType());
            String text = message.getText();
            if (role == null || !StringUtils.hasText(text)) {
                continue;
            }
            if (firstUserText == null && ChatMessageDocument.ROLE_USER.equals(role)) {
                firstUserText = text;
            }
            docs.add(new ChatMessageDocument(
                    ObjectId.get().toHexString(),
                    conversationId,
                    role,
                    text,
                    now));
        }

        if (docs.isEmpty()) {
            return;
        }
        messageRepository.saveAll(docs);

        ChatMessageDocument last = docs.get(docs.size() - 1);
        session.setLastMessageContent(truncate(last.getContent(), properties.getPreviewMaxLength()));
        session.setMessageCount(toIntCount(existingCount + docs.size()));
        // 只在这条会话还叫「新的对话」且这是第一条用户消息时生成标题，
        // 后续追问不会反复改标题。
        if (existingCount == 0 && firstUserText != null
                && ChatSessionDocument.DEFAULT_TITLE.equals(session.getTitle())) {
            session.setTitle(truncate(firstUserText, properties.getTitleMaxLength()));
        }
        session.setUpdatedAt(now);
        sessionRepository.save(session);

        log.debug("对话记忆已写入: sessionId={}, 新增 {} 条", conversationId, docs.size());
    }

    /**
     * 读取记忆窗口：最近 {@code maxMessages} 条，按时间正序返回。
     */
    @Override
    public List<Message> get(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        int limit = Math.max(1, properties.getMaxMessages());
        List<ChatMessageDocument> recent = messageRepository
                .findBySessionIdOrderByCreatedAtDescIdDesc(conversationId, PageRequest.of(0, limit));
        if (recent.isEmpty()) {
            return List.of();
        }
        Collections.reverse(recent);
        return recent.stream()
                .map(this::toMessage)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 清空某个会话的记忆。
     *
     * <p>新会话由 id 区分、不会复用，因此正常情况下只有删除会话时会走到这里。
     */
    @Override
    public void clear(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        messageRepository.deleteBySessionId(conversationId);
        sessionRepository.findById(conversationId).ifPresent(session -> {
            session.setMessageCount(0);
            session.setLastMessageContent("");
            session.setUpdatedAt(LocalDateTime.now());
            sessionRepository.save(session);
        });
    }

    private String toRole(MessageType type) {
        if (type == null) {
            return null;
        }
        // MessageType.getValue() 返回 "USER" / "ASSISTANT" / "SYSTEM" / "TOOL"
        return switch (type.getValue().toLowerCase(Locale.ROOT)) {
            case "user" -> ChatMessageDocument.ROLE_USER;
            case "assistant" -> ChatMessageDocument.ROLE_ASSISTANT;
            case "system" -> ChatMessageDocument.ROLE_SYSTEM;
            // 工具消息不进入前端可见的对话历史
            default -> null;
        };
    }

    private Message toMessage(ChatMessageDocument doc) {
        if (doc.getRole() == null) {
            return null;
        }
        String content = doc.getContent() == null ? "" : doc.getContent();
        return switch (doc.getRole()) {
            case ChatMessageDocument.ROLE_USER -> new UserMessage(content);
            case ChatMessageDocument.ROLE_ASSISTANT -> new AssistantMessage(content);
            case ChatMessageDocument.ROLE_SYSTEM -> new SystemMessage(content);
            default -> null;
        };
    }

    /** 按码点裁剪，避免把 emoji / 生僻字切成半个字符 */
    private static String truncate(String text, int maxCodePoints) {
        if (text == null) {
            return "";
        }
        String normalized = text.strip().replaceAll("\\s+", " ");
        int limit = Math.max(1, maxCodePoints);
        if (normalized.codePointCount(0, normalized.length()) <= limit) {
            return normalized;
        }
        int end = normalized.offsetByCodePoints(0, limit);
        return normalized.substring(0, end) + "…";
    }

    private static int toIntCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) count;
    }
}

package com.blog.aispringboot.service;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.common.PageResult;
import com.blog.aispringboot.document.ChatMessageDocument;
import com.blog.aispringboot.document.ChatSessionDocument;
import com.blog.aispringboot.dto.ChatMessageVO;
import com.blog.aispringboot.dto.ChatReplyVO;
import com.blog.aispringboot.dto.ChatSessionVO;
import com.blog.aispringboot.config.SystemPromptProvider;
import com.blog.aispringboot.memory.extract.MemoryExtractTaskRepository;
import com.blog.aispringboot.memory.extract.MemoryExtractionScheduler;
import com.blog.aispringboot.memory.prompt.PromptContextAssembler;
import com.blog.aispringboot.memory.retention.MemoryRetentionService;
import com.blog.aispringboot.memory.working.WorkingMemoryStore;
import com.blog.aispringboot.repository.ChatMessageRepository;
import com.blog.aispringboot.repository.ChatSessionRepository;
import com.blog.aispringboot.security.LoginUser;
import com.blog.aispringboot.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.types.ObjectId;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * 对话会话、消息历史与大模型回复。
 *
 * <p>记忆的读写由 Spring AI 的 {@code ChatMemory}（本项目的 {@code MongoChatMemory}）
 * 经 {@code MessageChatMemoryAdvisor} 自动完成：请求前读窗口、存用户消息，
 * 响应后存助手回复。本类只负责会话归属校验、调用模型、把持久化结果返回给前端。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PsychologicalChatService {

    /** sys_users.status：1=管理员 */
    private static final int STATUS_ADMIN = 1;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 100;

    /** 与 {@code SendMessageRequest} 的上限保持一致（controller 已校验，这里兜底） */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    /**
     * 同一会话的发送请求串行化。并发时若两个请求读到相同的历史，
     * 模型会看到重复或错序的上下文；分段锁也能避免为每个会话累积锁对象。
     */
    private static final int LOCK_STRIPES = 64;

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatClient chatClient;
    private final SystemPromptProvider promptProvider;
    private final PromptContextAssembler promptContextAssembler;
    private final WorkingMemoryStore workingMemoryStore;
    private final MemoryExtractionScheduler memoryExtractionScheduler;
    private final MemoryExtractTaskRepository memoryExtractTaskRepository;
    private final MemoryRetentionService memoryRetentionService;
    private final Object[] sessionLocks = createLocks();

    /**
     * 新建会话。标题先用默认值，等第一条用户消息写入记忆时再自动生成。
     */
    public ChatSessionVO createSession() {
        LoginUser user = currentUser();
        LocalDateTime now = LocalDateTime.now();

        ChatSessionDocument session = new ChatSessionDocument();
        // 显式生成 id：会话 id 同时是 ChatMemory 的 conversationId，
        // 让 Spring Data 隐式生成会让「创建后立刻可用的 id」变得不确定。
        session.setId(ObjectId.get().toHexString());
        session.setUserId(user.userId());
        session.setUsername(user.username());
        session.setTitle(ChatSessionDocument.DEFAULT_TITLE);
        session.setLastMessageContent("");
        session.setMessageCount(0);
        session.setStartedAt(now);
        session.setUpdatedAt(now);

        sessionRepository.save(session);
        log.info("创建对话会话: id={}, userId={}", session.getId(), user.userId());
        return toSessionVO(session);
    }

    /**
     * 会话列表。
     *
     * <p>默认：普通用户只能看到自己的会话；管理员（前端「咨询记录」页）看到全部。
     * 对话页传 {@code mine=true} 强制只返回本人会话——管理员进入对话页时也不应该
     * 看到其他用户的聊天记录。
     */
    public PageResult<ChatSessionVO> listSessions(int page, int size, boolean mine) {
        LoginUser user = currentUser();
        int current = Math.max(DEFAULT_PAGE, page);
        int pageSize = Math.min(Math.max(1, size <= 0 ? DEFAULT_PAGE_SIZE : size), MAX_PAGE_SIZE);

        // 按最近活跃排序，新消息会把它顶到最上面
        PageRequest pageable = PageRequest.of(current - 1, pageSize,
                Sort.by(Sort.Direction.DESC, "updatedAt"));

        Page<ChatSessionDocument> result = !mine && isAdmin(user)
                ? sessionRepository.findAll(pageable)
                : sessionRepository.findByUserId(user.userId(), pageable);

        return PageResult.of(result.map(this::toSessionVO));
    }

    /** 某个会话的完整消息历史（不受记忆窗口限制） */
    public List<ChatMessageVO> listMessages(String sessionId) {
        LoginUser user = currentUser();
        requireAccessibleSession(sessionId, user);
        return messageRepository.findBySessionIdOrderByCreatedAtAscIdAsc(sessionId).stream()
                .map(this::toMessageVO)
                .toList();
    }

    /**
     * 发送一条用户消息并返回助手回复。
     *
     * <p>调用链路：{@code ChatClient} 读取系统提示词 → advisor 从 MongoDB 取最近
     * {@code mha.chat-memory.max-messages} 条历史 → 调模型 → advisor 把用户消息和
     * 助手回复追加进 MongoDB → 这里取回持久化后的消息返回前端。
     */
    public ChatReplyVO sendMessage(String sessionId, String content) {
        LoginUser user = currentUser();
        ChatSessionDocument session = requireAccessibleSession(sessionId, user);
        // 发送消息只允许会话本人。管理员可以查看/删除会话（咨询记录页），
        // 但不能以其他用户的身份继续对话。
        if (!Objects.equals(session.getUserId(), user.userId())) {
            throw new BusinessException("会话不存在");
        }

        String text = content == null ? "" : content.strip();
        if (text.isEmpty()) {
            throw new BusinessException("消息内容不能为空");
        }
        if (text.codePointCount(0, text.length()) > MAX_MESSAGE_LENGTH) {
            throw new BusinessException("单条消息不能超过 " + MAX_MESSAGE_LENGTH + " 个字符");
        }

        long startedAt = System.currentTimeMillis();
        String replyText;
        synchronized (lockFor(sessionId)) {
            // 动态上下文（画像 / 长期记忆 / 工作记忆）失败时降级为纯人设，
            // 不能因为 Redis、Milvus 或 Ollama 抖动就让用户发不出消息。
            String dynamicContext = "";
            try {
                dynamicContext = promptContextAssembler.assemble(user.userId(), sessionId, text);
            } catch (Exception e) {
                log.warn("装配动态上下文失败，本轮降级为纯系统人设: sessionId={}, userId={}, error={}",
                        sessionId, user.userId(), e.getClass().getSimpleName());
            }
            String systemPrompt = dynamicContext == null || dynamicContext.isBlank()
                    ? promptProvider.chatSystemPrompt()
                    : promptProvider.chatSystemPrompt() + "\n\n" + dynamicContext;

            try {
                replyText = chatClient.prompt()
                        .system(systemPrompt)
                        .user(text)
                        // 这个参数决定 advisor 读写哪条会话的记忆
                        .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, sessionId))
                        .call()
                        .content();
            } catch (Exception e) {
                log.error("调用大模型失败: sessionId={}, userId={}", sessionId, user.userId(), e);
                throw new BusinessException("AI 服务暂时不可用，请稍后重试");
            }
        }

        if (!StringUtils.hasText(replyText)) {
            throw new BusinessException("AI 未返回有效内容，请重试");
        }

        // advisor 已把回复追加进 chat_messages，这里取回数据库里的 id 与时间，
        // 保证返回给前端的那条消息和下次打开历史时看到的是同一条。
        ChatMessageDocument saved = messageRepository
                .findFirstBySessionIdOrderByCreatedAtDescIdDesc(sessionId)
                .filter(m -> ChatMessageDocument.ROLE_ASSISTANT.equals(m.getRole())
                        && replyText.equals(m.getContent()))
                .orElse(null);
        ChatSessionDocument latest = sessionRepository.findById(sessionId).orElse(null);
        if (saved == null) {
            // 极端情况：生成过程中会话被删除，记忆层跳过了写入。
            // 回复仍返回给前端，但不会出现在历史里。
            log.warn("AI 回复未写入消息历史: sessionId={}", sessionId);
        }

        log.info("AI 回复完成: sessionId={}, 耗时={}ms, 回复长度={}",
                sessionId, System.currentTimeMillis() - startedAt,
                replyText.codePointCount(0, replyText.length()));

        // 写 outbox 后立即返回，真正的提炼在 90 秒防抖后由后台线程执行。
        // schedule() 内部吞掉异常，不会影响本次回复。
        memoryExtractionScheduler.schedule(sessionId, user.userId());

        return new ChatReplyVO(
                sessionId,
                saved != null ? saved.getId() : null,
                ChatMessageDocument.ROLE_ASSISTANT,
                replyText,
                saved != null && saved.getCreatedAt() != null ? saved.getCreatedAt() : LocalDateTime.now(),
                latest != null && latest.getMessageCount() != null ? latest.getMessageCount() : 0);
    }

    /**
     * 删除会话及其全部消息。
     *
     * <p>同时清理该会话的工作记忆和待提炼任务。已经写入的长期记忆<b>不立即删除</b>：
     * 该会话产生的 normal 记忆进入保留期，30 天内未被召回才由后台清理；
     * important 记忆与用户画像不受影响。用户要立即清空全部记忆时走
     * {@code DELETE /psychological-chat/memory}。
     *
     * <p>这里刻意不做同步提炼：用户已经要求删除，再把内容交给模型总结既慢又违背意图。
     */
    public void deleteSession(String sessionId) {
        LoginUser user = currentUser();
        ChatSessionDocument session = requireAccessibleSession(sessionId, user);
        messageRepository.deleteBySessionId(sessionId);
        sessionRepository.delete(session);
        workingMemoryStore.delete(session.getUserId(), sessionId);
        memoryExtractTaskRepository.cancel(sessionId);

        // 保留期登记失败不影响会话删除：记忆继续保留，只是不会自动清理，
        // 不能因此让用户看到删除失败。
        try {
            memoryRetentionService.armForSession(sessionId);
        } catch (Exception e) {
            log.warn("登记长期记忆保留期失败: sessionId={}, error={}",
                    sessionId, e.getClass().getSimpleName());
        }

        log.info("删除对话会话: id={}, userId={}, operatorId={}",
                sessionId, session.getUserId(), user.userId());
    }

    /**
     * 取出会话并校验归属。
     *
     * <p>管理员可以读取任意会话（咨询记录），普通用户只能操作自己的。
     * 返回「无权访问」而不是「不存在」在安全上更严谨；这里两者都用同一句提示，
     * 避免通过 id 探测别人的会话是否存在。
     */
    private ChatSessionDocument requireAccessibleSession(String sessionId, LoginUser user) {
        ChatSessionDocument session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException("会话不存在"));
        if (!isAdmin(user) && !Objects.equals(session.getUserId(), user.userId())) {
            throw new BusinessException("会话不存在");
        }
        return session;
    }

    private Object lockFor(String sessionId) {
        return sessionLocks[Math.floorMod(Objects.hashCode(sessionId), sessionLocks.length)];
    }

    private static Object[] createLocks() {
        Object[] locks = new Object[LOCK_STRIPES];
        for (int i = 0; i < locks.length; i++) {
            locks[i] = new Object();
        }
        return locks;
    }

    private boolean isAdmin(LoginUser user) {
        return user.status() != null && user.status() == STATUS_ADMIN;
    }

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.userId() == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return user;
    }

    private ChatSessionVO toSessionVO(ChatSessionDocument session) {
        return new ChatSessionVO(
                session.getId(),
                session.getTitle(),
                session.getLastMessageContent(),
                session.getTitle(),
                session.getLastMessageContent(),
                session.getMessageCount() == null ? 0 : session.getMessageCount(),
                session.getUsername(),
                session.getStartedAt(),
                session.getUpdatedAt());
    }

    private ChatMessageVO toMessageVO(ChatMessageDocument message) {
        return new ChatMessageVO(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getCreatedAt());
    }
}

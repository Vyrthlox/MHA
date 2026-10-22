package com.blog.aispringboot.memory.extract;

import com.blog.aispringboot.config.AsyncConfig;
import com.blog.aispringboot.config.MemoryProperties;
import com.blog.aispringboot.config.SystemPromptProvider;
import com.blog.aispringboot.document.ChatMessageDocument;
import com.blog.aispringboot.document.ChatSessionDocument;
import com.blog.aispringboot.memory.longterm.LongTermMemoryService;
import com.blog.aispringboot.memory.working.WorkingMemory;
import com.blog.aispringboot.memory.working.WorkingMemoryStore;
import com.blog.aispringboot.repository.ChatMessageRepository;
import com.blog.aispringboot.repository.ChatSessionRepository;
import com.blog.aispringboot.service.UserProfileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 一次记忆提炼的完整流程：读新增消息 → 调独立 ChatClient 产出 JSON →
 * 写 Milvus / 画像 / 工作记忆 → 完成任务。
 *
 * <p>注意：这个方法运行在 {@link AsyncConfig#MEMORY_EXECUTOR} 线程池里，
 * 不持有数据库长事务。所有失败都只影响本次记忆提炼，不影响用户已经拿到的回复。
 */
@Slf4j
@Service
public class MemoryExtractionService {

    /**
     * 这些类别默认按重要信息处理，即使模型误标成 normal 也不会被自动清理。
     * 身份背景、症状/诱因、核心关系、目标都属于心理支持场景里需要长期保留的信息。
     */
    private static final Set<String> IMPORTANT_CATEGORIES = Set.of(
            "background", "symptom", "trigger", "goal", "relationship");

    private final MemoryExtractTaskRepository taskRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final MongoTemplate mongoTemplate;
    private final ChatClient digestChatClient;
    private final SystemPromptProvider promptProvider;
    private final UserProfileService profileService;
    private final WorkingMemoryStore workingMemoryStore;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryProperties properties;

    public MemoryExtractionService(MemoryExtractTaskRepository taskRepository,
                                   ChatSessionRepository sessionRepository,
                                   ChatMessageRepository messageRepository,
                                   MongoTemplate mongoTemplate,
                                   @Qualifier("digestChatClient") ChatClient digestChatClient,
                                   SystemPromptProvider promptProvider,
                                   UserProfileService profileService,
                                   WorkingMemoryStore workingMemoryStore,
                                   LongTermMemoryService longTermMemoryService,
                                   MemoryProperties properties) {
        this.taskRepository = taskRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.mongoTemplate = mongoTemplate;
        this.digestChatClient = digestChatClient;
        this.promptProvider = promptProvider;
        this.profileService = profileService;
        this.workingMemoryStore = workingMemoryStore;
        this.longTermMemoryService = longTermMemoryService;
        this.properties = properties;
    }

    @Async(AsyncConfig.MEMORY_EXECUTOR)
    public void extract(MemoryExtractTask task) {
        if (task == null || task.getId() == null || task.getUserId() == null) {
            return;
        }
        Long taskId = task.getId();
        int attempts = task.getAttempts() == null ? 1 : task.getAttempts();
        String runId = taskId + "-" + attempts;

        try {
            Optional<ChatSessionDocument> sessionOpt = sessionRepository.findById(task.getSessionId());
            if (sessionOpt.isEmpty()
                    || !Objects.equals(sessionOpt.get().getUserId(), task.getUserId())) {
                // 会话已删除或归属变化：不再提炼，直接结束任务
                taskRepository.markDone(taskId, attempts, safeCount(task.getLastExtractedCount()));
                return;
            }

            long total = messageRepository.countBySessionId(task.getSessionId());
            int lastCount = safeCount(task.getLastExtractedCount());
            if (total <= lastCount) {
                taskRepository.markDone(taskId, attempts, total);
                return;
            }

            List<ChatMessageDocument> messages = loadMessages(task.getSessionId(), total);
            if (messages.isEmpty()) {
                taskRepository.markDone(taskId, attempts, total);
                return;
            }

            String sessionType = resolveType(task.getUserId(), task.getSessionId(), null);
            String input = buildInput(task.getUserId(), task.getSessionId(), messages);
            MemoryDigest digest = callModel(input);
            if (digest == null) {
                throw new IllegalStateException("模型未返回可解析的记忆 JSON");
            }

            List<MemoryDigest.MemoryItem> items = cleanMemories(digest.getMemories());
            String digestType = resolveType(task.getUserId(), task.getSessionId(), digest.getType());

            // 模型调用期间会话可能已被删除：不要再写入新的长期记忆，
            // 否则这些记忆没有机会进入删除会话后的保留期。
            if (!sessionRepository.existsById(task.getSessionId())) {
                taskRepository.markDone(taskId, attempts, safeCount(task.getLastExtractedCount()));
                log.info("提炼期间会话已删除，放弃写入: taskId={}, sessionId={}", taskId, task.getSessionId());
                return;
            }

            // 重试前先清掉上次中断留下的半成品（按 memory_id 台账）
            longTermMemoryService.cleanupPending(task.getSessionId());
            longTermMemoryService.rememberAll(task.getUserId(), task.getSessionId(),
                    digestType, runId, items);

            profileService.apply(task.getUserId(), digest.getProfilePatch());
            profileService.refreshMemoryCount(task.getUserId());
            applyWorkingMemory(task, digest, digestType);

            boolean updated = taskRepository.markDone(taskId, attempts, total);
            if (!updated) {
                log.warn("记忆提炼任务已被更新，跳过本次完成标记: taskId={}, attempts={}", taskId, attempts);
                return;
            }

            // 提炼期间用户又发了消息：再排一次（仍然走 90 秒防抖）
            long after = messageRepository.countBySessionId(task.getSessionId());
            if (after > total) {
                taskRepository.schedule(task.getSessionId(), task.getUserId());
            }

            log.info("记忆提炼完成: taskId={}, userId={}, sessionId={}, messages={}, memories={}",
                    taskId, task.getUserId(), task.getSessionId(), total - lastCount, items.size());
        } catch (Exception e) {
            String error = e.getClass().getSimpleName()
                    + (StringUtils.hasText(e.getMessage()) ? ": " + e.getMessage() : "");
            log.warn("记忆提炼失败: taskId={}, sessionId={}, attempts={}, error={}",
                    taskId, task.getSessionId(), attempts, truncate(error, 300));
            taskRepository.markFailed(taskId, attempts, error);
        }
    }

    /**
     * 读取“上次提炼之后”的消息，最多 maxMessages 条。
     *
     * <p>如果积压超过窗口，只取最近窗口，并把进度推进到当前总数；正常聊天频率下
     * 90 秒内不会产生 40 条以上的消息，这个取舍是为了防止一次请求把提示词撑爆。
     */
    private List<ChatMessageDocument> loadMessages(String sessionId, long total) {
        int maxMessages = Math.max(1, properties.getExtract().getMaxMessages());
        long start = Math.max(0, total - maxMessages);
        Query query = new Query(Criteria.where("sessionId").is(sessionId))
                .with(Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("_id")))
                .skip(start)
                .limit(maxMessages);
        return mongoTemplate.find(query, ChatMessageDocument.class);
    }

    private String buildInput(Long userId, String sessionId, List<ChatMessageDocument> messages) {
        int maxChars = Math.max(1000, properties.getExtract().getMaxChars());
        StringBuilder input = new StringBuilder();
        String summary = profileService.find(userId)
                .map(p -> p.getSummary())
                .filter(StringUtils::hasText)
                .orElse("（暂无）");
        input.append("[已有用户画像]\n").append(summary).append("\n\n");

        WorkingMemory workingMemory = workingMemoryStore.get(userId, sessionId).orElse(null);
        input.append("[当前工作记忆]\n").append(describe(workingMemory)).append("\n\n");

        input.append("[新增对话]\n");
        for (ChatMessageDocument message : messages) {
            if (!StringUtils.hasText(message.getContent())) {
                continue;
            }
            String speaker = ChatMessageDocument.ROLE_USER.equals(message.getRole()) ? "用户" : "助手";
            String line = speaker + ": " + message.getContent().strip() + "\n";
            if (input.length() + line.length() > maxChars) {
                int remaining = maxChars - input.length();
                if (remaining > 20) {
                    input.append(line, 0, Math.min(line.length(), remaining));
                }
                break;
            }
            input.append(line);
        }
        return input.toString();
    }

    private String describe(WorkingMemory memory) {
        if (memory == null || memory.isEmpty()) {
            return "（暂无）";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(memory.getActiveTask())) {
            sb.append("进行中的任务：").append(memory.getActiveTask()).append("\n");
        }
        if (memory.getTodos() != null && !memory.getTodos().isEmpty()) {
            sb.append("待办：").append(String.join("；", memory.getTodos())).append("\n");
        }
        if (memory.getOpenQuestions() != null && !memory.getOpenQuestions().isEmpty()) {
            sb.append("待确认：").append(String.join("；", memory.getOpenQuestions())).append("\n");
        }
        if (memory.getRiskLevel() != null) {
            sb.append("关注程度：").append(memory.getRiskLevel()).append("/5\n");
        }
        return sb.toString().strip();
    }

    /**
     * 调独立 ChatClient（没有挂记忆 advisor，避免提炼内容污染对话历史）。
     * 优先 entity()；失败时用 BeanOutputConverter 显式拼格式再解析一次。
     */
    private MemoryDigest callModel(String input) {
        String system = promptProvider.extractionSystemPrompt();
        try {
            MemoryDigest digest = digestChatClient.prompt()
                    .system(system)
                    .user(input)
                    .call()
                    .entity(MemoryDigest.class);
            if (digest != null) {
                return digest;
            }
        } catch (Exception e) {
            log.warn("entity() 解析记忆 JSON 失败，尝试显式格式重试: error={}",
                    e.getClass().getSimpleName());
        }

        BeanOutputConverter<MemoryDigest> converter = new BeanOutputConverter<>(MemoryDigest.class);
        String raw = digestChatClient.prompt()
                .system(system)
                .user(input + "\n\n请只输出符合以下格式的 JSON，不要输出解释或 Markdown：\n"
                        + converter.getFormat())
                .call()
                .content();
        return converter.convert(raw);
    }

    private List<MemoryDigest.MemoryItem> cleanMemories(List<MemoryDigest.MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        int limit = Math.max(1, properties.getMaxMemoriesPerTurn());
        List<MemoryDigest.MemoryItem> result = new ArrayList<>();
        for (MemoryDigest.MemoryItem item : items) {
            if (item == null || !StringUtils.hasText(item.getContent())) {
                continue;
            }
            item.setContent(truncate(item.getContent().strip(), properties.getMemoryMaxChars()));
            if (!StringUtils.hasText(item.getStability())) {
                item.setStability("temporary");
            }
            if (!StringUtils.hasText(item.getType())) {
                item.setType(null);
            }
            item.setSensitivity(resolveSensitivity(item));
            result.add(item);
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    /**
     * 敏感度以模型标注为主，服务端按 importance/category 兜底：
     * 模型说 important，或 importance >= 4，或属于长期画像相关类别，都算 important。
     * 这样即使模型漏标，身份、症状、关系等重要信息也不会被 30 天清理机制误删。
     */
    private static String resolveSensitivity(MemoryDigest.MemoryItem item) {
        String marked = item.getSensitivity() == null ? "" : item.getSensitivity().strip();
        if ("important".equalsIgnoreCase(marked)) {
            return "important";
        }
        if (item.getImportance() != null && item.getImportance() >= 4) {
            return "important";
        }
        String category = item.getCategory() == null
                ? "" : item.getCategory().strip().toLowerCase(Locale.ROOT);
        return IMPORTANT_CATEGORIES.contains(category) ? "important" : "normal";
    }

    private void applyWorkingMemory(MemoryExtractTask task, MemoryDigest digest, String sessionType) {
        WorkingMemory memory = workingMemoryStore.get(task.getUserId(), task.getSessionId())
                .orElseGet(() -> {
                    WorkingMemory created = new WorkingMemory();
                    created.setSessionId(task.getSessionId());
                    created.setUserId(task.getUserId());
                    created.setType(sessionType);
                    created.setRiskLevel(0);
                    return created;
                });

        MemoryDigest.Working patch = digest.getWorkingMemory();
        if (patch != null) {
            if (StringUtils.hasText(patch.getActiveTask())) {
                memory.setActiveTask(truncate(patch.getActiveTask().strip(), 500));
            }
            if (patch.getTodos() != null) {
                memory.setTodos(cleanList(patch.getTodos()));
            }
            if (patch.getOpenQuestions() != null) {
                memory.setOpenQuestions(cleanList(patch.getOpenQuestions()));
            }
            if (patch.getRiskLevel() != null) {
                int risk = Math.max(0, Math.min(5, patch.getRiskLevel()));
                int current = memory.getRiskLevel() == null ? 0 : memory.getRiskLevel();
                memory.setRiskLevel(Math.max(current, risk));
            }
            if (StringUtils.hasText(patch.getType())) {
                memory.setType(normalizeType(patch.getType(), sessionType));
            }
        }
        if (!StringUtils.hasText(memory.getType())) {
            memory.setType(sessionType);
        }

        // 只有类型也算有效信息：下一次召回需要靠它做 type 过滤。
        if (memory.isEmpty() && !StringUtils.hasText(memory.getType())) {
            workingMemoryStore.delete(task.getUserId(), task.getSessionId());
        } else {
            workingMemoryStore.save(memory);
        }
    }

    private String resolveType(Long userId, String sessionId, String modelType) {
        if (StringUtils.hasText(modelType)) {
            return normalizeType(modelType, "chat");
        }
        return workingMemoryStore.get(userId, sessionId)
                .map(WorkingMemory::getType)
                .filter(StringUtils::hasText)
                .map(type -> normalizeType(type, "chat"))
                .orElse("chat");
    }

    private static List<String> cleanList(List<String> values) {
        if (values == null) {
            return new ArrayList<>();
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::strip)
                .limit(10)
                .map(value -> truncate(value, 200))
                .toList();
    }

    private static String normalizeType(String value, String fallback) {
        if ("treatment".equalsIgnoreCase(value)) {
            return WorkingMemory.TYPE_TREATMENT;
        }
        if ("chat".equalsIgnoreCase(value)) {
            return WorkingMemory.TYPE_CHAT;
        }
        return WorkingMemory.TYPE_TREATMENT.equalsIgnoreCase(fallback)
                ? WorkingMemory.TYPE_TREATMENT : WorkingMemory.TYPE_CHAT;
    }

    private static int safeCount(Integer count) {
        return count == null ? 0 : Math.max(0, count);
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }
}

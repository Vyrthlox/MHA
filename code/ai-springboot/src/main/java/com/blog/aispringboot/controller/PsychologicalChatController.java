package com.blog.aispringboot.controller;

import com.blog.aispringboot.common.BusinessException;
import com.blog.aispringboot.common.PageResult;
import com.blog.aispringboot.common.Result;
import com.blog.aispringboot.dto.ChatMessageVO;
import com.blog.aispringboot.dto.ChatReplyVO;
import com.blog.aispringboot.dto.ChatSessionVO;
import com.blog.aispringboot.dto.MemoryViewVO;
import com.blog.aispringboot.dto.SendMessageRequest;
import com.blog.aispringboot.entity.UserProfile;
import com.blog.aispringboot.memory.extract.MemoryExtractTaskRepository;
import com.blog.aispringboot.memory.longterm.LongTermMemoryService;
import com.blog.aispringboot.memory.working.WorkingMemory;
import com.blog.aispringboot.memory.working.WorkingMemoryStore;
import com.blog.aispringboot.security.LoginUser;
import com.blog.aispringboot.security.UserContext;
import com.blog.aispringboot.service.PsychologicalChatService;
import com.blog.aispringboot.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 对话的会话与历史接口。
 *
 * <p>所有接口都受 {@code JwtAuthInterceptor} 保护（不在白名单里），
 * 用户身份从 {@code UserContext} 取。
 *
 * <p>前端调用点：
 * <ul>
 *   <li>对话页 {@code home.vue}：会话列表、消息历史（本次接入）</li>
 *   <li>咨询记录页 {@code consultations.vue}：{@code GET /sessions}（分页参数用
 *       {@code currentPage/pageSize}，因此这里同时兼容两套参数名）</li>
 * </ul>
 */
@RestController
@RequestMapping("/psychological-chat")
@RequiredArgsConstructor
public class PsychologicalChatController {

    private final PsychologicalChatService chatService;
    private final UserProfileService userProfileService;
    private final WorkingMemoryStore workingMemoryStore;
    private final LongTermMemoryService longTermMemoryService;
    private final MemoryExtractTaskRepository memoryExtractTaskRepository;

    /** 新建会话 */
    @PostMapping("/sessions")
    public Result<ChatSessionVO> createSession() {
        return Result.success("会话创建成功", chatService.createSession());
    }

    /**
     * 会话分页列表。
     *
     * <p>同时接受 {@code page/size} 与 {@code currentPage/pageSize}：
     * 管理端分页组件传的是后者，新接入的页面用前者更符合常见习惯。
     * {@code mine=true} 时即使当前用户是管理员也只看本人的会话（对话页使用）。
     */
    @GetMapping("/sessions")
    public Result<PageResult<ChatSessionVO>> listSessions(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(name = "currentPage", required = false) Integer currentPage,
            @RequestParam(name = "pageSize", required = false) Integer pageSize,
            @RequestParam(name = "mine", required = false, defaultValue = "false") boolean mine) {
        int resolvedPage = page != null ? page : (currentPage != null ? currentPage : 1);
        int resolvedSize = size != null ? size : (pageSize != null ? pageSize : 10);
        return Result.success(chatService.listSessions(resolvedPage, resolvedSize, mine));
    }

    /** 会话的完整消息历史 */
    @GetMapping("/sessions/{sessionId}/messages")
    public Result<List<ChatMessageVO>> listMessages(@PathVariable String sessionId) {
        return Result.success(chatService.listMessages(sessionId));
    }

    /**
     * 发送消息并获取 AI 回复。
     *
     * <p>这是同步接口：大模型完整生成后才返回。前端调用时要把超时放宽到
     * 60~120 秒（{@code request.js} 默认只有 5 秒）。
     */
    @PostMapping("/sessions/{sessionId}/messages")
    public Result<ChatReplyVO> sendMessage(@PathVariable String sessionId,
                                           @Valid @RequestBody SendMessageRequest request) {
        return Result.success("回复成功", chatService.sendMessage(sessionId, request.getContent()));
    }

    /**
     * 查看本人当前的记忆状态（画像 + 工作记忆），用于调试和演示。
     *
     * <p>用户身份只从 JWT 取，绝不接受请求参数里的 userId。
     */
    @GetMapping("/memory")
    public Result<MemoryViewVO> memory(@RequestParam(required = false) String sessionId) {
        LoginUser user = currentUser();
        UserProfile profile = userProfileService.find(user.userId()).orElse(null);

        WorkingMemory workingMemory = sessionId != null && !sessionId.isBlank()
                ? workingMemoryStore.get(user.userId(), sessionId).orElse(null)
                : workingMemoryStore.findLatest(user.userId()).orElse(null);

        return Result.success(new MemoryViewVO(
                user.userId(),
                profile == null ? null : profile.getSummary(),
                profile == null ? 0 : profile.getRiskLevel(),
                profile == null ? 0 : profile.getMemoryCount(),
                profile == null ? null : profile.getLastExtractedAt(),
                workingMemory));
    }

    /**
     * 清空本人全部记忆：Milvus 长期记忆、MySQL 画像、Redis 工作记忆、待执行任务。
     *
     * <p>不可恢复，前端需要二次确认。会话与消息历史不在本接口范围内。
     *
     * <p>向量库不可用时仍要清掉画像与工作记忆，不能因为一个组件故障让用户的撤回请求
     * 整体失败；但此时长期向量还在，必须如实返回失败提示，让用户稍后重试。
     */
    @DeleteMapping("/memory")
    public Result<Void> clearMemory() {
        LoginUser user = currentUser();
        boolean vectorsCleared;
        try {
            longTermMemoryService.forget(user.userId());
            vectorsCleared = true;
        } catch (Exception e) {
            vectorsCleared = false;
        }
        userProfileService.clear(user.userId());
        workingMemoryStore.deleteAll(user.userId());
        memoryExtractTaskRepository.cancelByUserId(user.userId());
        if (!vectorsCleared) {
            throw new BusinessException("画像与工作记忆已清空，长期记忆暂未删除，请稍后重试");
        }
        return Result.success("记忆已清空", null);
    }

    /** 删除会话（连带消息） */
    @DeleteMapping("/sessions/{sessionId}")
    public Result<Void> deleteSession(@PathVariable String sessionId) {
        chatService.deleteSession(sessionId);
        return Result.success("会话已删除", null);
    }

    private LoginUser currentUser() {
        LoginUser user = UserContext.get();
        if (user == null || user.userId() == null) {
            throw new BusinessException("登录已过期，请重新登录");
        }
        return user;
    }
}

package com.blog.aispringboot.service;

import com.blog.aispringboot.entity.UserProfile;
import com.blog.aispringboot.memory.extract.MemoryDigest;
import com.blog.aispringboot.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 用户长期画像。
 *
 * <p>画像摘要是每轮提示词里最稳定、最常被使用的一段信息，因此只保留模型总结后的
 * 简短文本，不把原始对话塞进 MySQL。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileService {

    private static final int SUMMARY_MAX_CHARS = 1000;

    private final UserProfileRepository repository;
    private final JsonMapper jsonMapper;

    public Optional<UserProfile> find(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return repository.findByUserId(userId);
    }

    public void apply(Long userId, MemoryDigest.ProfilePatch patch) {
        if (userId == null || patch == null) {
            return;
        }
        String summary = StringUtils.hasText(patch.getSummary())
                ? truncate(patch.getSummary().strip(), SUMMARY_MAX_CHARS)
                : null;
        String traits = toJson(patch.getTraits());
        String mental = toJson(patch.getMentalStatus());
        Integer risk = clampRisk(patch.getRiskLevel());

        if (summary == null && traits == null && mental == null && risk == null) {
            return;
        }
        repository.upsert(userId, summary, traits, mental, risk, LocalDateTime.now());
    }

    /** 提炼完成后刷新记忆条数 */
    public void refreshMemoryCount(Long userId) {
        repository.refreshMemoryCount(userId);
    }

    public void clear(Long userId) {
        repository.deleteByUserId(userId);
    }

    public int currentRisk(Long userId) {
        return find(userId).map(UserProfile::getRiskLevel).orElse(0);
    }

    private String toJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("画像 JSON 序列化失败，跳过该字段: error={}", e.getClass().getSimpleName());
            return null;
        }
    }

    private static Integer clampRisk(Integer risk) {
        if (risk == null) {
            return null;
        }
        return Math.max(0, Math.min(5, risk));
    }

    private static String truncate(String text, int max) {
        if (text.codePointCount(0, text.length()) <= max) {
            return text;
        }
        return text.substring(0, text.offsetByCodePoints(0, max));
    }
}

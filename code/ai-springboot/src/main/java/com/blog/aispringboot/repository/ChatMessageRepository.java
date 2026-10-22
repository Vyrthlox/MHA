package com.blog.aispringboot.repository;

import com.blog.aispringboot.document.ChatMessageDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@code chat_messages} 仓储。
 *
 * <p>两个排序方法都带 {@code id} 作为第二排序键：MongoDB 的日期精度是毫秒，
 * 同毫秒写入的消息靠 ObjectId（时间前缀 + 自增计数）保证稳定顺序。
 */
@Repository
public interface ChatMessageRepository extends MongoRepository<ChatMessageDocument, String> {

    /** 完整历史，按时间正序（前端渲染用） */
    List<ChatMessageDocument> findBySessionIdOrderByCreatedAtAscIdAsc(String sessionId);

    /** 最近 N 条，按时间倒序（记忆窗口用，取完需反转） */
    List<ChatMessageDocument> findBySessionIdOrderByCreatedAtDescIdDesc(String sessionId, Pageable pageable);

    /** 最后一条消息（发送接口在模型回复后取回持久化结果） */
    Optional<ChatMessageDocument> findFirstBySessionIdOrderByCreatedAtDescIdDesc(String sessionId);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);
}

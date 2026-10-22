package com.blog.aispringboot.repository;

import com.blog.aispringboot.document.ChatSessionDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

/**
 * {@code chat_sessions} 仓储。
 *
 * <p>普通用户只能看到自己的会话，因此按 {@code userId} 分页查询；
 * 管理员查看全部（前端「咨询记录」页面）。
 */
@Repository
public interface ChatSessionRepository extends MongoRepository<ChatSessionDocument, String> {

    Page<ChatSessionDocument> findByUserId(Long userId, Pageable pageable);
}

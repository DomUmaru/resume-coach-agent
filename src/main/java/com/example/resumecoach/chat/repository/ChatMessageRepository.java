package com.example.resumecoach.chat.repository;

import com.example.resumecoach.chat.model.entity.ChatMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 中文说明：聊天消息仓储。
 */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);
}


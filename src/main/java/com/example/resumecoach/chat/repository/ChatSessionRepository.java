package com.example.resumecoach.chat.repository;

import com.example.resumecoach.chat.model.entity.ChatSessionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 中文说明：聊天会话仓储。
 */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, String> {
}


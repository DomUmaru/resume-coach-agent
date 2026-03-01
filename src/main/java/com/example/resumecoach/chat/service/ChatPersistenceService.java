package com.example.resumecoach.chat.service;

import com.example.resumecoach.chat.model.entity.ChatMessageEntity;
import com.example.resumecoach.chat.model.entity.ChatSessionEntity;
import com.example.resumecoach.chat.repository.ChatMessageRepository;
import com.example.resumecoach.chat.repository.ChatSessionRepository;
import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.rag.context.Citation;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 中文说明：聊天持久化服务，统一处理会话与消息落库。
 */
@Service
public class ChatPersistenceService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public ChatPersistenceService(ChatSessionRepository chatSessionRepository,
                                  ChatMessageRepository chatMessageRepository,
                                  ObjectMapper objectMapper) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 中文说明：确保会话存在；如果不存在则创建一条新的会话记录。
     */
    @Transactional
    public void ensureSession(String sessionId, String userId, String docId, String title) {
        if (chatSessionRepository.existsById(sessionId)) {
            return;
        }
        ChatSessionEntity session = new ChatSessionEntity();
        session.setId(sessionId);
        session.setUserId(userId);
        session.setDocId(docId);
        session.setTitle(title);
        chatSessionRepository.save(session);
    }

    /**
     * 中文说明：保存一条用户消息。
     */
    @Transactional
    public void saveUserMessage(String sessionId, String content) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(IdGenerator.generate("msg"));
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setContent(content);
        chatMessageRepository.save(entity);
    }

    /**
     * 中文说明：保存一条 assistant 消息，同时记录 citation 和工具轨迹。
     */
    @Transactional
    public void saveAssistantMessage(String sessionId, String content, List<Citation> citations, String toolTraceJson) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setId(IdGenerator.generate("msg"));
        entity.setSessionId(sessionId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setToolTraceJson(toolTraceJson);
        entity.setCitationsJson(toJson(citations));
        chatMessageRepository.save(entity);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            // 中文说明：序列化失败时返回空数组，避免影响聊天主链路。
            return "[]";
        }
    }
}

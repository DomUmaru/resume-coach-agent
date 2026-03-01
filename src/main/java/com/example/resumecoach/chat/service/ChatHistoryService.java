package com.example.resumecoach.chat.service;

import com.example.resumecoach.chat.model.dto.ChatHistoryResponse;
import com.example.resumecoach.chat.model.entity.ChatMessageEntity;
import com.example.resumecoach.chat.repository.ChatMessageRepository;
import com.example.resumecoach.rag.context.Citation;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：会话历史服务。
 * 策略：按时间正序读取消息，保证前端复盘对话时语义连贯。
 */
@Service
public class ChatHistoryService {

    private final ChatMessageRepository chatMessageRepository;
    private final ObjectMapper objectMapper;

    public ChatHistoryService(ChatMessageRepository chatMessageRepository, ObjectMapper objectMapper) {
        this.chatMessageRepository = chatMessageRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 中文说明：查询指定 session 的完整消息历史。
     */
    public ChatHistoryResponse queryBySessionId(String sessionId) {
        ChatHistoryResponse response = new ChatHistoryResponse();
        response.setSessionId(sessionId);
        List<ChatMessageEntity> entities = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<ChatHistoryResponse.MessageItem> messages = entities.stream()
                .map(entity -> new ChatHistoryResponse.MessageItem(
                        entity.getRole(),
                        entity.getContent(),
                        parseCitations(entity.getCitationsJson())
                ))
                .toList();
        response.setMessages(messages);
        return response;
    }

    private List<Citation> parseCitations(String citationsJson) {
        if (citationsJson == null || citationsJson.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(citationsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            // 中文说明：历史解析失败时返回空引用，避免影响主查询结果。
            return new ArrayList<>();
        }
    }
}

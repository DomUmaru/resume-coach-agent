package com.example.resumecoach.chat.model.dto;

import com.example.resumecoach.rag.context.Citation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：会话历史响应体。
 */
@Data
public class ChatHistoryResponse {

    private String sessionId;
    private List<MessageItem> messages = new ArrayList<>();

    /**
     * 中文说明：历史消息单条结构。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MessageItem {
        private String role;
        private String content;
        private List<Citation> citations = new ArrayList<>();
    }
}

package com.example.resumecoach.chat.controller;

import com.example.resumecoach.chat.model.dto.ChatHistoryResponse;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.chat.service.ChatHistoryService;
import com.example.resumecoach.chat.service.ChatStreamService;
import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 中文说明：聊天相关接口。
 * 输入：流式聊天请求或会话 ID。
 * 输出：SSE 事件流或历史消息列表。
 * 策略：接口层只负责路由、参数校验和统一响应，聊天主流程下沉到 ChatStreamService。
 */
@RestController
@RequestMapping("/api/chat")
@Validated
public class ChatController {

    private final ChatStreamService chatStreamService;
    private final ChatHistoryService chatHistoryService;

    public ChatController(ChatStreamService chatStreamService, ChatHistoryService chatHistoryService) {
        this.chatStreamService = chatStreamService;
        this.chatHistoryService = chatHistoryService;
    }

    /**
     * 中文说明：发起一轮流式聊天。
     * @param request 聊天请求，包含用户消息、sessionId、docId 和可选检索参数
     * @return SSE 发射器，后续会连续推送 start、tool_call、token、citation、done 等事件
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody @Valid ChatStreamRequest request) {
        return chatStreamService.stream(request);
    }

    /**
     * 中文说明：查询指定会话的历史消息。
     * @param sessionId 会话 ID
     * @return 统一响应包装的历史消息列表
     */
    @GetMapping("/history/{sessionId}")
    public ApiResponse<ChatHistoryResponse> history(@PathVariable("sessionId") @NotBlank String sessionId) {
        ChatHistoryResponse response = chatHistoryService.queryBySessionId(sessionId);
        return ApiResponse.ok(response, TraceContext.getTraceId());
    }
}

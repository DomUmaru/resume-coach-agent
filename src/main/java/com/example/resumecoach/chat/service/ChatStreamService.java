package com.example.resumecoach.chat.service;

import com.example.resumecoach.agent.orchestrator.AgentOrchestrator;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 中文说明：聊天流式服务，负责将 Agent 结果拆分为 SSE 事件输出。
 * 输入：聊天请求。
 * 输出：SSE 事件流（start/tool_call/token/citation/done）。
 * 策略：先发过程事件，再按 token 分片输出答案，便于前端实时渲染。
 */
@Service
public class ChatStreamService {

    private final AgentOrchestrator agentOrchestrator;
    private final ChatPersistenceService chatPersistenceService;
    private final ObjectMapper objectMapper;

    public ChatStreamService(AgentOrchestrator agentOrchestrator,
                             ChatPersistenceService chatPersistenceService,
                             ObjectMapper objectMapper) {
        this.agentOrchestrator = agentOrchestrator;
        this.chatPersistenceService = chatPersistenceService;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);

        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "start", Map.of("status", "processing"));
                chatPersistenceService.ensureSession(request.getSessionId(), request.getUserId(),
                        request.getDocId(), "简历对话");
                chatPersistenceService.saveUserMessage(request.getSessionId(), request.getMessage());

                AgentOrchestrator.AgentResult result = agentOrchestrator.handle(request);

                Map<String, Object> toolPayload = new HashMap<>();
                toolPayload.put("intent", result.getDecision().getIntent());
                toolPayload.put("shouldRetrieve", result.getDecision().isShouldRetrieve());
                toolPayload.put("selectedTool", result.getSelectedTool());
                sendEvent(emitter, "tool_call", toolPayload);

                // 中文说明：按空格切分 token 用于演示流式输出，后续替换为真实模型 token 流。
                String[] tokens = result.getAnswer().split(" ");
                for (String token : tokens) {
                    sendEvent(emitter, "token", Map.of("content", token + " "));
                    Thread.sleep(35L);
                }

                sendEvent(emitter, "citation", result.getCitations());
                sendEvent(emitter, "done", Map.of("status", "completed"));

                String toolTraceJson = objectMapper.writeValueAsString(toolPayload);
                chatPersistenceService.saveAssistantMessage(request.getSessionId(), result.getAnswer(),
                        result.getCitations(), toolTraceJson);
                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", Map.of("message", ex.getMessage()));
                } catch (IOException ignored) {
                    // 中文说明：若连接已经断开，额外发送错误事件没有意义，直接结束即可。
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}

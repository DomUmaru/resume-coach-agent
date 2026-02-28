package com.example.resumecoach.chat.service;

import com.example.resumecoach.agent.orchestrator.AgentOrchestrator;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.observability.service.RagTraceLogService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 中文说明：聊天流式服务，负责将 Agent 结果拆分为 SSE 事件输出。
 * 策略：主链路成功后补写可观测日志，保证业务可用优先。
 */
@Service
public class ChatStreamService {

    private final AgentOrchestrator agentOrchestrator;
    private final ChatPersistenceService chatPersistenceService;
    private final RagTraceLogService ragTraceLogService;
    private final ObjectMapper objectMapper;

    public ChatStreamService(AgentOrchestrator agentOrchestrator,
                             ChatPersistenceService chatPersistenceService,
                             RagTraceLogService ragTraceLogService,
                             ObjectMapper objectMapper) {
        this.agentOrchestrator = agentOrchestrator;
        this.chatPersistenceService = chatPersistenceService;
        this.ragTraceLogService = ragTraceLogService;
        this.objectMapper = objectMapper;
    }

    public SseEmitter stream(ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String traceId = TraceContext.getTraceId();

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
                toolPayload.put("selectedToolArguments", result.getSelectedToolArguments());
                toolPayload.put("selectedToolConfidence", result.getSelectedToolConfidence());
                toolPayload.put("selectedToolReason", result.getSelectedToolReason());
                sendEvent(emitter, "tool_call", toolPayload);

                String[] tokens = result.getAnswer().split(" ");
                for (String token : tokens) {
                    sendEvent(emitter, "token", Map.of("content", token + " "));
                    Thread.sleep(35L);
                }

                sendEvent(emitter, "citation", result.getCitations());
                sendEvent(emitter, "done", Map.of("status", "completed"));

                String toolTraceJson = objectMapper.writeValueAsString(toolPayload);
                chatPersistenceService.saveAssistantMessage(
                        request.getSessionId(),
                        result.getAnswer(),
                        result.getCitations(),
                        toolTraceJson);

                String rewritten = String.valueOf(result.getRetrievalTrace().getOrDefault("rewrittenQuery", ""));
                @SuppressWarnings("unchecked")
                List<String> multiQueries = (List<String>) result.getRetrievalTrace().getOrDefault("multiQueries", List.of());
                ragTraceLogService.save(
                        traceId,
                        request.getSessionId(),
                        request.getMessage(),
                        rewritten,
                        multiQueries,
                        result.getRetrievalTrace(),
                        toolPayload,
                        result.getGuardrailTrace(),
                        result.getLatency(),
                        result.getCitations());

                emitter.complete();
            } catch (Exception ex) {
                try {
                    sendEvent(emitter, "error", Map.of("message", ex.getMessage()));
                } catch (IOException ignored) {
                    // 中文说明：连接已断开时无需继续发送错误事件。
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

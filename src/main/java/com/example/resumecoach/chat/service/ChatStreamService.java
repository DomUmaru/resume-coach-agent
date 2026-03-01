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
 * 中文说明：聊天流式服务。
 * 职责：调用 AgentOrchestrator 执行一轮对话，并把结果拆成 SSE 事件连续推送给前端。
 * 策略：先保证主链路可用，再补写聊天消息和 trace 日志；即使日志保存失败，也不影响主回答返回。
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

    /**
     * 中文说明：执行一次流式聊天。
     * 流程：创建 SSE 通道 -> 写入用户消息 -> 调用 Agent -> 发送 start/tool_call/token/citation/done 事件
     * -> 持久化 assistant 消息 -> 写入 trace。
     * @param request 聊天请求
     * @return 可持续推送事件的 SseEmitter
     */
    public SseEmitter stream(ChatStreamRequest request) {
        SseEmitter emitter = new SseEmitter(120_000L);
        String traceId = TraceContext.getTraceId();

        CompletableFuture.runAsync(() -> {
            try {
                sendEvent(emitter, "start", Map.of("status", "processing"));
                // 先确保会话存在，再保存用户输入，保证后续 trace 和历史消息能正确关联。
                chatPersistenceService.ensureSession(request.getSessionId(), request.getUserId(),
                        request.getDocId(), "简历对话");
                chatPersistenceService.saveUserMessage(request.getSessionId(), request.getMessage());

                AgentOrchestrator.AgentResult result = agentOrchestrator.handle(request);

                // tool_call 事件用于让前端和 trace 同时看到本轮意图、工具选择和参数信息。
                Map<String, Object> toolPayload = new HashMap<>();
                toolPayload.put("intent", result.getDecision().getIntent());
                toolPayload.put("shouldRetrieve", result.getDecision().isShouldRetrieve());
                toolPayload.put("selectedTool", result.getSelectedTool());
                toolPayload.put("selectedToolArguments", result.getSelectedToolArguments());
                toolPayload.put("selectedToolArgumentValidation", result.getSelectedToolArgumentValidation());
                toolPayload.put("selectedToolConfidence", result.getSelectedToolConfidence());
                toolPayload.put("selectedToolReason", result.getSelectedToolReason());
                sendEvent(emitter, "tool_call", toolPayload);

                // 当前实现按空格模拟 token 流式输出，重点是演示 SSE 事件组织方式，而不是精确 tokenizer。
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
                    // 中文说明：连接已经断开时，无需继续尝试推送 error 事件。
                }
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    /**
     * 中文说明：统一发送一个 SSE 事件，避免上层重复拼装事件对象。
     */
    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event().name(eventName).data(data));
    }
}

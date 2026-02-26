package com.example.resumecoach.ai.service;

import com.example.resumecoach.agent.model.ToolSelectionDecision;
import com.example.resumecoach.agent.tool.ToolNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 中文说明：统一封装 Spring AI 文本生成能力。
 * 输入：业务场景所需提示词与证据上下文。
 * 输出：模型生成文本；若模型不可用则返回本地降级文本。
 * 策略：优先走 Spring AI ChatClient，异常时自动降级，确保主链路不中断。
 */
@Service
public class LlmService {

    private final ChatClient chatClient;
    private final boolean enabled;
    private final ObjectMapper objectMapper;

    public LlmService(ObjectProvider<ChatClient.Builder> builderProvider,
                      ObjectMapper objectMapper,
                      @Value("${app.ai.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = (enabled && builder != null) ? builder.build() : null;
    }

    public String generateStarRewrite(String rawText, String evidence, String fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        String prompt = """
                你是简历教练。请将用户输入改写为 STAR 结构，要求：
                1) 输出包含 S/T/A/R 四段；
                2) 内容简洁、量化优先；
                3) 若给定证据与原文冲突，以证据为准。

                用户原文：
                %s

                检索证据：
                %s
                """.formatted(rawText, evidence == null ? "无" : evidence);
        return callOrFallback(prompt, fallback);
    }

    public String generateQaAnswer(String question, String evidence, String fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        String prompt = """
                你是简历问答助手。请基于证据回答问题，要求：
                1) 先给直接答案，再给 2-3 条要点；
                2) 不要编造证据之外的信息；
                3) 若证据不足，明确说明并给出追问建议。

                用户问题：
                %s

                检索证据：
                %s
                """.formatted(question, evidence == null ? "无" : evidence);
        return callOrFallback(prompt, fallback);
    }

    public boolean isAvailable() {
        return enabled && chatClient != null;
    }

    public ToolSelectionDecision chooseTool(String intent, String userMessage, boolean shouldRetrieve) {
        String fallbackTool = ruleBasedTool(intent);
        ToolSelectionDecision fallback = new ToolSelectionDecision(fallbackTool, 0.6d, "rule-fallback");
        if (!isAvailable()) {
            return fallback;
        }
        String prompt = """
                你是一个工具路由器。请在以下工具中选择一个最合适的工具名，并严格输出 JSON（不要输出其他文字）。
                输出 schema:
                {
                  "toolName": "retrieve_resume_context_tool|star_rewrite_tool|resume_qa_tool|none",
                  "confidence": 0.0-1.0,
                  "reason": "一句中文理由"
                }

                可选工具：
                1) %s
                2) %s
                3) %s
                4) %s

                规则：
                - 如果是 STAR 改写，优先选 %s
                - 如果是简历问答或模拟面试，优先选 %s
                - 如果用户只是要求“展示证据/上下文”，选 %s
                - 无需执行工具时选 %s

                已识别意图：%s
                是否需要检索：%s
                用户输入：%s
                """.formatted(
                ToolNames.RETRIEVE, ToolNames.STAR_REWRITE, ToolNames.RESUME_QA, ToolNames.NONE,
                ToolNames.STAR_REWRITE, ToolNames.RESUME_QA, ToolNames.RETRIEVE, ToolNames.NONE,
                intent, shouldRetrieve, userMessage
        );
        String raw = callOrFallback(prompt, "{\"toolName\":\"" + fallbackTool + "\",\"confidence\":0.60,\"reason\":\"fallback\"}");
        return parseAndValidateDecision(raw, fallback);
    }

    private String callOrFallback(String prompt, String fallback) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            return (content == null || content.isBlank()) ? fallback : content;
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String ruleBasedTool(String intent) {
        if ("REWRITE".equals(intent)) {
            return ToolNames.STAR_REWRITE;
        }
        if ("QA".equals(intent) || "MOCK_INTERVIEW".equals(intent)) {
            return ToolNames.RESUME_QA;
        }
        return ToolNames.NONE;
    }

    private ToolSelectionDecision parseAndValidateDecision(String raw, ToolSelectionDecision fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String tool = normalizeToolName(root.path("toolName").asText(), fallback.getToolName());
            double confidence = root.path("confidence").asDouble(fallback.getConfidence());
            String reason = root.path("reason").asText("model-selection");
            if (confidence < 0.0d || confidence > 1.0d) {
                confidence = fallback.getConfidence();
            }
            return new ToolSelectionDecision(tool, confidence, reason);
        } catch (Exception ignored) {
            String tool = normalizeToolName(raw, fallback.getToolName());
            return new ToolSelectionDecision(tool, fallback.getConfidence(), "parse-fallback");
        }
    }

    private String normalizeToolName(String raw, String fallback) {
        String normalized = raw.trim().toLowerCase();
        if (normalized.contains(ToolNames.STAR_REWRITE)) return ToolNames.STAR_REWRITE;
        if (normalized.contains(ToolNames.RESUME_QA)) return ToolNames.RESUME_QA;
        if (normalized.contains(ToolNames.RETRIEVE)) return ToolNames.RETRIEVE;
        if (normalized.contains(ToolNames.NONE)) return ToolNames.NONE;
        return fallback;
    }
}

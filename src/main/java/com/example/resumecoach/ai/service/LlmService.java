package com.example.resumecoach.ai.service;

import com.example.resumecoach.agent.model.ToolSelectionDecision;
import com.example.resumecoach.agent.tool.ToolNames;
import com.example.resumecoach.agent.tool.ToolRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 中文说明：统一封装 Spring AI 文本生成与工具决策能力。
 * 输入：业务提示词、检索证据、工具注册信息。
 * 输出：模型生成的文本，或结构化的工具选择结果。
 * 策略：优先走 Spring AI ChatClient；异常或关闭 AI 时自动降级，保证主链路不断。
 */
@Service
public class LlmService {

    private final ChatClient chatClient;
    private final boolean enabled;
    private final ObjectMapper objectMapper;
    private final ToolRegistry toolRegistry;

    public LlmService(ObjectProvider<ChatClient.Builder> builderProvider,
                      ObjectMapper objectMapper,
                      ToolRegistry toolRegistry,
                      @Value("${app.ai.enabled:false}") boolean enabled) {
        this.enabled = enabled;
        this.objectMapper = objectMapper;
        this.toolRegistry = toolRegistry;
        ChatClient.Builder builder = builderProvider.getIfAvailable();
        this.chatClient = (enabled && builder != null) ? builder.build() : null;
    }

    /**
     * 中文说明：生成 STAR 改写结果。
     * @param rawText 用户原始文本
     * @param evidence 检索到的证据
     * @param fallback 模型不可用时的本地兜底文本
     * @return 改写结果
     */
    public String generateStarRewrite(String rawText, String evidence, String fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        String prompt = """
                你是简历教练。请将用户输入改写为 STAR 结构，要求：
                1) 输出包含 S/T/A/R 四段；
                2) 内容简洁、量化优先；
                3) 如果给定证据与原文冲突，以证据为准。
                用户原文：
                %s

                检索证据：
                %s
                """.formatted(rawText, evidence == null ? "无" : evidence);
        return callOrFallback(prompt, fallback);
    }

    /**
     * 中文说明：基于证据生成问答答案。
     * @param question 用户问题
     * @param evidence 检索证据
     * @param fallback 模型不可用时的兜底答案
     * @return 问答结果
     */
    public String generateQaAnswer(String question, String evidence, String fallback) {
        if (!isAvailable()) {
            return fallback;
        }
        String prompt = """
                你是简历问答助手。请基于证据回答问题，要求：
                1) 先给直接答案，再给 2-3 条要点；
                2) 不要编造证据之外的信息；
                3) 如果证据不足，明确说明并给出追问建议。
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

    /**
     * 中文说明：让模型决定当前该调用哪个工具。
     * @param intent 已识别意图
     * @param userMessage 用户原始输入
     * @param shouldRetrieve 当前轮是否适合检索
     * @return 结构化工具选择结果；若模型不可用或解析失败，则回退为规则选择
     */
    public ToolSelectionDecision chooseTool(String intent, String userMessage, boolean shouldRetrieve) {
        String fallbackTool = ruleBasedTool(intent);
        ToolSelectionDecision fallback = new ToolSelectionDecision(
                fallbackTool,
                defaultArguments(fallbackTool, userMessage),
                buildArgumentValidation(fallbackTool, Map.of(), defaultArguments(fallbackTool, userMessage), "rule-fallback"),
                0.6d,
                "rule-fallback");
        if (!isAvailable()) {
            return fallback;
        }

        String prompt = """
                你是一个工具路由器。请从候选工具中选择最合适的工具，并严格输出 JSON，不要输出任何额外文字。
                输出 schema:
                {
                  "toolName": "retrieve_resume_context_tool|star_rewrite_tool|resume_qa_tool|none",
                  "arguments": {},
                  "confidence": 0.0-1.0,
                  "reason": "一句中文理由"
                }

                工具定义：
                %s

                路由规则：
                - 如果是 STAR 改写，优先选 %s，并提供 rawText
                - 如果是简历问答或模拟面试，优先选 %s，并提供 question
                - 如果是展示证据、限定检索范围或只需要证据上下文，优先选 %s，并提供 query
                - 如果不需要执行工具，选 %s，并让 arguments 为空对象

                已识别意图：%s
                是否需要检索：%s
                用户输入：%s
                """.formatted(
                toolRegistry.promptBlock(),
                ToolNames.STAR_REWRITE,
                ToolNames.RESUME_QA,
                ToolNames.RETRIEVE,
                ToolNames.NONE,
                intent,
                shouldRetrieve,
                userMessage
        );
        String raw = callOrFallback(prompt, fallbackJson(fallbackTool, userMessage));
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

    /**
     * 中文说明：解析模型返回的工具选择 JSON，并做参数归一化与校验。
     * 若模型输出不可解析，则自动回退到 fallback 结果。
     */
    private ToolSelectionDecision parseAndValidateDecision(String raw, ToolSelectionDecision fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            JsonNode root = objectMapper.readTree(raw);
            String tool = normalizeToolName(root.path("toolName").asText(), fallback.getToolName());
            Map<String, Object> rawArguments = objectMapper.convertValue(root.path("arguments"), new TypeReference<>() {
            });
            Map<String, Object> arguments = sanitizeArguments(
                    tool,
                    rawArguments,
                    fallback.getArguments());
            Map<String, Object> argumentValidation =
                    buildArgumentValidation(tool, rawArguments, arguments, "model");
            double confidence = root.path("confidence").asDouble(fallback.getConfidence());
            String reason = root.path("reason").asText("model-selection");
            if (confidence < 0.0d || confidence > 1.0d) {
                confidence = fallback.getConfidence();
            }
            return new ToolSelectionDecision(tool, arguments, argumentValidation, confidence, reason);
        } catch (Exception ignored) {
            String tool = normalizeToolName(raw, fallback.getToolName());
            return new ToolSelectionDecision(
                    tool,
                    fallback.getArguments(),
                    buildArgumentValidation(tool, Map.of(), fallback.getArguments(), "parse-fallback"),
                    fallback.getConfidence(),
                    "parse-fallback");
        }
    }

    private String fallbackJson(String toolName, String userMessage) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("toolName", toolName);
        payload.put("arguments", defaultArguments(toolName, userMessage));
        payload.put("confidence", 0.60d);
        payload.put("reason", "fallback");
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"toolName\":\"" + toolName + "\",\"arguments\":{},\"confidence\":0.60,\"reason\":\"fallback\"}";
        }
    }

    private Map<String, Object> defaultArguments(String toolName, String userMessage) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        if (ToolNames.STAR_REWRITE.equals(toolName)) {
            arguments.put("rawText", userMessage);
            arguments.put("tone", "TECHNICAL");
        } else if (ToolNames.RESUME_QA.equals(toolName)) {
            arguments.put("question", userMessage);
        } else if (ToolNames.RETRIEVE.equals(toolName)) {
            arguments.put("query", userMessage);
        }
        return arguments;
    }

    private Map<String, Object> sanitizeArguments(String toolName,
                                                  Map<String, Object> arguments,
                                                  Map<String, Object> fallbackArguments) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> source = arguments == null ? Map.of() : arguments;
        if (ToolNames.STAR_REWRITE.equals(toolName)) {
            putIfPresent(result, "rawText", source.get("rawText"));
            putIfPresent(result, "targetRole", source.get("targetRole"));
            putIfPresent(result, "tone", normalizeTone(source.get("tone")));
        } else if (ToolNames.RESUME_QA.equals(toolName)) {
            putIfPresent(result, "question", source.get("question"));
            putIfPresent(result, "focusSection", normalizeSection(source.get("focusSection")));
        } else if (ToolNames.RETRIEVE.equals(toolName)) {
            putIfPresent(result, "query", source.get("query"));
            putIfPresent(result, "section", normalizeSection(source.get("section")));
            putIfPresent(result, "page", normalizeInteger(source.get("page")));
            putIfPresent(result, "chunkType", normalizeChunkType(source.get("chunkType")));
        }
        if (result.isEmpty() && fallbackArguments != null) {
            result.putAll(fallbackArguments);
        }
        return result;
    }

    private Map<String, Object> buildArgumentValidation(String toolName,
                                                        Map<String, Object> rawArguments,
                                                        Map<String, Object> normalizedArguments,
                                                        String source) {
        Map<String, Object> validation = new LinkedHashMap<>();
        Map<String, Object> raw = rawArguments == null ? Map.of() : rawArguments;
        Map<String, Object> normalized = normalizedArguments == null ? Map.of() : normalizedArguments;

        validation.put("source", source);
        validation.put("toolName", toolName);
        validation.put("rawArguments", raw);
        validation.put("normalizedArguments", normalized);
        validation.put("rawArgumentCount", raw.size());
        validation.put("normalizedArgumentCount", normalized.size());
        validation.put("usedFallbackArguments", !raw.isEmpty() && normalized.isEmpty());

        List<String> droppedKeys = raw.keySet().stream()
                .filter(key -> !normalized.containsKey(key))
                .toList();
        validation.put("droppedKeys", droppedKeys);
        validation.put("hasDroppedKeys", !droppedKeys.isEmpty());
        validation.put("valid", droppedKeys.isEmpty() || !normalized.isEmpty());
        return validation;
    }

    private void putIfPresent(Map<String, Object> map, String key, Object value) {
        if (value == null) {
            return;
        }
        String stringValue = String.valueOf(value).trim();
        if (!stringValue.isBlank() && !"null".equalsIgnoreCase(stringValue)) {
            map.put(key, value);
        }
    }

    private Object normalizeInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalizeTone(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "CONCISE", "IMPACTFUL", "TECHNICAL" -> normalized;
            default -> null;
        };
    }

    private String normalizeSection(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SUMMARY", "PROJECT", "WORK", "EDUCATION", "SKILL", "OTHER" -> normalized;
            default -> null;
        };
    }

    private String normalizeChunkType(Object value) {
        if (value == null) {
            return null;
        }
        String normalized = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "PARENT", "CHILD", "SUMMARY" -> normalized;
            default -> null;
        };
    }

    private String normalizeToolName(String raw, String fallback) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains(ToolNames.STAR_REWRITE)) {
            return ToolNames.STAR_REWRITE;
        }
        if (normalized.contains(ToolNames.RESUME_QA)) {
            return ToolNames.RESUME_QA;
        }
        if (normalized.contains(ToolNames.RETRIEVE)) {
            return ToolNames.RETRIEVE;
        }
        if (normalized.contains(ToolNames.NONE)) {
            return ToolNames.NONE;
        }
        return fallback;
    }
}

package com.example.resumecoach.ai.service;

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

    public LlmService(ObjectProvider<ChatClient.Builder> builderProvider,
                      @Value("${app.ai.enabled:false}") boolean enabled) {
        this.enabled = enabled;
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

    private String callOrFallback(String prompt, String fallback) {
        try {
            String content = chatClient.prompt().user(prompt).call().content();
            return (content == null || content.isBlank()) ? fallback : content;
        } catch (Exception ex) {
            return fallback;
        }
    }
}


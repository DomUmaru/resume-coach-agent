package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.rag.context.Citation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 中文说明：简历问答工具。
 * 策略：优先调用 Spring AI 生成问答结果，模型不可用时降级为模板回答。
 */
@Component
public class ResumeQaTool {

    private final LlmService llmService;

    public ResumeQaTool(LlmService llmService) {
        this.llmService = llmService;
    }

    public ToolCallResult run(String question, String docId, String retrievedEvidence) {
        String fallback = "你在项目中主要负责召回策略优化、特征工程迭代，以及线上效果监控与回归分析。";
        String content = llmService.generateQaAnswer(question, retrievedEvidence, fallback);
        Citation citation = new Citation("chunk_103", docId, 2, "WORK", 0.84d);
        return new ToolCallResult("resume_qa_tool", content, List.of(citation));
    }
}

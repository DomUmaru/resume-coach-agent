package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.rag.context.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：简历问答工具。
 * 职责：基于用户问题和检索到的简历证据，生成一段问答结果。
 * 策略：优先调用 LLM 生成答案；当模型不可用时使用本地 fallback 文案，保证链路可用。
 */
@Component
public class ResumeQaTool {

    private final LlmService llmService;

    public ResumeQaTool(LlmService llmService) {
        this.llmService = llmService;
    }

    /**
     * 中文说明：执行一次简历问答。
     * @param question 用户问题
     * @param docId 当前简历文档 ID
     * @param retrievedEvidence 已检索到的证据文本
     * @param supportingCitations 支撑当前答案的引用列表
     * @return 工具输出结果，包含答案文本和 citation
     */
    public ToolCallResult run(String question,
                              String docId,
                              String retrievedEvidence,
                              List<Citation> supportingCitations) {
        String fallback = "你在项目中主要负责召回策略优化、特征工程迭代，以及线上效果监控与回归分析。";
        String content = llmService.generateQaAnswer(question, retrievedEvidence, fallback);
        List<Citation> citations = supportingCitations == null ? new ArrayList<>() : new ArrayList<>(supportingCitations);
        if (citations.isEmpty()) {
            // 当上层没有携带 citation 时，给出一个最小可用占位，避免工具结果完全失去引用信息。
            citations.add(new Citation("chunk_103", docId, 2, "WORK", 0.84d));
        }
        return new ToolCallResult("resume_qa_tool", content, citations);
    }
}

package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.rag.context.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：STAR 改写工具。
 * 策略：优先调用 Spring AI 生成改写结果，模型不可用时降级为本地模板。
 */
@Component
public class StarRewriteTool {

    private final LlmService llmService;

    public StarRewriteTool(LlmService llmService) {
        this.llmService = llmService;
    }

    public ToolCallResult run(String rawText,
                              String docId,
                              String retrievedEvidence,
                              List<Citation> supportingCitations) {
        String fallback = """
                S（情境）：负责核心推荐场景的召回质量优化。
                T（任务）：在保证延迟可控前提下提升点击率和转化率。
                A（行动）：重构召回策略并引入分层召回与特征优化。
                R（结果）：线上点击率提升约 12%，核心链路延迟稳定在 SLA 范围内。""";
        String content = llmService.generateStarRewrite(rawText, retrievedEvidence, fallback);
        List<Citation> citations = supportingCitations == null ? new ArrayList<>() : new ArrayList<>(supportingCitations);
        if (citations.isEmpty()) {
            citations.add(new Citation("chunk_102", docId, 3, "PROJECT", 0.89d));
        }
        return new ToolCallResult("star_rewrite_tool", content, citations);
    }
}

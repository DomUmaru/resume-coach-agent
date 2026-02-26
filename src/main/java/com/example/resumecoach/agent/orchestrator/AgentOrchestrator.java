package com.example.resumecoach.agent.orchestrator;

import com.example.resumecoach.agent.model.SkillDecision;
import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.model.ToolSelectionDecision;
import com.example.resumecoach.agent.skill.AnswerSkill;
import com.example.resumecoach.agent.skill.IntentSkill;
import com.example.resumecoach.agent.skill.RetrievalSkill;
import com.example.resumecoach.agent.tool.ToolNames;
import com.example.resumecoach.agent.tool.ResumeQaTool;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.agent.tool.StarRewriteTool;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.guardrail.CitationVerifierService;
import com.example.resumecoach.rag.guardrail.NoEvidencePolicyService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：Agent 编排器，串联 Skill 决策、Tool 执行与防幻觉守卫。
 * 输入：聊天请求。
 * 输出：最终答案与证据引用。
 * 策略：先检索再生成，最后做 citation 一致性校验，不通过则触发安全回复。
 */
@Component
public class AgentOrchestrator {

    private final IntentSkill intentSkill;
    private final RetrievalSkill retrievalSkill;
    private final AnswerSkill answerSkill;
    private final RetrieveResumeContextTool retrieveResumeContextTool;
    private final StarRewriteTool starRewriteTool;
    private final ResumeQaTool resumeQaTool;
    private final CitationVerifierService citationVerifierService;
    private final NoEvidencePolicyService noEvidencePolicyService;
    private final LlmService llmService;

    public AgentOrchestrator(IntentSkill intentSkill,
                             RetrievalSkill retrievalSkill,
                             AnswerSkill answerSkill,
                             RetrieveResumeContextTool retrieveResumeContextTool,
                             StarRewriteTool starRewriteTool,
                             ResumeQaTool resumeQaTool,
                             CitationVerifierService citationVerifierService,
                             NoEvidencePolicyService noEvidencePolicyService,
                             LlmService llmService) {
        this.intentSkill = intentSkill;
        this.retrievalSkill = retrievalSkill;
        this.answerSkill = answerSkill;
        this.retrieveResumeContextTool = retrieveResumeContextTool;
        this.starRewriteTool = starRewriteTool;
        this.resumeQaTool = resumeQaTool;
        this.citationVerifierService = citationVerifierService;
        this.noEvidencePolicyService = noEvidencePolicyService;
        this.llmService = llmService;
    }

    public AgentResult handle(ChatStreamRequest request) {
        String intent = intentSkill.decideIntent(request.getMessage(), request.getIntentHint());
        boolean shouldRetrieve = retrievalSkill.shouldRetrieve(intent);
        SkillDecision decision = new SkillDecision(intent, shouldRetrieve);

        List<Citation> mergedCitations = new ArrayList<>();
        String retrievalEvidence = "";
        String toolContent = "";
        String selectedTool = ToolNames.NONE;
        double selectedToolConfidence = 0.0d;
        String selectedToolReason = "none";

        if (decision.isShouldRetrieve()) {
            ToolCallResult retrieval = retrieveResumeContextTool.run(request.getMessage(), request.getDocId(), request.getOptions());
            retrievalEvidence = retrieval.getContent();
            mergedCitations.addAll(retrieval.getCitations());
            if (retrievalEvidence == null || retrievalEvidence.isBlank() || retrieval.getCitations().isEmpty()) {
                return new AgentResult(
                        decision,
                        noEvidencePolicyService.noEvidenceReply(intent),
                        List.of(),
                        ToolNames.RETRIEVE,
                        1.0d,
                        "retrieval-empty");
            }
        }

        ToolSelectionDecision toolDecision = llmService.chooseTool(intent, request.getMessage(), decision.isShouldRetrieve());
        selectedTool = toolDecision.getToolName();
        selectedToolConfidence = toolDecision.getConfidence();
        selectedToolReason = toolDecision.getReason();

        // 中文说明：Skill 负责路由决策，Tool 只负责执行对应任务。
        if (ToolNames.STAR_REWRITE.equals(selectedTool)) {
            ToolCallResult rewrite = starRewriteTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = rewrite.getContent();
            mergedCitations.addAll(rewrite.getCitations());
        } else if (ToolNames.RESUME_QA.equals(selectedTool)) {
            ToolCallResult qa = resumeQaTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = qa.getContent();
            mergedCitations.addAll(qa.getCitations());
        } else if (ToolNames.RETRIEVE.equals(selectedTool)) {
            toolContent = retrievalEvidence;
        } else if ("REWRITE".equals(intent)) {
            ToolCallResult rewrite = starRewriteTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = rewrite.getContent();
            mergedCitations.addAll(rewrite.getCitations());
            selectedTool = ToolNames.STAR_REWRITE;
            selectedToolConfidence = Math.max(selectedToolConfidence, 0.7d);
            selectedToolReason = "intent-fallback";
        } else if ("QA".equals(intent) || "MOCK_INTERVIEW".equals(intent)) {
            ToolCallResult qa = resumeQaTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = qa.getContent();
            mergedCitations.addAll(qa.getCitations());
            selectedTool = ToolNames.RESUME_QA;
            selectedToolConfidence = Math.max(selectedToolConfidence, 0.7d);
            selectedToolReason = "intent-fallback";
        }

        String finalAnswer = answerSkill.composeFinalAnswer(intent, toolContent);
        CitationVerifierService.VerificationResult result =
                citationVerifierService.verify(finalAnswer, retrievalEvidence, mergedCitations);
        if (!result.pass() && decision.isShouldRetrieve()) {
            return new AgentResult(decision, noEvidencePolicyService.weakCitationReply(result.overlap()),
                    mergedCitations, selectedTool, selectedToolConfidence, selectedToolReason);
        }
        return new AgentResult(decision, finalAnswer, mergedCitations, selectedTool, selectedToolConfidence, selectedToolReason);
    }

    /**
     * 中文说明：编排结果对象，供聊天服务进行 SSE 输出和持久化。
     */
    public static class AgentResult {
        private final SkillDecision decision;
        private final String answer;
        private final List<Citation> citations;
        private final String selectedTool;
        private final double selectedToolConfidence;
        private final String selectedToolReason;

        public AgentResult(SkillDecision decision,
                           String answer,
                           List<Citation> citations,
                           String selectedTool,
                           double selectedToolConfidence,
                           String selectedToolReason) {
            this.decision = decision;
            this.answer = answer;
            this.citations = citations;
            this.selectedTool = selectedTool;
            this.selectedToolConfidence = selectedToolConfidence;
            this.selectedToolReason = selectedToolReason;
        }

        public SkillDecision getDecision() {
            return decision;
        }

        public String getAnswer() {
            return answer;
        }

        public List<Citation> getCitations() {
            return citations;
        }

        public String getSelectedTool() {
            return selectedTool;
        }

        public double getSelectedToolConfidence() {
            return selectedToolConfidence;
        }

        public String getSelectedToolReason() {
            return selectedToolReason;
        }
    }
}

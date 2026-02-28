package com.example.resumecoach.agent.orchestrator;

import com.example.resumecoach.agent.model.SkillDecision;
import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.model.ToolSelectionDecision;
import com.example.resumecoach.agent.skill.AnswerSkill;
import com.example.resumecoach.agent.skill.IntentSkill;
import com.example.resumecoach.agent.skill.RetrievalSkill;
import com.example.resumecoach.agent.tool.ResumeQaTool;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.agent.tool.StarRewriteTool;
import com.example.resumecoach.agent.tool.ToolNames;
import com.example.resumecoach.ai.service.LlmService;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.guardrail.CitationVerifierService;
import com.example.resumecoach.rag.guardrail.NoEvidencePolicyService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 中文说明：Agent 编排器，串联 Skill 决策、Tool 执行与 Guardrail 校验。
 * 策略：输出完整执行轨迹（检索/工具/参数/校验/耗时），用于可观测与回归分析。
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
        long totalStart = System.currentTimeMillis();
        String intent = intentSkill.decideIntent(request.getMessage(), request.getIntentHint());
        boolean shouldRetrieve = retrievalSkill.shouldRetrieve(intent);
        SkillDecision decision = new SkillDecision(intent, shouldRetrieve);

        List<Citation> mergedCitations = new ArrayList<>();
        String retrievalEvidence = "";
        String toolContent = "";
        String selectedTool = ToolNames.NONE;
        Map<String, Object> selectedToolArguments = new LinkedHashMap<>();
        double selectedToolConfidence = 0.0d;
        String selectedToolReason = "none";
        Map<String, Object> retrievalTrace = new LinkedHashMap<>();
        Map<String, Object> guardrailTrace = new LinkedHashMap<>();

        long retrievalStart = System.currentTimeMillis();
        if (decision.isShouldRetrieve()) {
            RetrieveResumeContextTool.RetrievalExecution retrievalExec =
                    retrieveResumeContextTool.runWithTrace(request.getMessage(), request.getDocId(), request.getOptions());
            ToolCallResult retrieval = retrievalExec.result();
            retrievalTrace.putAll(retrievalExec.trace());

            retrievalEvidence = retrieval.getContent();
            mergedCitations.addAll(retrieval.getCitations());
            if (retrievalEvidence == null || retrievalEvidence.isBlank() || retrieval.getCitations().isEmpty()) {
                guardrailTrace.put("stage", "pre-generate");
                guardrailTrace.put("pass", false);
                guardrailTrace.put("reason", "retrieval-empty");
                return new AgentResult(
                        decision,
                        noEvidencePolicyService.noEvidenceReply(intent),
                        List.of(),
                        ToolNames.RETRIEVE,
                        Map.of(),
                        1.0d,
                        "retrieval-empty",
                        retrievalTrace,
                        guardrailTrace,
                        buildLatency(totalStart, retrievalStart, System.currentTimeMillis(), System.currentTimeMillis()));
            }
        } else {
            retrievalTrace.put("rawQuery", request.getMessage());
            retrievalTrace.put("rewrittenQuery", request.getMessage());
            retrievalTrace.put("multiQueries", List.of());
        }
        long retrievalEnd = System.currentTimeMillis();

        long generationStart = System.currentTimeMillis();
        ToolSelectionDecision toolDecision = llmService.chooseTool(intent, request.getMessage(), decision.isShouldRetrieve());
        selectedTool = toolDecision.getToolName();
        selectedToolArguments.putAll(toolDecision.getArguments());
        selectedToolConfidence = toolDecision.getConfidence();
        selectedToolReason = toolDecision.getReason();

        if (ToolNames.STAR_REWRITE.equals(selectedTool)) {
            String rawText = stringArg(selectedToolArguments, "rawText", request.getMessage());
            ToolCallResult rewrite = starRewriteTool.run(rawText, request.getDocId(), retrievalEvidence);
            toolContent = rewrite.getContent();
            mergedCitations.addAll(rewrite.getCitations());
        } else if (ToolNames.RESUME_QA.equals(selectedTool)) {
            String question = stringArg(selectedToolArguments, "question", request.getMessage());
            ToolCallResult qa = resumeQaTool.run(question, request.getDocId(), retrievalEvidence);
            toolContent = qa.getContent();
            mergedCitations.addAll(qa.getCitations());
        } else if (ToolNames.RETRIEVE.equals(selectedTool)) {
            toolContent = retrievalEvidence;
        } else if ("REWRITE".equals(intent)) {
            ToolCallResult rewrite = starRewriteTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = rewrite.getContent();
            mergedCitations.addAll(rewrite.getCitations());
            selectedTool = ToolNames.STAR_REWRITE;
            selectedToolArguments = new LinkedHashMap<>();
            selectedToolArguments.put("rawText", request.getMessage());
            selectedToolConfidence = Math.max(selectedToolConfidence, 0.7d);
            selectedToolReason = "intent-fallback";
        } else if ("QA".equals(intent) || "MOCK_INTERVIEW".equals(intent)) {
            ToolCallResult qa = resumeQaTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = qa.getContent();
            mergedCitations.addAll(qa.getCitations());
            selectedTool = ToolNames.RESUME_QA;
            selectedToolArguments = new LinkedHashMap<>();
            selectedToolArguments.put("question", request.getMessage());
            selectedToolConfidence = Math.max(selectedToolConfidence, 0.7d);
            selectedToolReason = "intent-fallback";
        }

        String finalAnswer = answerSkill.composeFinalAnswer(intent, toolContent);
        CitationVerifierService.VerificationResult verify =
                citationVerifierService.verify(finalAnswer, retrievalEvidence, mergedCitations);
        guardrailTrace.put("stage", "post-generate");
        guardrailTrace.put("pass", verify.pass());
        guardrailTrace.put("overlap", verify.overlap());
        guardrailTrace.put("reason", verify.reason());
        long generationEnd = System.currentTimeMillis();

        if (!verify.pass() && decision.isShouldRetrieve()) {
            return new AgentResult(
                    decision,
                    noEvidencePolicyService.weakCitationReply(verify.overlap()),
                    mergedCitations,
                    selectedTool,
                    selectedToolArguments,
                    selectedToolConfidence,
                    selectedToolReason,
                    retrievalTrace,
                    guardrailTrace,
                    buildLatency(totalStart, retrievalStart, retrievalEnd, generationEnd));
        }

        return new AgentResult(
                decision,
                finalAnswer,
                mergedCitations,
                selectedTool,
                selectedToolArguments,
                selectedToolConfidence,
                selectedToolReason,
                retrievalTrace,
                guardrailTrace,
                buildLatency(totalStart, retrievalStart, retrievalEnd, generationEnd));
    }

    private Map<String, Object> buildLatency(long totalStart, long retrievalStart, long retrievalEnd, long generationEnd) {
        Map<String, Object> latency = new LinkedHashMap<>();
        latency.put("retrievalMs", Math.max(0, retrievalEnd - retrievalStart));
        latency.put("generationMs", Math.max(0, generationEnd - retrievalEnd));
        latency.put("totalMs", Math.max(0, generationEnd - totalStart));
        return latency;
    }

    private String stringArg(Map<String, Object> arguments, String key, String fallback) {
        if (arguments == null) {
            return fallback;
        }
        Object value = arguments.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            return fallback;
        }
        return String.valueOf(value);
    }

    /**
     * 中文说明：编排结果对象，供聊天服务进行 SSE 输出和持久化。
     */
    public static class AgentResult {
        private final SkillDecision decision;
        private final String answer;
        private final List<Citation> citations;
        private final String selectedTool;
        private final Map<String, Object> selectedToolArguments;
        private final double selectedToolConfidence;
        private final String selectedToolReason;
        private final Map<String, Object> retrievalTrace;
        private final Map<String, Object> guardrailTrace;
        private final Map<String, Object> latency;

        public AgentResult(SkillDecision decision,
                           String answer,
                           List<Citation> citations,
                           String selectedTool,
                           Map<String, Object> selectedToolArguments,
                           double selectedToolConfidence,
                           String selectedToolReason,
                           Map<String, Object> retrievalTrace,
                           Map<String, Object> guardrailTrace,
                           Map<String, Object> latency) {
            this.decision = decision;
            this.answer = answer;
            this.citations = citations;
            this.selectedTool = selectedTool;
            this.selectedToolArguments = selectedToolArguments;
            this.selectedToolConfidence = selectedToolConfidence;
            this.selectedToolReason = selectedToolReason;
            this.retrievalTrace = retrievalTrace;
            this.guardrailTrace = guardrailTrace;
            this.latency = latency;
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

        public Map<String, Object> getSelectedToolArguments() {
            return selectedToolArguments;
        }

        public double getSelectedToolConfidence() {
            return selectedToolConfidence;
        }

        public String getSelectedToolReason() {
            return selectedToolReason;
        }

        public Map<String, Object> getRetrievalTrace() {
            return retrievalTrace;
        }

        public Map<String, Object> getGuardrailTrace() {
            return guardrailTrace;
        }

        public Map<String, Object> getLatency() {
            return latency;
        }
    }
}

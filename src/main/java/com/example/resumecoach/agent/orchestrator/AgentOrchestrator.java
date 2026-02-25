package com.example.resumecoach.agent.orchestrator;

import com.example.resumecoach.agent.model.SkillDecision;
import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.skill.AnswerSkill;
import com.example.resumecoach.agent.skill.IntentSkill;
import com.example.resumecoach.agent.skill.RetrievalSkill;
import com.example.resumecoach.agent.tool.ResumeQaTool;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.agent.tool.StarRewriteTool;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：Agent 编排器，串联 Skill 决策与 Tool 执行。
 * 输入：聊天请求。
 * 输出：最终答案与证据引用。
 * 策略：先判定意图与检索策略，再将检索证据传递给下游工具生成答案。
 */
@Component
public class AgentOrchestrator {

    private final IntentSkill intentSkill;
    private final RetrievalSkill retrievalSkill;
    private final AnswerSkill answerSkill;
    private final RetrieveResumeContextTool retrieveResumeContextTool;
    private final StarRewriteTool starRewriteTool;
    private final ResumeQaTool resumeQaTool;

    public AgentOrchestrator(IntentSkill intentSkill,
                             RetrievalSkill retrievalSkill,
                             AnswerSkill answerSkill,
                             RetrieveResumeContextTool retrieveResumeContextTool,
                             StarRewriteTool starRewriteTool,
                             ResumeQaTool resumeQaTool) {
        this.intentSkill = intentSkill;
        this.retrievalSkill = retrievalSkill;
        this.answerSkill = answerSkill;
        this.retrieveResumeContextTool = retrieveResumeContextTool;
        this.starRewriteTool = starRewriteTool;
        this.resumeQaTool = resumeQaTool;
    }

    public AgentResult handle(ChatStreamRequest request) {
        String intent = intentSkill.decideIntent(request.getMessage(), request.getIntentHint());
        boolean shouldRetrieve = retrievalSkill.shouldRetrieve(intent);
        SkillDecision decision = new SkillDecision(intent, shouldRetrieve);

        List<Citation> mergedCitations = new ArrayList<>();
        String toolContent = "";
        String retrievalEvidence = "";

        if (decision.isShouldRetrieve()) {
            ToolCallResult retrieval = retrieveResumeContextTool.run(request.getMessage(), request.getDocId(), request.getOptions());
            retrievalEvidence = retrieval.getContent();
            mergedCitations.addAll(retrieval.getCitations());
        }

        // 中文说明：Skill 负责决策路由，Tool 只负责执行具体能力。
        if ("REWRITE".equals(decision.getIntent())) {
            ToolCallResult rewrite = starRewriteTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = rewrite.getContent();
            mergedCitations.addAll(rewrite.getCitations());
        } else if ("QA".equals(decision.getIntent()) || "MOCK_INTERVIEW".equals(decision.getIntent())) {
            ToolCallResult qa = resumeQaTool.run(request.getMessage(), request.getDocId(), retrievalEvidence);
            toolContent = qa.getContent();
            mergedCitations.addAll(qa.getCitations());
        }

        String finalAnswer = answerSkill.composeFinalAnswer(decision.getIntent(), toolContent);
        return new AgentResult(decision, finalAnswer, mergedCitations);
    }

    /**
     * 中文说明：编排结果对象，供聊天服务进行 SSE 输出和落库。
     */
    public static class AgentResult {
        private final SkillDecision decision;
        private final String answer;
        private final List<Citation> citations;

        public AgentResult(SkillDecision decision, String answer, List<Citation> citations) {
            this.decision = decision;
            this.answer = answer;
            this.citations = citations;
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
    }
}


package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：答案组装技能。
 * 职责：把 tool 产出的原始内容按照意图包装成最终对用户展示的话术。
 * 策略：保持输出前缀和格式稳定，便于前端展示，也便于后续评测与回归分析。
 */
@Component
public class AnswerSkill {

    /**
     * 中文说明：根据意图对工具结果做最终包装。
     * @param intent 当前意图
     * @param toolContent 工具产出的原始内容
     * @return 最终回答文本
     */
    public String composeFinalAnswer(String intent, String toolContent) {
        if ("REWRITE".equals(intent)) {
            return "以下是基于简历证据的 STAR 改写建议：\n" + toolContent;
        }
        if ("MOCK_INTERVIEW".equals(intent)) {
            return "以下是可用于模拟面试的回答草稿：\n" + toolContent;
        }
        if ("QA".equals(intent)) {
            return "以下是基于证据的回答：\n" + toolContent;
        }
        return "我可以帮你做简历问答、STAR 改写和模拟面试，请告诉我你想优化哪一部分。";
    }
}

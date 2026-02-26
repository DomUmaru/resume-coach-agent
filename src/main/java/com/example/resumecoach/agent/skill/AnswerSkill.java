package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：答案组织技能，按意图输出统一格式。
 * 策略：保持输出框架稳定，便于前端展示和后续评测。
 */
@Component
public class AnswerSkill {

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


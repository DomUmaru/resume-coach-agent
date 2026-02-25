package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：答案组织技能，按意图生成更稳定的输出框架。
 * 策略：当前返回 Demo 级模板；后续可接入 Prompt 模板与引用校验器。
 */
@Component
public class AnswerSkill {

    public String composeFinalAnswer(String intent, String toolContent) {
        if ("REWRITE".equals(intent)) {
            return "以下是基于你简历内容的 STAR 改写建议：\n" + toolContent;
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


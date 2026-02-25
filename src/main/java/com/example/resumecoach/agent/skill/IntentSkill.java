package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：意图识别技能，决定用户问题属于问答、改写或泛聊。
 * 策略：当前采用关键词规则，后续可替换为模型分类器。
 */
@Component
public class IntentSkill {

    public String decideIntent(String message, String intentHint) {
        if (intentHint != null && !intentHint.isBlank()) {
            return intentHint.toUpperCase();
        }
        String text = message == null ? "" : message.toLowerCase();
        if (text.contains("star") || text.contains("改写") || text.contains("润色")) {
            return "REWRITE";
        }
        if (text.contains("面试")) {
            return "MOCK_INTERVIEW";
        }
        if (text.contains("项目") || text.contains("经验") || text.contains("为什么")) {
            return "QA";
        }
        return "CHITCHAT";
    }
}


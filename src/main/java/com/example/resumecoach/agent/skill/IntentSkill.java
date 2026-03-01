package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：意图识别技能。
 * 职责：判断用户当前这句话更像问答、改写、模拟面试还是泛聊。
 * 策略：当前采用轻量关键词规则，便于稳定落地；后续可替换为模型分类器。
 */
@Component
public class IntentSkill {

    /**
     * 中文说明：根据用户消息和可选 hint 决定本轮意图。
     * @param message 用户输入
     * @param intentHint 上层显式传入的意图提示，存在时优先使用
     * @return REWRITE / MOCK_INTERVIEW / QA / CHITCHAT
     */
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

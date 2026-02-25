package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：检索决策技能，判断当前问题是否需要检索简历上下文。
 * 策略：改写、问答、模拟面试默认需要检索，泛聊默认不检索。
 */
@Component
public class RetrievalSkill {

    public boolean shouldRetrieve(String intent) {
        return "REWRITE".equals(intent) || "QA".equals(intent) || "MOCK_INTERVIEW".equals(intent);
    }
}


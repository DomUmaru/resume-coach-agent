package com.example.resumecoach.agent.skill;

import org.springframework.stereotype.Component;

/**
 * 中文说明：检索决策技能。
 * 职责：判断当前意图是否需要先检索简历证据。
 * 策略：改写、问答、模拟面试默认依赖简历证据；泛聊默认不检索。
 */
@Component
public class RetrievalSkill {

    /**
     * 中文说明：根据意图判断是否要走检索链路。
     * @param intent 当前意图
     * @return true 表示先检索证据，false 表示可直接生成或闲聊
     */
    public boolean shouldRetrieve(String intent) {
        return "REWRITE".equals(intent) || "QA".equals(intent) || "MOCK_INTERVIEW".equals(intent);
    }
}

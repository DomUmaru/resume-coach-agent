package com.example.resumecoach.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：Skill 决策结果，描述当前请求应该走哪条业务分支。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SkillDecision {

    private String intent;
    private boolean shouldRetrieve;

}

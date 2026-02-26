package com.example.resumecoach.agent.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：工具选择决策对象。
 * 策略：统一输出工具名、置信度和理由，便于审计与调试。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolSelectionDecision {
    private String toolName;
    private double confidence;
    private String reason;
}


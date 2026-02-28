package com.example.resumecoach.agent.tool;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：工具定义对象。
 * 策略：集中描述工具用途与参数 schema，便于工具注册、提示词生成与审计追踪。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;
    private String inputSchema;
}

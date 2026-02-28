package com.example.resumecoach.agent.tool;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 中文说明：工具注册表。
 * 策略：集中管理可暴露给模型的工具定义，避免工具说明散落在多个类中难以维护。
 */
@Component
public class ToolRegistry {

    private final Map<String, ToolDefinition> definitions;

    public ToolRegistry() {
        List<ToolDefinition> items = List.of(
                new ToolDefinition(
                        ToolNames.RETRIEVE,
                        "检索简历证据，适用于展示证据、限定 section/page 查询或需要先取上下文的场景。",
                        """
                        {
                          "query": "string, required",
                          "section": "SUMMARY|PROJECT|WORK|EDUCATION|SKILL|OTHER, optional",
                          "page": "integer, optional",
                          "chunkType": "PARENT|CHILD|SUMMARY, optional"
                        }
                        """.trim()),
                new ToolDefinition(
                        ToolNames.STAR_REWRITE,
                        "将用户输入改写为 STAR 表达，适用于项目经历润色、简历改写。",
                        """
                        {
                          "rawText": "string, required",
                          "targetRole": "string, optional",
                          "tone": "CONCISE|IMPACTFUL|TECHNICAL, optional"
                        }
                        """.trim()),
                new ToolDefinition(
                        ToolNames.RESUME_QA,
                        "基于检索到的简历证据回答问题，适用于简历问答和模拟面试。",
                        """
                        {
                          "question": "string, required",
                          "focusSection": "SUMMARY|PROJECT|WORK|EDUCATION|SKILL|OTHER, optional"
                        }
                        """.trim()),
                new ToolDefinition(
                        ToolNames.NONE,
                        "不调用工具，适用于泛聊或无需工具即可回答的场景。",
                        "{}"))
        ;
        this.definitions = items.stream().collect(Collectors.toMap(ToolDefinition::getName, item -> item));
    }

    public List<ToolDefinition> list() {
        return definitions.values().stream().toList();
    }

    public ToolDefinition get(String toolName) {
        return definitions.get(toolName);
    }

    public String promptBlock() {
        return list().stream()
                .map(item -> "- " + item.getName() + "："
                        + item.getDescription()
                        + "\n  inputSchema=" + item.getInputSchema())
                .collect(Collectors.joining("\n"));
    }
}

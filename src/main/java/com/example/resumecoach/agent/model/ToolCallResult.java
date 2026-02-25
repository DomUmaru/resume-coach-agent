package com.example.resumecoach.agent.model;

import com.example.resumecoach.rag.context.Citation;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：Tool 执行的统一返回结构。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ToolCallResult {

    private String toolName;
    private String content;
    private List<Citation> citations = new ArrayList<>();

}

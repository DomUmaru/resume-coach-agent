package com.example.resumecoach.resume.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：PDF 单页解析结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParsedPage {
    private int pageNumber;
    private String content;
}


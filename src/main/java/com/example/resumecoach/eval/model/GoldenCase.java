package com.example.resumecoach.eval.model;

import lombok.Data;

import java.util.List;

/**
 * 中文说明：离线评测单条样本。
 * 策略：通过相关 section 和关键词定义“相关性”，避免依赖固定 chunkId。
 */
@Data
public class GoldenCase {
    private String caseId;
    private String query;
    private List<String> relevantSections;
    private List<String> relevantKeywords;
}


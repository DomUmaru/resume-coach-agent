package com.example.resumecoach.eval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：单条评测结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalCaseResult {
    private String caseId;
    private String query;
    private int retrievedCount;
    private int relevantCount;
    private int totalRelevantCount;
    private double hitAtK;
    private double recallAtK;
    private double ndcgAtK;
    private double reciprocalRank;
    private double citationPrecision;
}

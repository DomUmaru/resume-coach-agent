package com.example.resumecoach.eval.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：离线评测汇总结果。
 */
@Data
public class EvalSummaryResponse {
    private int totalCases;
    private double avgHitAtK;
    private double avgMRR;
    private double avgCitationPrecision;
    private List<EvalCaseResult> caseResults = new ArrayList<>();
}


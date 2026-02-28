package com.example.resumecoach.eval.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：评测报告历史条目。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalReportItem {
    private String reportId;
    private String docId;
    private String strategyVersion;
    private int totalCases;
    private double avgHitAtK;
    private double avgRecallAtK;
    private double avgNdcgAtK;
    private double avgMRR;
    private double avgCitationPrecision;
    private String createdAt;
}

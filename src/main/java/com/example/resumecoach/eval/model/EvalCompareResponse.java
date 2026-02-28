package com.example.resumecoach.eval.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：评测报告对比响应。
 */
@Data
public class EvalCompareResponse {
    private EvalReportItem reportA;
    private EvalReportItem reportB;
    private MetricDelta delta = new MetricDelta();
    private String winner;
    private String summary;
    private List<ConfigDiffItem> configDiff = new ArrayList<>();

    @Data
    public static class MetricDelta {
        private double hitAtKDelta;
        private double recallAtKDelta;
        private double ndcgAtKDelta;
        private double mrrDelta;
        private double citationPrecisionDelta;
    }

    @Data
    public static class ConfigDiffItem {
        private String key;
        private String valueA;
        private String valueB;
    }
}

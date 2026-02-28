package com.example.resumecoach.observability.model;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文说明：会话级链路摘要，用于前端调试面板展示。
 */
@Data
public class TraceSummaryResponse {
    private String sessionId;
    private int totalRequests;
    private double avgRetrievalMs;
    private double avgGenerationMs;
    private double avgTotalMs;
    private double p95RetrievalMs;
    private double p95GenerationMs;
    private double p95TotalMs;
    private double guardrailBlockRate;
    private double retrievalTriggerRate;
    private double fallbackRate;
    private double avgCandidateCount;
    private double avgFinalChunkCount;
    private double avgCitationCount;
    private double avgContextCompressedChars;
    private Map<String, Integer> toolDistribution = new LinkedHashMap<>();
    private Map<String, Integer> guardrailReasonDistribution = new LinkedHashMap<>();
    private Map<String, Integer> fusionStrategyDistribution = new LinkedHashMap<>();
}


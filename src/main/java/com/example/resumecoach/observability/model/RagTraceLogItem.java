package com.example.resumecoach.observability.model;

import lombok.Data;

/**
 * 中文说明：RAG 日志返回项。
 */
@Data
public class RagTraceLogItem {
    private String id;
    private String traceId;
    private String sessionId;
    private String rawQuery;
    private String rewrittenQuery;
    private String multiQueryJson;
    private String retrievalJson;
    private String toolJson;
    private String guardrailJson;
    private String latencyJson;
    private String finalCitationsJson;
    private String createdAt;
}


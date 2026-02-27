package com.example.resumecoach.observability.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 中文说明：RAG 全链路追踪日志实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "rag_trace_log")
public class RagTraceLogEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "trace_id", nullable = false, length = 64)
    private String traceId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "raw_query", nullable = false, columnDefinition = "text")
    private String rawQuery;

    @Column(name = "rewritten_query", columnDefinition = "text")
    private String rewrittenQuery;

    @Column(name = "multi_query_json", columnDefinition = "text")
    private String multiQueryJson;

    @Column(name = "retrieval_json", columnDefinition = "text")
    private String retrievalJson;

    @Column(name = "tool_json", columnDefinition = "text")
    private String toolJson;

    @Column(name = "guardrail_json", columnDefinition = "text")
    private String guardrailJson;

    @Column(name = "latency_json", columnDefinition = "text")
    private String latencyJson;

    @Column(name = "final_citations_json", columnDefinition = "text")
    private String finalCitationsJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


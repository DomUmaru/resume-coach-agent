package com.example.resumecoach.eval.model.entity;

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
 * 中文说明：离线评测报告实体。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "eval_report")
public class EvalReportEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "total_cases", nullable = false)
    private Integer totalCases;

    @Column(name = "avg_hit_at_k", nullable = false)
    private Double avgHitAtK;

    @Column(name = "avg_mrr", nullable = false)
    private Double avgMrr;

    @Column(name = "avg_citation_precision", nullable = false)
    private Double avgCitationPrecision;

    @Column(name = "report_json", nullable = false, columnDefinition = "text")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


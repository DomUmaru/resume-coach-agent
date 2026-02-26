package com.example.resumecoach.eval.repository;

import com.example.resumecoach.eval.model.entity.EvalReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * 中文说明：评测报告仓储。
 */
public interface EvalReportRepository extends JpaRepository<EvalReportEntity, String> {

    List<EvalReportEntity> findTop20ByDocIdOrderByCreatedAtDesc(String docId);
}


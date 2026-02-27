package com.example.resumecoach.observability.repository;

import com.example.resumecoach.observability.model.entity.RagTraceLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 中文说明：RAG 链路日志仓储。
 */
public interface RagTraceLogRepository extends JpaRepository<RagTraceLogEntity, String> {

    List<RagTraceLogEntity> findTop50BySessionIdOrderByCreatedAtDesc(String sessionId);

    Optional<RagTraceLogEntity> findFirstByTraceIdOrderByCreatedAtDesc(String traceId);
}


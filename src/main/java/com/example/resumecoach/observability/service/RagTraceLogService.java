package com.example.resumecoach.observability.service;

import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.observability.model.RagTraceLogItem;
import com.example.resumecoach.observability.model.entity.RagTraceLogEntity;
import com.example.resumecoach.observability.repository.RagTraceLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 中文说明：RAG 链路日志服务。
 * 策略：日志写入失败不影响主链路，查询接口优先保证可读性。
 */
@Service
public class RagTraceLogService {

    private final RagTraceLogRepository ragTraceLogRepository;
    private final ObjectMapper objectMapper;

    public RagTraceLogService(RagTraceLogRepository ragTraceLogRepository, ObjectMapper objectMapper) {
        this.ragTraceLogRepository = ragTraceLogRepository;
        this.objectMapper = objectMapper;
    }

    public void save(String traceId,
                     String sessionId,
                     String rawQuery,
                     String rewrittenQuery,
                     List<String> multiQueries,
                     Map<String, Object> retrieval,
                     Map<String, Object> tool,
                     Map<String, Object> guardrail,
                     Map<String, Object> latency,
                     Object finalCitations) {
        try {
            RagTraceLogEntity entity = new RagTraceLogEntity();
            entity.setId(IdGenerator.generate("trace"));
            entity.setTraceId(traceId);
            entity.setSessionId(sessionId);
            entity.setRawQuery(nullSafe(rawQuery));
            entity.setRewrittenQuery(nullSafe(rewrittenQuery));
            entity.setMultiQueryJson(toJson(multiQueries));
            entity.setRetrievalJson(toJson(retrieval));
            entity.setToolJson(toJson(tool));
            entity.setGuardrailJson(toJson(guardrail));
            entity.setLatencyJson(toJson(latency));
            entity.setFinalCitationsJson(toJson(finalCitations));
            ragTraceLogRepository.save(entity);
        } catch (Exception ignored) {
            // 中文说明：可观测日志写入失败不阻断业务主流程。
        }
    }

    public List<RagTraceLogItem> latestBySession(String sessionId) {
        return ragTraceLogRepository.findTop50BySessionIdOrderByCreatedAtDesc(sessionId)
                .stream().map(this::toItem).toList();
    }

    public RagTraceLogItem detailByTraceId(String traceId) {
        return ragTraceLogRepository.findFirstByTraceIdOrderByCreatedAtDesc(traceId)
                .map(this::toItem)
                .orElse(null);
    }

    private RagTraceLogItem toItem(RagTraceLogEntity entity) {
        RagTraceLogItem item = new RagTraceLogItem();
        item.setId(entity.getId());
        item.setTraceId(entity.getTraceId());
        item.setSessionId(entity.getSessionId());
        item.setRawQuery(entity.getRawQuery());
        item.setRewrittenQuery(entity.getRewrittenQuery());
        item.setMultiQueryJson(entity.getMultiQueryJson());
        item.setRetrievalJson(entity.getRetrievalJson());
        item.setToolJson(entity.getToolJson());
        item.setGuardrailJson(entity.getGuardrailJson());
        item.setLatencyJson(entity.getLatencyJson());
        item.setFinalCitationsJson(entity.getFinalCitationsJson());
        item.setCreatedAt(entity.getCreatedAt() == null ? "" : entity.getCreatedAt().toString());
        return item;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}


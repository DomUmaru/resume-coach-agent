package com.example.resumecoach.observability.service;

import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.observability.model.RagTraceLogItem;
import com.example.resumecoach.observability.model.TraceSummaryResponse;
import com.example.resumecoach.observability.model.entity.RagTraceLogEntity;
import com.example.resumecoach.observability.repository.RagTraceLogRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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

    public TraceSummaryResponse summaryBySession(String sessionId) {
        List<RagTraceLogEntity> logs = ragTraceLogRepository.findTop50BySessionIdOrderByCreatedAtDesc(sessionId);
        TraceSummaryResponse summary = new TraceSummaryResponse();
        summary.setSessionId(sessionId);
        summary.setTotalRequests(logs.size());
        if (logs.isEmpty()) {
            return summary;
        }

        double retrievalSum = 0.0d;
        double generationSum = 0.0d;
        double totalSum = 0.0d;
        double candidateCountSum = 0.0d;
        double finalChunkCountSum = 0.0d;
        double citationCountSum = 0.0d;
        double contextCompressedCharsSum = 0.0d;
        int guardrailBlocked = 0;
        int retrievalTriggered = 0;
        int fallbackCount = 0;
        List<Double> retrievalSamples = new ArrayList<>();
        List<Double> generationSamples = new ArrayList<>();
        List<Double> totalSamples = new ArrayList<>();
        Map<String, Long> toolDist = logs.stream()
                .map(log -> parseJsonMap(log.getToolJson()))
                .map(map -> String.valueOf(map.getOrDefault("selectedTool", "unknown")))
                .collect(Collectors.groupingBy(tool -> tool, java.util.LinkedHashMap::new, Collectors.counting()));
        Map<String, Integer> guardrailReasons = new java.util.LinkedHashMap<>();
        Map<String, Integer> fusionStrategies = new java.util.LinkedHashMap<>();

        for (RagTraceLogEntity log : logs) {
            Map<String, Object> latency = parseJsonMap(log.getLatencyJson());
            double retrievalMs = toDouble(latency.get("retrievalMs"));
            double generationMs = toDouble(latency.get("generationMs"));
            double totalMs = toDouble(latency.get("totalMs"));
            retrievalSum += retrievalMs;
            generationSum += generationMs;
            totalSum += totalMs;
            retrievalSamples.add(retrievalMs);
            generationSamples.add(generationMs);
            totalSamples.add(totalMs);

            Map<String, Object> retrieval = parseJsonMap(log.getRetrievalJson());
            candidateCountSum += toDouble(retrieval.get("candidateCount"));
            finalChunkCountSum += toDouble(retrieval.get("finalCount"));
            contextCompressedCharsSum += toDouble(retrieval.get("contextCompressedChars"));
            String fusionStrategy = String.valueOf(retrieval.getOrDefault("fusionStrategy", "unknown"));
            fusionStrategies.merge(fusionStrategy, 1, Integer::sum);

            Map<String, Object> guardrail = parseJsonMap(log.getGuardrailJson());
            Object pass = guardrail.get("pass");
            if (pass instanceof Boolean b && !b) {
                guardrailBlocked++;
            } else if ("false".equals(String.valueOf(pass))) {
                guardrailBlocked++;
            }
            String guardrailReason = String.valueOf(guardrail.getOrDefault("reason", "unknown"));
            guardrailReasons.merge(guardrailReason, 1, Integer::sum);

            Map<String, Object> tool = parseJsonMap(log.getToolJson());
            if (toBoolean(tool.get("shouldRetrieve"))) {
                retrievalTriggered++;
            }
            if ("intent-fallback".equals(String.valueOf(tool.get("selectedToolReason")))) {
                fallbackCount++;
            }

            citationCountSum += parseJsonArraySize(log.getFinalCitationsJson());
        }

        int n = logs.size();
        summary.setAvgRetrievalMs(retrievalSum / n);
        summary.setAvgGenerationMs(generationSum / n);
        summary.setAvgTotalMs(totalSum / n);
        summary.setP95RetrievalMs(percentile(retrievalSamples, 95));
        summary.setP95GenerationMs(percentile(generationSamples, 95));
        summary.setP95TotalMs(percentile(totalSamples, 95));
        summary.setGuardrailBlockRate((double) guardrailBlocked / (double) n);
        summary.setRetrievalTriggerRate((double) retrievalTriggered / (double) n);
        summary.setFallbackRate((double) fallbackCount / (double) n);
        summary.setAvgCandidateCount(candidateCountSum / n);
        summary.setAvgFinalChunkCount(finalChunkCountSum / n);
        summary.setAvgCitationCount(citationCountSum / n);
        summary.setAvgContextCompressedChars(contextCompressedCharsSum / n);

        Map<String, Integer> dist = new java.util.LinkedHashMap<>();
        toolDist.forEach((k, v) -> dist.put(k, v.intValue()));
        summary.setToolDistribution(dist);
        summary.setGuardrailReasonDistribution(guardrailReasons);
        summary.setFusionStrategyDistribution(fusionStrategies);
        return summary;
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

    private Map<String, Object> parseJsonMap(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private double toDouble(Object value) {
        if (value == null) {
            return 0.0d;
        }
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0.0d;
        }
    }

    private int parseJsonArraySize(String json) {
        if (json == null || json.isBlank()) {
            return 0;
        }
        try {
            return objectMapper.readTree(json).isArray() ? objectMapper.readTree(json).size() : 0;
        } catch (Exception ex) {
            return 0;
        }
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return false;
        }
        String normalized = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }

    private double percentile(List<Double> values, int p) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        int rank = (int) Math.ceil((p / 100.0d) * sorted.size());
        int index = Math.min(sorted.size() - 1, Math.max(0, rank - 1));
        return sorted.get(index);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }
}


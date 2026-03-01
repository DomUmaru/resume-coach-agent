package com.example.resumecoach.eval.service;

import com.example.resumecoach.rag.guardrail.GuardrailProperties;
import com.example.resumecoach.rag.retrieval.RetrievalTuningProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文说明：评测策略快照服务。
 * 策略：将关键检索与 guardrail 配置序列化后计算版本摘要，便于离线评测做回归比较。
 */
@Service
public class EvalStrategySnapshotService {

    private final RetrievalTuningProperties retrieval;
    private final GuardrailProperties guardrail;
    private final ObjectMapper objectMapper;
    private final boolean aiEnabled;

    public EvalStrategySnapshotService(RetrievalTuningProperties retrieval,
                                       GuardrailProperties guardrail,
                                       ObjectMapper objectMapper,
                                       @Value("${app.ai.enabled:false}") boolean aiEnabled) {
        this.retrieval = retrieval;
        this.guardrail = guardrail;
        this.objectMapper = objectMapper;
        this.aiEnabled = aiEnabled;
    }

    /**
     * 中文说明：构建当前策略配置快照。
     */
    public StrategySnapshot buildSnapshot() {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("aiEnabled", aiEnabled);
        snapshot.put("retrieval.minTopK", retrieval.getMinTopK());
        snapshot.put("retrieval.maxTopK", retrieval.getMaxTopK());
        snapshot.put("retrieval.defaultTopK", retrieval.getDefaultTopK());
        snapshot.put("retrieval.vectorMinScore", retrieval.getVectorMinScore());
        snapshot.put("retrieval.fusionStrategy", retrieval.getFusionStrategy());
        snapshot.put("retrieval.keywordWeight", retrieval.getKeywordWeight());
        snapshot.put("retrieval.ftsWeight", retrieval.getFtsWeight());
        snapshot.put("retrieval.vectorWeight", retrieval.getVectorWeight());
        snapshot.put("guardrail.enabled", guardrail.isEnabled());
        snapshot.put("guardrail.minEvidenceOverlap", guardrail.getMinEvidenceOverlap());
        snapshot.put("guardrail.noEvidenceRefuse", guardrail.isNoEvidenceRefuse());

        String json = toJson(snapshot);
        String version = digestSha1(json);
        return new StrategySnapshot(version, json);
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String digestSha1(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] digest = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.substring(0, 12);
        } catch (Exception ex) {
            return "unknown";
        }
    }

    /**
     * 中文说明：策略快照对象。
     */
    public record StrategySnapshot(String version, String json) {
    }
}

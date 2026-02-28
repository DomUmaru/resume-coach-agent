package com.example.resumecoach.observability.service;

import com.example.resumecoach.observability.model.TraceSummaryResponse;
import com.example.resumecoach.observability.model.entity.RagTraceLogEntity;
import com.example.resumecoach.observability.repository.RagTraceLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagTraceLogServiceTest {

    @Mock
    private RagTraceLogRepository ragTraceLogRepository;

    private RagTraceLogService ragTraceLogService;

    @BeforeEach
    void setUp() {
        ragTraceLogService = new RagTraceLogService(ragTraceLogRepository, new ObjectMapper());
    }

    @Test
    void shouldAggregateSummaryMetricsBySession() throws Exception {
        RagTraceLogEntity first = buildLog(
                "session-1",
                Map.of("candidateCount", 6, "finalCount", 3, "contextCompressedChars", 120, "fusionStrategy", "RRF"),
                Map.of("selectedTool", "resume_qa_tool", "selectedToolReason", "intent-fallback", "shouldRetrieve", true),
                Map.of("pass", false, "reason", "low overlap"),
                Map.of("retrievalMs", 100, "generationMs", 50, "totalMs", 150),
                List.of(Map.of("chunkId", "c1"), Map.of("chunkId", "c2")));
        RagTraceLogEntity second = buildLog(
                "session-1",
                Map.of("candidateCount", 2, "finalCount", 1, "contextCompressedChars", 60, "fusionStrategy", "WEIGHTED"),
                Map.of("selectedTool", "retrieve_resume_context_tool", "selectedToolReason", "model", "shouldRetrieve", false),
                Map.of("pass", true, "reason", "ok"),
                Map.of("retrievalMs", 200, "generationMs", 150, "totalMs", 350),
                List.of(Map.of("chunkId", "c3")));
        when(ragTraceLogRepository.findTop50BySessionIdOrderByCreatedAtDesc("session-1"))
                .thenReturn(List.of(first, second));

        TraceSummaryResponse summary = ragTraceLogService.summaryBySession("session-1");

        assertThat(summary.getTotalRequests()).isEqualTo(2);
        assertThat(summary.getAvgRetrievalMs()).isEqualTo(150.0d);
        assertThat(summary.getAvgGenerationMs()).isEqualTo(100.0d);
        assertThat(summary.getAvgTotalMs()).isEqualTo(250.0d);
        assertThat(summary.getP95RetrievalMs()).isEqualTo(200.0d);
        assertThat(summary.getP95GenerationMs()).isEqualTo(150.0d);
        assertThat(summary.getP95TotalMs()).isEqualTo(350.0d);
        assertThat(summary.getGuardrailBlockRate()).isEqualTo(0.5d);
        assertThat(summary.getRetrievalTriggerRate()).isEqualTo(0.5d);
        assertThat(summary.getFallbackRate()).isEqualTo(0.5d);
        assertThat(summary.getAvgCandidateCount()).isEqualTo(4.0d);
        assertThat(summary.getAvgFinalChunkCount()).isEqualTo(2.0d);
        assertThat(summary.getAvgCitationCount()).isEqualTo(1.5d);
        assertThat(summary.getAvgContextCompressedChars()).isEqualTo(90.0d);
        assertThat(summary.getToolDistribution()).containsEntry("resume_qa_tool", 1);
        assertThat(summary.getToolDistribution()).containsEntry("retrieve_resume_context_tool", 1);
        assertThat(summary.getGuardrailReasonDistribution()).containsEntry("low overlap", 1);
        assertThat(summary.getFusionStrategyDistribution()).containsEntry("RRF", 1);
        assertThat(summary.getFusionStrategyDistribution()).containsEntry("WEIGHTED", 1);
    }

    private RagTraceLogEntity buildLog(String sessionId,
                                       Map<String, Object> retrieval,
                                       Map<String, Object> tool,
                                       Map<String, Object> guardrail,
                                       Map<String, Object> latency,
                                       Object citations) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        RagTraceLogEntity entity = new RagTraceLogEntity();
        entity.setSessionId(sessionId);
        entity.setRetrievalJson(objectMapper.writeValueAsString(retrieval));
        entity.setToolJson(objectMapper.writeValueAsString(tool));
        entity.setGuardrailJson(objectMapper.writeValueAsString(guardrail));
        entity.setLatencyJson(objectMapper.writeValueAsString(latency));
        entity.setFinalCitationsJson(objectMapper.writeValueAsString(citations));
        return entity;
    }
}

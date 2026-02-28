package com.example.resumecoach.eval.service;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.eval.model.EvalCompareResponse;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.model.entity.EvalReportEntity;
import com.example.resumecoach.eval.repository.EvalReportRepository;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
import com.example.resumecoach.resume.repository.ResumeChunkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfflineEvalServiceTest {

    @Mock
    private RetrieveResumeContextTool retrieveResumeContextTool;
    @Mock
    private ResumeChunkRepository resumeChunkRepository;
    @Mock
    private EvalReportRepository evalReportRepository;
    @Mock
    private EvalStrategySnapshotService evalStrategySnapshotService;

    private OfflineEvalService offlineEvalService;

    @BeforeEach
    void setUp() {
        offlineEvalService = new OfflineEvalService(
                retrieveResumeContextTool,
                resumeChunkRepository,
                new ObjectMapper(),
                evalReportRepository,
                evalStrategySnapshotService);
    }

    @Test
    void shouldCalculateEvalMetricsAndPersistReport() {
        List<ResumeChunkEntity> allChunks = List.of(
                chunk("c1", "doc-1", SectionType.PROJECT, "项目 优化 效果 提升"),
                chunk("c2", "doc-1", SectionType.SKILL, "技能 技术栈 熟悉 掌握"),
                chunk("c3", "doc-1", SectionType.EDUCATION, "教育经历"),
                chunk("c4", "doc-1", SectionType.WORK, "负责 行动 结果 业务"),
                chunk("c5", "doc-1", SectionType.SUMMARY, "技能 总结 掌握")
        );
        Map<String, ResumeChunkEntity> chunkMap = allChunks.stream()
                .collect(java.util.stream.Collectors.toMap(ResumeChunkEntity::getId, item -> item));

        when(resumeChunkRepository.findByDocId("doc-1")).thenReturn(allChunks);
        when(resumeChunkRepository.findAllById(anyList())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<String> ids = invocation.getArgument(0, List.class);
            return ids.stream().map(chunkMap::get).filter(java.util.Objects::nonNull).toList();
        });
        when(retrieveResumeContextTool.run(eq("请总结我的项目经历亮点"), eq("doc-1"), any()))
                .thenReturn(new ToolCallResult("retrieve", "项目证据", List.of(
                        citation("c1", "doc-1", 1, "PROJECT", 0.90d),
                        citation("c3", "doc-1", 2, "EDUCATION", 0.20d)
                )));
        when(retrieveResumeContextTool.run(eq("我有哪些核心技能"), eq("doc-1"), any()))
                .thenReturn(new ToolCallResult("retrieve", "技能证据", List.of(
                        citation("c2", "doc-1", 1, "SKILL", 0.95d),
                        citation("c4", "doc-1", 2, "WORK", 0.30d)
                )));
        when(retrieveResumeContextTool.run(eq("请根据简历生成模拟面试回答"), eq("doc-1"), any()))
                .thenReturn(new ToolCallResult("retrieve", "面试证据", List.of(
                        citation("c1", "doc-1", 1, "PROJECT", 0.88d),
                        citation("c4", "doc-1", 2, "WORK", 0.86d)
                )));
        when(evalStrategySnapshotService.buildSnapshot())
                .thenReturn(new EvalStrategySnapshotService.StrategySnapshot("v-test-001", "{\"strategy\":\"test\"}"));

        EvalSummaryResponse summary = offlineEvalService.evaluate("doc-1");

        assertThat(summary.getTotalCases()).isEqualTo(3);
        assertThat(summary.getAvgHitAtK()).isEqualTo(1.0d);
        assertThat(summary.getAvgRecallAtK()).isCloseTo(2.0d / 3.0d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(summary.getAvgNdcgAtK()).isCloseTo(0.7421d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(summary.getAvgMRR()).isEqualTo(1.0d);
        assertThat(summary.getAvgCitationPrecision()).isCloseTo(2.0d / 3.0d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(summary.getCaseResults()).hasSize(3);

        ArgumentCaptor<EvalReportEntity> reportCaptor = ArgumentCaptor.forClass(EvalReportEntity.class);
        verify(evalReportRepository).save(reportCaptor.capture());
        EvalReportEntity report = reportCaptor.getValue();
        assertThat(report.getDocId()).isEqualTo("doc-1");
        assertThat(report.getStrategyVersion()).isEqualTo("v-test-001");
        assertThat(report.getAvgRecallAtK()).isCloseTo(2.0d / 3.0d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(report.getAvgNdcgAtK()).isCloseTo(0.7421d, org.assertj.core.data.Offset.offset(0.0001d));
    }

    @Test
    void shouldCompareReportsAndGenerateWinnerSummary() {
        EvalReportEntity reportA = report("A", "doc-1", 0.90d, 0.85d, 0.88d, 0.87d, 0.89d,
                "{\"retrieval.fusionStrategy\":\"RRF\",\"retrieval.vectorWeight\":0.4}");
        EvalReportEntity reportB = report("B", "doc-1", 0.80d, 0.75d, 0.78d, 0.77d, 0.79d,
                "{\"retrieval.fusionStrategy\":\"WEIGHTED\",\"retrieval.vectorWeight\":0.6}");
        when(evalReportRepository.findById("report-a")).thenReturn(Optional.of(reportA));
        when(evalReportRepository.findById("report-b")).thenReturn(Optional.of(reportB));

        EvalCompareResponse response = offlineEvalService.compare("report-a", "report-b");

        assertThat(response.getWinner()).isEqualTo("A");
        assertThat(response.getDelta().getHitAtKDelta()).isCloseTo(0.10d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(response.getDelta().getRecallAtKDelta()).isCloseTo(0.10d, org.assertj.core.data.Offset.offset(0.0001d));
        assertThat(response.getSummary()).contains("A 更优");
        assertThat(response.getConfigDiff()).hasSize(2);
        assertThat(response.getConfigDiff().stream().map(EvalCompareResponse.ConfigDiffItem::getKey))
                .contains("retrieval.fusionStrategy", "retrieval.vectorWeight");
    }

    private ResumeChunkEntity chunk(String id, String docId, SectionType section, String content) {
        ResumeChunkEntity entity = new ResumeChunkEntity();
        entity.setId(id);
        entity.setDocId(docId);
        entity.setSection(section);
        entity.setChunkType(ChunkType.CHILD);
        entity.setContent(content);
        entity.setSourcePage(1);
        return entity;
    }

    private Citation citation(String chunkId, String docId, int page, String section, double score) {
        return new Citation(chunkId, docId, page, section, score);
    }

    private EvalReportEntity report(String strategyVersion,
                                    String docId,
                                    double hitAtK,
                                    double recallAtK,
                                    double ndcgAtK,
                                    double mrr,
                                    double citationPrecision,
                                    String configSnapshotJson) {
        EvalReportEntity entity = new EvalReportEntity();
        entity.setId("report-" + strategyVersion.toLowerCase());
        entity.setDocId(docId);
        entity.setStrategyVersion(strategyVersion);
        entity.setTotalCases(3);
        entity.setAvgHitAtK(hitAtK);
        entity.setAvgRecallAtK(recallAtK);
        entity.setAvgNdcgAtK(ndcgAtK);
        entity.setAvgMrr(mrr);
        entity.setAvgCitationPrecision(citationPrecision);
        entity.setConfigSnapshotJson(configSnapshotJson);
        entity.setReportJson(new LinkedHashMap<>(Map.of("strategy", strategyVersion)).toString());
        return entity;
    }
}

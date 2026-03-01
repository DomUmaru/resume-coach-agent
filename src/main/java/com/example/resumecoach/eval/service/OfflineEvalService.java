package com.example.resumecoach.eval.service;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.common.api.ErrorCode;
import com.example.resumecoach.common.exception.BizException;
import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.eval.model.EvalCaseResult;
import com.example.resumecoach.eval.model.EvalCompareResponse;
import com.example.resumecoach.eval.model.EvalReportItem;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.model.GoldenCase;
import com.example.resumecoach.eval.model.entity.EvalReportEntity;
import com.example.resumecoach.eval.repository.EvalReportRepository;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.repository.ResumeChunkRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 中文说明：离线评测服务。
 * 策略：基于黄金集执行检索，输出 Hit@K、Recall@K、nDCG@K、MRR、Citation Precision，用于策略回归比较。
 */
@Service
public class OfflineEvalService {

    private final RetrieveResumeContextTool retrieveResumeContextTool;
    private final ResumeChunkRepository resumeChunkRepository;
    private final ObjectMapper objectMapper;
    private final EvalReportRepository evalReportRepository;
    private final EvalStrategySnapshotService evalStrategySnapshotService;

    public OfflineEvalService(RetrieveResumeContextTool retrieveResumeContextTool,
                              ResumeChunkRepository resumeChunkRepository,
                              ObjectMapper objectMapper,
                              EvalReportRepository evalReportRepository,
                              EvalStrategySnapshotService evalStrategySnapshotService) {
        this.retrieveResumeContextTool = retrieveResumeContextTool;
        this.resumeChunkRepository = resumeChunkRepository;
        this.objectMapper = objectMapper;
        this.evalReportRepository = evalReportRepository;
        this.evalStrategySnapshotService = evalStrategySnapshotService;
    }

    /**
     * 中文说明：针对当前 docId 运行一轮离线评测。
     */
    public EvalSummaryResponse evaluate(String docId) {
        List<GoldenCase> cases = loadGoldenCases();
        List<ResumeChunkEntity> allChunks = resumeChunkRepository.findByDocId(docId);
        List<EvalCaseResult> results = cases.stream()
                .map(item -> evaluateCase(docId, item, allChunks))
                .toList();

        EvalSummaryResponse summary = new EvalSummaryResponse();
        summary.setTotalCases(results.size());
        summary.setCaseResults(results);
        summary.setAvgHitAtK(avg(results.stream().map(EvalCaseResult::getHitAtK).toList()));
        summary.setAvgRecallAtK(avg(results.stream().map(EvalCaseResult::getRecallAtK).toList()));
        summary.setAvgNdcgAtK(avg(results.stream().map(EvalCaseResult::getNdcgAtK).toList()));
        summary.setAvgMRR(avg(results.stream().map(EvalCaseResult::getReciprocalRank).toList()));
        summary.setAvgCitationPrecision(avg(results.stream().map(EvalCaseResult::getCitationPrecision).toList()));
        saveReport(docId, summary);
        return summary;
    }

    public List<EvalReportItem> latestReports(String docId) {
        return evalReportRepository.findTop20ByDocIdOrderByCreatedAtDesc(docId).stream()
                .map(this::toReportItem)
                .toList();
    }

    /**
     * 中文说明：比较两次评测报告的指标和配置差异。
     */
    public EvalCompareResponse compare(String reportIdA, String reportIdB) {
        EvalReportEntity a = evalReportRepository.findById(reportIdA)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "reportIdA 不存在"));
        EvalReportEntity b = evalReportRepository.findById(reportIdB)
                .orElseThrow(() -> new BizException(ErrorCode.BAD_REQUEST, "reportIdB 不存在"));

        EvalCompareResponse response = new EvalCompareResponse();
        response.setReportA(toReportItem(a));
        response.setReportB(toReportItem(b));

        EvalCompareResponse.MetricDelta delta = new EvalCompareResponse.MetricDelta();
        delta.setHitAtKDelta(a.getAvgHitAtK() - b.getAvgHitAtK());
        delta.setRecallAtKDelta(a.getAvgRecallAtK() - b.getAvgRecallAtK());
        delta.setNdcgAtKDelta(a.getAvgNdcgAtK() - b.getAvgNdcgAtK());
        delta.setMrrDelta(a.getAvgMrr() - b.getAvgMrr());
        delta.setCitationPrecisionDelta(a.getAvgCitationPrecision() - b.getAvgCitationPrecision());
        response.setDelta(delta);

        Map<String, Object> mapA = parseSnapshot(a.getConfigSnapshotJson());
        Map<String, Object> mapB = parseSnapshot(b.getConfigSnapshotJson());
        response.setConfigDiff(buildConfigDiff(mapA, mapB));
        fillWinnerAndSummary(response);
        return response;
    }

    private EvalCaseResult evaluateCase(String docId, GoldenCase goldenCase, List<ResumeChunkEntity> allChunks) {
        ChatStreamRequest.Options options = new ChatStreamRequest.Options();
        options.setEnableRewrite(true);
        options.setEnableMultiQuery(true);
        options.setEnableRerank(true);
        options.setEnableVector(true);

        ToolCallResult retrieval = retrieveResumeContextTool.run(goldenCase.getQuery(), docId, options);
        List<Citation> citations = retrieval.getCitations() == null ? List.of() : retrieval.getCitations();
        List<String> chunkIds = citations.stream().map(Citation::getChunkId).toList();
        Map<String, ResumeChunkEntity> chunkMap = resumeChunkRepository.findAllById(chunkIds).stream()
                .collect(Collectors.toMap(ResumeChunkEntity::getId, Function.identity(), (x, y) -> x));

        int relevantCount = 0;
        int firstRelevantRank = -1;
        double dcg = 0.0d;
        for (int i = 0; i < citations.size(); i++) {
            Citation citation = citations.get(i);
            ResumeChunkEntity chunk = chunkMap.get(citation.getChunkId());
            if (isRelevant(citation, chunk, goldenCase)) {
                relevantCount++;
                dcg += 1.0d / log2(i + 2);
                if (firstRelevantRank < 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }

        int totalRelevantCount = (int) allChunks.stream()
                .filter(chunk -> isRelevant(null, chunk, goldenCase))
                .count();
        int retrievedCount = citations.size();
        double hitAtK = relevantCount > 0 ? 1.0d : 0.0d;
        double recallAtK = totalRelevantCount == 0 ? 0.0d : (double) relevantCount / (double) totalRelevantCount;
        double idealDcg = idealDcg(Math.min(totalRelevantCount, retrievedCount));
        double ndcgAtK = idealDcg == 0.0d ? 0.0d : dcg / idealDcg;
        double reciprocalRank = firstRelevantRank > 0 ? 1.0d / firstRelevantRank : 0.0d;
        double citationPrecision = retrievedCount == 0 ? 0.0d : (double) relevantCount / (double) retrievedCount;

        return new EvalCaseResult(
                goldenCase.getCaseId(),
                goldenCase.getQuery(),
                retrievedCount,
                relevantCount,
                totalRelevantCount,
                hitAtK,
                recallAtK,
                ndcgAtK,
                reciprocalRank,
                citationPrecision);
    }

    private boolean isRelevant(Citation citation, ResumeChunkEntity chunk, GoldenCase goldenCase) {
        List<String> sections = safeList(goldenCase.getRelevantSections()).stream()
                .map(String::toUpperCase)
                .toList();
        if (citation != null && !sections.isEmpty() && sections.contains(citation.getSection())) {
            return true;
        }
        if (chunk != null && !sections.isEmpty() && sections.contains(chunk.getSection().name())) {
            return true;
        }
        if (chunk == null || chunk.getContent() == null) {
            return false;
        }
        String content = chunk.getContent().toLowerCase(Locale.ROOT);
        Set<String> keywords = safeList(goldenCase.getRelevantKeywords()).stream()
                .map(k -> k.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        return keywords.stream().anyMatch(content::contains);
    }

    private List<GoldenCase> loadGoldenCases() {
        try {
            ClassPathResource resource = new ClassPathResource("eval/golden-set-sample.json");
            try (InputStream in = resource.getInputStream()) {
                return objectMapper.readValue(in, new TypeReference<>() {
                });
            }
        } catch (Exception ex) {
            return Collections.emptyList();
        }
    }

    private List<String> safeList(List<String> list) {
        return list == null ? List.of() : list;
    }

    private double avg(List<Double> values) {
        if (values == null || values.isEmpty()) {
            return 0.0d;
        }
        double sum = values.stream().mapToDouble(v -> v == null ? 0.0d : v).sum();
        return sum / (double) values.size();
    }

    private EvalReportItem toReportItem(EvalReportEntity item) {
        return new EvalReportItem(
                item.getId(),
                item.getDocId(),
                item.getStrategyVersion(),
                item.getTotalCases(),
                item.getAvgHitAtK(),
                item.getAvgRecallAtK(),
                item.getAvgNdcgAtK(),
                item.getAvgMrr(),
                item.getAvgCitationPrecision(),
                item.getCreatedAt() == null ? "" : item.getCreatedAt().toString());
    }

    private Map<String, Object> parseSnapshot(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<EvalCompareResponse.ConfigDiffItem> buildConfigDiff(Map<String, Object> a, Map<String, Object> b) {
        Set<String> keys = new java.util.HashSet<>();
        keys.addAll(a.keySet());
        keys.addAll(b.keySet());
        return keys.stream()
                .sorted()
                .filter(key -> !Objects.equals(a.get(key), b.get(key)))
                .map(key -> {
                    EvalCompareResponse.ConfigDiffItem item = new EvalCompareResponse.ConfigDiffItem();
                    item.setKey(key);
                    item.setValueA(String.valueOf(a.get(key)));
                    item.setValueB(String.valueOf(b.get(key)));
                    return item;
                })
                .toList();
    }

    private void fillWinnerAndSummary(EvalCompareResponse response) {
        double scoreA = weightedScore(response.getReportA());
        double scoreB = weightedScore(response.getReportB());
        double gap = scoreA - scoreB;

        String winner;
        if (Math.abs(gap) < 0.005d) {
            winner = "tie";
        } else {
            winner = gap > 0 ? "A" : "B";
        }
        response.setWinner(winner);

        String summary;
        if ("tie".equals(winner)) {
            summary = "两组策略综合表现接近，建议重点关注配置差异项并扩大样本再评估。";
        } else {
            String better = "A".equals(winner) ? "A" : "B";
            EvalReportItem w = "A".equals(winner) ? response.getReportA() : response.getReportB();
            summary = "综合得分 " + better + " 更优（Hit@K="
                    + format4(w.getAvgHitAtK())
                    + ", Recall@K=" + format4(w.getAvgRecallAtK())
                    + ", nDCG@K=" + format4(w.getAvgNdcgAtK())
                    + ", MRR=" + format4(w.getAvgMRR())
                    + ", CitationPrecision=" + format4(w.getAvgCitationPrecision())
                    + "）。";
        }
        response.setSummary(summary);
    }

    private double weightedScore(EvalReportItem item) {
        return item.getAvgHitAtK() * 0.20d
                + item.getAvgRecallAtK() * 0.20d
                + item.getAvgNdcgAtK() * 0.20d
                + item.getAvgMRR() * 0.20d
                + item.getAvgCitationPrecision() * 0.20d;
    }

    private String format4(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private void saveReport(String docId, EvalSummaryResponse summary) {
        try {
            EvalStrategySnapshotService.StrategySnapshot snapshot = evalStrategySnapshotService.buildSnapshot();
            EvalReportEntity entity = new EvalReportEntity();
            entity.setId(IdGenerator.generate("eval"));
            entity.setDocId(docId);
            entity.setTotalCases(summary.getTotalCases());
            entity.setAvgHitAtK(summary.getAvgHitAtK());
            entity.setAvgRecallAtK(summary.getAvgRecallAtK());
            entity.setAvgNdcgAtK(summary.getAvgNdcgAtK());
            entity.setAvgMrr(summary.getAvgMRR());
            entity.setAvgCitationPrecision(summary.getAvgCitationPrecision());
            entity.setStrategyVersion(snapshot.version());
            entity.setConfigSnapshotJson(snapshot.json());
            entity.setReportJson(objectMapper.writeValueAsString(summary));
            evalReportRepository.save(entity);
        } catch (Exception ignored) {
            // 中文说明：评测落库失败不影响本次评测结果返回。
        }
    }

    private double idealDcg(int idealRelevantCount) {
        double sum = 0.0d;
        for (int i = 0; i < idealRelevantCount; i++) {
            sum += 1.0d / log2(i + 2);
        }
        return sum;
    }

    private double log2(int n) {
        return Math.log(n) / Math.log(2.0d);
    }
}

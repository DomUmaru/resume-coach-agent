package com.example.resumecoach.eval.service;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.agent.tool.RetrieveResumeContextTool;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.eval.model.EvalCaseResult;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.model.GoldenCase;
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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 中文说明：离线评测服务。
 * 策略：基于黄金集样例执行检索，并计算 Hit@K / MRR / Citation Precision。
 */
@Service
public class OfflineEvalService {

    private final RetrieveResumeContextTool retrieveResumeContextTool;
    private final ResumeChunkRepository resumeChunkRepository;
    private final ObjectMapper objectMapper;

    public OfflineEvalService(RetrieveResumeContextTool retrieveResumeContextTool,
                              ResumeChunkRepository resumeChunkRepository,
                              ObjectMapper objectMapper) {
        this.retrieveResumeContextTool = retrieveResumeContextTool;
        this.resumeChunkRepository = resumeChunkRepository;
        this.objectMapper = objectMapper;
    }

    public EvalSummaryResponse evaluate(String docId) {
        List<GoldenCase> cases = loadGoldenCases();
        List<EvalCaseResult> results = cases.stream().map(item -> evaluateCase(docId, item)).toList();

        EvalSummaryResponse summary = new EvalSummaryResponse();
        summary.setTotalCases(results.size());
        summary.setCaseResults(results);
        summary.setAvgHitAtK(avg(results.stream().map(EvalCaseResult::getHitAtK).toList()));
        summary.setAvgMRR(avg(results.stream().map(EvalCaseResult::getReciprocalRank).toList()));
        summary.setAvgCitationPrecision(avg(results.stream().map(EvalCaseResult::getCitationPrecision).toList()));
        return summary;
    }

    private EvalCaseResult evaluateCase(String docId, GoldenCase goldenCase) {
        ChatStreamRequest.Options options = new ChatStreamRequest.Options();
        options.setEnableRewrite(true);
        options.setEnableMultiQuery(true);
        options.setEnableRerank(true);
        options.setEnableVector(true);

        ToolCallResult retrieval = retrieveResumeContextTool.run(goldenCase.getQuery(), docId, options);
        List<Citation> citations = retrieval.getCitations() == null ? List.of() : retrieval.getCitations();
        List<String> chunkIds = citations.stream().map(Citation::getChunkId).toList();
        Map<String, ResumeChunkEntity> chunkMap = resumeChunkRepository.findAllById(chunkIds).stream()
                .collect(Collectors.toMap(ResumeChunkEntity::getId, Function.identity(), (a, b) -> a));

        int relevantCount = 0;
        int firstRelevantRank = -1;
        for (int i = 0; i < citations.size(); i++) {
            Citation c = citations.get(i);
            ResumeChunkEntity chunk = chunkMap.get(c.getChunkId());
            if (isRelevant(c, chunk, goldenCase)) {
                relevantCount++;
                if (firstRelevantRank < 0) {
                    firstRelevantRank = i + 1;
                }
            }
        }

        int retrievedCount = citations.size();
        double hitAtK = relevantCount > 0 ? 1.0d : 0.0d;
        double rr = firstRelevantRank > 0 ? 1.0d / firstRelevantRank : 0.0d;
        double precision = retrievedCount == 0 ? 0.0d : (double) relevantCount / (double) retrievedCount;
        return new EvalCaseResult(goldenCase.getCaseId(), goldenCase.getQuery(), retrievedCount, relevantCount, hitAtK, rr, precision);
    }

    private boolean isRelevant(Citation citation, ResumeChunkEntity chunk, GoldenCase goldenCase) {
        List<String> sections = safeList(goldenCase.getRelevantSections()).stream()
                .map(String::toUpperCase)
                .toList();
        if (!sections.isEmpty() && sections.contains(citation.getSection())) {
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
}


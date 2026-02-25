package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.query.MultiQueryService;
import com.example.resumecoach.rag.query.QueryRewriteService;
import com.example.resumecoach.rag.ranking.RerankService;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.repository.ResumeChunkRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中文说明：简历上下文检索工具。
 * 策略：按 Rewrite -> MultiQuery -> Hybrid 召回 -> Rerank 的顺序执行。
 */
@Component
public class RetrieveResumeContextTool {

    private final ResumeChunkRepository resumeChunkRepository;
    private final QueryRewriteService queryRewriteService;
    private final MultiQueryService multiQueryService;
    private final RerankService rerankService;

    public RetrieveResumeContextTool(ResumeChunkRepository resumeChunkRepository,
                                     QueryRewriteService queryRewriteService,
                                     MultiQueryService multiQueryService,
                                     RerankService rerankService) {
        this.resumeChunkRepository = resumeChunkRepository;
        this.queryRewriteService = queryRewriteService;
        this.multiQueryService = multiQueryService;
        this.rerankService = rerankService;
    }

    public ToolCallResult run(String query, String docId, ChatStreamRequest.Options options) {
        List<ResumeChunkEntity> allChunks = resumeChunkRepository.findByDocIdOrderBySourcePageAsc(docId);
        if (allChunks.isEmpty()) {
            return new ToolCallResult("retrieve_resume_context_tool", "未检索到相关简历证据。", List.of());
        }

        boolean enableRewrite = options == null || Boolean.TRUE.equals(options.getEnableRewrite());
        boolean enableMultiQuery = options == null || Boolean.TRUE.equals(options.getEnableMultiQuery());
        boolean enableRerank = options == null || Boolean.TRUE.equals(options.getEnableRerank());

        String rewritten = enableRewrite ? queryRewriteService.rewrite(query) : query;
        List<String> queries = enableMultiQuery
                ? multiQueryService.expand(query, rewritten)
                : List.of(rewritten == null ? "" : rewritten);

        Set<String> mergedTokens = new HashSet<>();
        Map<String, ResumeChunkEntity> candidates = new HashMap<>();
        Map<String, Double> rrfScore = new HashMap<>();

        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            Set<String> tokens = tokenize(q);
            mergedTokens.addAll(tokens);

            List<ResumeChunkEntity> keywordTop = allChunks.stream()
                    .sorted((a, b) -> Double.compare(score(tokens, b.getContent()), score(tokens, a.getContent())))
                    .limit(6)
                    .toList();

            List<ResumeChunkEntity> ftsTop = resumeChunkRepository.searchByFts(docId, q, 6);
            addRrf(rrfScore, candidates, keywordTop);
            addRrf(rrfScore, candidates, ftsTop);
        }

        List<ResumeChunkEntity> fused = rrfScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> candidates.get(entry.getKey()))
                .filter(item -> item != null)
                .limit(8)
                .toList();

        List<ResumeChunkEntity> topChunks = enableRerank
                ? rerankService.rerank(fused, mergedTokens, 4)
                : fused.stream().limit(4).toList();

        String merged = topChunks.stream()
                .map(ResumeChunkEntity::getContent)
                .collect(Collectors.joining("\n"));

        List<Citation> citations = topChunks.stream()
                .map(item -> new Citation(
                        item.getId(),
                        item.getDocId(),
                        item.getSourcePage() == null ? 1 : item.getSourcePage(),
                        item.getSection().name(),
                        rerankService.score(item.getContent(), mergedTokens)))
                .toList();

        return new ToolCallResult("retrieve_resume_context_tool", merged, citations);
    }

    private void addRrf(Map<String, Double> scoreMap,
                        Map<String, ResumeChunkEntity> entityMap,
                        List<ResumeChunkEntity> ranking) {
        final int k = 60;
        for (int i = 0; i < ranking.size(); i++) {
            ResumeChunkEntity item = ranking.get(i);
            entityMap.put(item.getId(), item);
            scoreMap.merge(item.getId(), 1.0d / (k + i + 1), Double::sum);
        }
    }

    private double score(Set<String> queryTokens, String content) {
        if (content == null || content.isBlank() || queryTokens.isEmpty()) {
            return 0.0d;
        }
        String normalized = content.toLowerCase(Locale.ROOT);
        long hit = queryTokens.stream().filter(normalized::contains).count();
        return (double) hit / (double) queryTokens.size();
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}


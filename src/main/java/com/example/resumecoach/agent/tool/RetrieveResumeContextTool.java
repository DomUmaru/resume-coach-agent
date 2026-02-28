package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.embedding.EmbeddingService;
import com.example.resumecoach.rag.query.MultiQueryService;
import com.example.resumecoach.rag.query.QueryRewriteService;
import com.example.resumecoach.rag.ranking.RerankService;
import com.example.resumecoach.rag.retrieval.RetrievalTuningProperties;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.repository.ResumeChunkRepository;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中文说明：简历上下文检索工具。
 * 策略：检索命中细粒度 child chunk，但生成阶段回填 parent chunk，兼顾召回精度和上下文完整度。
 */
@Component
public class RetrieveResumeContextTool {

    private final ResumeChunkRepository resumeChunkRepository;
    private final QueryRewriteService queryRewriteService;
    private final MultiQueryService multiQueryService;
    private final RerankService rerankService;
    private final EmbeddingService embeddingService;
    private final RetrievalTuningProperties tuningProperties;

    public RetrieveResumeContextTool(ResumeChunkRepository resumeChunkRepository,
                                     QueryRewriteService queryRewriteService,
                                     MultiQueryService multiQueryService,
                                     RerankService rerankService,
                                     EmbeddingService embeddingService,
                                     RetrievalTuningProperties tuningProperties) {
        this.resumeChunkRepository = resumeChunkRepository;
        this.queryRewriteService = queryRewriteService;
        this.multiQueryService = multiQueryService;
        this.rerankService = rerankService;
        this.embeddingService = embeddingService;
        this.tuningProperties = tuningProperties;
    }

    public ToolCallResult run(String query, String docId, ChatStreamRequest.Options options) {
        return runWithTrace(query, docId, options).result();
    }

    public RetrievalExecution runWithTrace(String query, String docId, ChatStreamRequest.Options options) {
        List<ResumeChunkEntity> childChunks =
                resumeChunkRepository.findByDocIdAndChunkTypeOrderBySourcePageAsc(docId, ChunkType.CHILD);
        if (childChunks.isEmpty()) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("rawQuery", query);
            trace.put("rewrittenQuery", query);
            trace.put("multiQueries", List.of());
            trace.put("candidateCount", 0);
            return new RetrievalExecution(
                    new ToolCallResult(ToolNames.RETRIEVE, "未检索到相关简历证据。", List.of()),
                    trace);
        }

        boolean enableRewrite = options == null || Boolean.TRUE.equals(options.getEnableRewrite());
        boolean enableMultiQuery = options == null || Boolean.TRUE.equals(options.getEnableMultiQuery());
        boolean enableRerank = options == null || Boolean.TRUE.equals(options.getEnableRerank());
        boolean enableVector = options == null || Boolean.TRUE.equals(options.getEnableVector());

        String rewritten = enableRewrite ? queryRewriteService.rewrite(query) : query;
        List<String> queries = enableMultiQuery
                ? multiQueryService.expand(query, rewritten)
                : List.of(rewritten == null ? "" : rewritten);
        int dynamicTopK = decideTopK(query);

        Set<String> mergedTokens = new HashSet<>();
        Map<String, ResumeChunkEntity> candidates = new HashMap<>();
        Map<String, Double> rrfScore = new HashMap<>();
        int ftsTotal = 0;
        int keywordTotal = 0;
        int vectorTotal = 0;

        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            Set<String> tokens = tokenize(q);
            mergedTokens.addAll(tokens);

            List<ResumeChunkEntity> keywordTop = childChunks.stream()
                    .sorted((a, b) -> Double.compare(score(tokens, b.getContent()), score(tokens, a.getContent())))
                    .limit(dynamicTopK + 2L)
                    .toList();
            keywordTotal += keywordTop.size();

            List<ResumeChunkEntity> ftsTop = resumeChunkRepository.searchByFts(docId, q, dynamicTopK + 2);
            ftsTotal += ftsTop.size();

            List<ResumeChunkEntity> vectorTop = enableVector ? searchByVector(childChunks, q, dynamicTopK + 2) : List.of();
            vectorTotal += vectorTop.size();

            addRrf(rrfScore, candidates, keywordTop);
            addRrf(rrfScore, candidates, ftsTop);
            addRrf(rrfScore, candidates, vectorTop);
        }

        List<ResumeChunkEntity> fused = rrfScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> candidates.get(entry.getKey()))
                .filter(item -> item != null)
                .limit(dynamicTopK + 4L)
                .toList();

        List<ResumeChunkEntity> topChunks = enableRerank
                ? rerankService.rerank(fused, mergedTokens, dynamicTopK)
                : fused.stream().limit(dynamicTopK).toList();

        Map<String, ResumeChunkEntity> parentChunkMap = loadParentChunkMap(topChunks);
        String merged = topChunks.stream()
                .map(item -> resolveContextChunk(item, parentChunkMap))
                .map(ResumeChunkEntity::getContent)
                .distinct()
                .collect(Collectors.joining("\n"));

        List<Citation> citations = topChunks.stream()
                .map(item -> new Citation(
                        item.getId(),
                        item.getDocId(),
                        item.getSourcePage() == null ? 1 : item.getSourcePage(),
                        item.getSection().name(),
                        rerankService.score(item.getContent(), mergedTokens)))
                .toList();

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("rawQuery", query);
        trace.put("rewrittenQuery", rewritten);
        trace.put("multiQueries", queries);
        trace.put("dynamicTopK", dynamicTopK);
        trace.put("keywordCandidates", keywordTotal);
        trace.put("ftsCandidates", ftsTotal);
        trace.put("vectorCandidates", vectorTotal);
        trace.put("candidateCount", candidates.size());
        trace.put("fusedCount", fused.size());
        trace.put("finalCount", topChunks.size());
        trace.put("finalChunkIds", topChunks.stream().map(ResumeChunkEntity::getId).toList());
        trace.put("finalParentChunkIds", topChunks.stream()
                .map(item -> resolveContextChunk(item, parentChunkMap).getId())
                .distinct()
                .toList());

        return new RetrievalExecution(new ToolCallResult(ToolNames.RETRIEVE, merged, citations), trace);
    }

    private List<ResumeChunkEntity> searchByVector(List<ResumeChunkEntity> chunks, String query, int topN) {
        float[] queryVector = embeddingService.embed(query);
        String vectorLiteral = embeddingService.serialize(queryVector);
        int dim = embeddingService.dimension(queryVector);
        String docId = chunks.isEmpty() ? "" : chunks.get(0).getDocId();

        try {
            List<ResumeChunkEntity> dbTop =
                    resumeChunkRepository.searchByVectorDistance(docId, vectorLiteral, dim, topN * 2);
            return dbTop.stream()
                    .filter(chunk -> vectorScore(chunk, queryVector) >= tuningProperties.getVectorMinScore())
                    .limit(topN)
                    .toList();
        } catch (Exception ignored) {
            // 中文说明：数据库向量检索不可用时，降级到应用内余弦相似度排序。
            return chunks.stream()
                    .sorted((a, b) -> Double.compare(vectorScore(b, queryVector), vectorScore(a, queryVector)))
                    .filter(chunk -> vectorScore(chunk, queryVector) >= tuningProperties.getVectorMinScore())
                    .limit(topN)
                    .toList();
        }
    }

    private Map<String, ResumeChunkEntity> loadParentChunkMap(List<ResumeChunkEntity> chunks) {
        List<String> parentIds = chunks.stream()
                .map(ResumeChunkEntity::getParentId)
                .filter(parentId -> parentId != null && !parentId.isBlank())
                .distinct()
                .toList();
        if (parentIds.isEmpty()) {
            return Map.of();
        }
        return resumeChunkRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(ResumeChunkEntity::getId, item -> item, (a, b) -> a));
    }

    private ResumeChunkEntity resolveContextChunk(ResumeChunkEntity child, Map<String, ResumeChunkEntity> parentChunkMap) {
        if (child == null) {
            return null;
        }
        if (child.getParentId() == null || child.getParentId().isBlank()) {
            return child;
        }
        ResumeChunkEntity parent = parentChunkMap.get(child.getParentId());
        return parent == null ? child : parent;
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

    private double vectorScore(ResumeChunkEntity chunk, float[] queryVector) {
        float[] chunkVector = embeddingService.deserialize(chunk.getContentEmbedding());
        return embeddingService.cosine(queryVector, chunkVector);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }

    private int decideTopK(String query) {
        Set<String> tokens = tokenize(query);
        int size = tokens.size();
        int candidate = size >= 14 ? tuningProperties.getMaxTopK()
                : size <= 4 ? tuningProperties.getMinTopK()
                : tuningProperties.getDefaultTopK() + 1;
        candidate = Math.max(tuningProperties.getMinTopK(), candidate);
        return Math.min(tuningProperties.getMaxTopK(), candidate);
    }

    /**
     * 中文说明：检索执行结果，包含工具输出与调试追踪信息。
     */
    public record RetrievalExecution(ToolCallResult result, Map<String, Object> trace) {
    }
}

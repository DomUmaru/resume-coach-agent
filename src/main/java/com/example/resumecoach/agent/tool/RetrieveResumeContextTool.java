package com.example.resumecoach.agent.tool;

import com.example.resumecoach.agent.model.ToolCallResult;
import com.example.resumecoach.chat.model.dto.ChatStreamRequest;
import com.example.resumecoach.rag.context.Citation;
import com.example.resumecoach.rag.context.ContextCompressionService;
import com.example.resumecoach.rag.embedding.EmbeddingService;
import com.example.resumecoach.rag.query.MultiQueryService;
import com.example.resumecoach.rag.query.QueryRewriteService;
import com.example.resumecoach.rag.ranking.MmrService;
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
 * 职责：围绕用户问题做 query rewrite、多查询扩展、关键词/FTS/向量混合召回、融合排序、rerank、
 * MMR 去冗余、parent 回填和上下文压缩，最终返回可供回答使用的 evidence 与 citation。
 * 策略：召回层优先命中 child chunk，提高精度；生成层回填 parent chunk，保证上下文完整性。
 */
@Component
public class RetrieveResumeContextTool {

    private final ResumeChunkRepository resumeChunkRepository;
    private final QueryRewriteService queryRewriteService;
    private final MultiQueryService multiQueryService;
    private final RerankService rerankService;
    private final MmrService mmrService;
    private final ContextCompressionService contextCompressionService;
    private final EmbeddingService embeddingService;
    private final RetrievalTuningProperties tuningProperties;

    public RetrieveResumeContextTool(ResumeChunkRepository resumeChunkRepository,
                                     QueryRewriteService queryRewriteService,
                                     MultiQueryService multiQueryService,
                                     RerankService rerankService,
                                     MmrService mmrService,
                                     ContextCompressionService contextCompressionService,
                                     EmbeddingService embeddingService,
                                     RetrievalTuningProperties tuningProperties) {
        this.resumeChunkRepository = resumeChunkRepository;
        this.queryRewriteService = queryRewriteService;
        this.multiQueryService = multiQueryService;
        this.rerankService = rerankService;
        this.mmrService = mmrService;
        this.contextCompressionService = contextCompressionService;
        this.embeddingService = embeddingService;
        this.tuningProperties = tuningProperties;
    }

    public ToolCallResult run(String query, String docId, ChatStreamRequest.Options options) {
        return runWithTrace(query, docId, options).result();
    }

    /**
     * 中文说明：执行一次带 trace 的检索。
     * @param query 用户问题或工具改写后的 query
     * @param docId 当前简历文档 ID
     * @param options 检索选项，包含 rewrite、多查询、rerank、向量检索和过滤条件
     * @return 包含工具结果与调试轨迹的检索执行结果
     */
    public RetrievalExecution runWithTrace(String query, String docId, ChatStreamRequest.Options options) {
        ChatStreamRequest.Filter filter = options == null ? null : options.getFilter();
        List<ResumeChunkEntity> childChunks = applyFilter(
                resumeChunkRepository.findByDocIdAndChunkTypeOrderBySourcePageAsc(docId, ChunkType.CHILD),
                filter);
        if (childChunks.isEmpty()) {
            Map<String, Object> trace = new LinkedHashMap<>();
            trace.put("rawQuery", query);
            trace.put("rewrittenQuery", query);
            trace.put("multiQueries", List.of());
            trace.put("candidateCount", 0);
            trace.put("filter", filterTrace(filter));
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
        String fusionStrategy = normalizeFusionStrategy();

        for (String q : queries) {
            if (q == null || q.isBlank()) {
                continue;
            }
            Set<String> tokens = tokenize(q);
            mergedTokens.addAll(tokens);

            // 三路召回：关键词、数据库 FTS、向量检索；后面再做融合与重排。
            List<ResumeChunkEntity> keywordTop = childChunks.stream()
                    .sorted((a, b) -> Double.compare(score(tokens, b.getContent()), score(tokens, a.getContent())))
                    .limit(dynamicTopK + 2L)
                    .toList();
            keywordTotal += keywordTop.size();

            List<ResumeChunkEntity> ftsTop = applyFilter(
                    resumeChunkRepository.searchByFts(docId, q, dynamicTopK + 2),
                    filter);
            ftsTotal += ftsTop.size();

            List<ResumeChunkEntity> vectorTop = enableVector ? searchByVector(childChunks, q, dynamicTopK + 2) : List.of();
            vectorTotal += vectorTop.size();

            mergeScores(rrfScore, candidates, keywordTop, tuningProperties.getKeywordWeight(), fusionStrategy);
            mergeScores(rrfScore, candidates, ftsTop, tuningProperties.getFtsWeight(), fusionStrategy);
            mergeScores(rrfScore, candidates, vectorTop, tuningProperties.getVectorWeight(), fusionStrategy);
        }

        List<ResumeChunkEntity> fused = rrfScore.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .map(entry -> candidates.get(entry.getKey()))
                .filter(item -> item != null)
                .limit(dynamicTopK + 4L)
                .toList();

        List<ResumeChunkEntity> rerankedChunks = enableRerank
                ? rerankService.rerank(fused, mergedTokens, dynamicTopK)
                : fused.stream().limit(dynamicTopK).toList();
        List<ResumeChunkEntity> topChunks = mmrService.diversify(rerankedChunks, mergedTokens, dynamicTopK);

        // 命中的是 child，但生成时尽量回填 parent，避免把上下文切得太碎。
        Map<String, ResumeChunkEntity> parentChunkMap = loadParentChunkMap(topChunks);
        List<String> parentContexts = topChunks.stream()
                .map(item -> resolveContextChunk(item, parentChunkMap))
                .map(ResumeChunkEntity::getContent)
                .distinct()
                .toList();
        ContextCompressionService.CompressionResult compression =
                contextCompressionService.compress(parentContexts, mergedTokens);
        String merged = compression.content();

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
        trace.put("filter", filterTrace(filter));
        trace.put("fusionStrategy", fusionStrategy);
        trace.put("fusionWeights", fusionWeightsTrace());
        trace.put("dynamicTopK", dynamicTopK);
        trace.put("keywordCandidates", keywordTotal);
        trace.put("ftsCandidates", ftsTotal);
        trace.put("vectorCandidates", vectorTotal);
        trace.put("candidateCount", candidates.size());
        trace.put("fusedCount", fused.size());
        trace.put("rerankedCount", rerankedChunks.size());
        trace.put("mmrCount", topChunks.size());
        trace.put("finalCount", topChunks.size());
        trace.put("finalChunkIds", topChunks.stream().map(ResumeChunkEntity::getId).toList());
        trace.put("finalParentChunkIds", topChunks.stream()
                .map(item -> resolveContextChunk(item, parentChunkMap).getId())
                .distinct()
                .toList());
        trace.put("contextOriginalChars", compression.originalChars());
        trace.put("contextCompressedChars", compression.compressedChars());
        trace.put("contextSelectedSentences", compression.selectedSentenceCount());

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
            // 中文说明：数据库向量检索不可用时，降级到应用内余弦相似度排序，保证主链路不中断。
            return chunks.stream()
                    .sorted((a, b) -> Double.compare(vectorScore(b, queryVector), vectorScore(a, queryVector)))
                    .filter(chunk -> vectorScore(chunk, queryVector) >= tuningProperties.getVectorMinScore())
                    .limit(topN)
                    .toList();
        }
    }

    private List<ResumeChunkEntity> applyFilter(List<ResumeChunkEntity> chunks, ChatStreamRequest.Filter filter) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        if (filter == null) {
            return chunks;
        }
        return chunks.stream()
                .filter(chunk -> filter.getSection() == null || filter.getSection() == chunk.getSection())
                .filter(chunk -> filter.getPage() == null || filter.getPage().equals(chunk.getSourcePage()))
                .filter(chunk -> filter.getChunkType() == null || filter.getChunkType() == chunk.getChunkType())
                .toList();
    }

    private Map<String, Object> filterTrace(ChatStreamRequest.Filter filter) {
        if (filter == null) {
            return Map.of();
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("section", filter.getSection() == null ? "" : filter.getSection().name());
        map.put("page", filter.getPage());
        map.put("chunkType", filter.getChunkType() == null ? "" : filter.getChunkType().name());
        return map;
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

    private void mergeScores(Map<String, Double> scoreMap,
                             Map<String, ResumeChunkEntity> entityMap,
                             List<ResumeChunkEntity> ranking,
                             double weight,
                             String fusionStrategy) {
        if (ranking == null || ranking.isEmpty()) {
            return;
        }
        if ("WEIGHTED".equals(fusionStrategy)) {
            addWeighted(scoreMap, entityMap, ranking, weight);
            return;
        }
        addRrf(scoreMap, entityMap, ranking);
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

    private void addWeighted(Map<String, Double> scoreMap,
                             Map<String, ResumeChunkEntity> entityMap,
                             List<ResumeChunkEntity> ranking,
                             double weight) {
        int n = ranking.size();
        if (n == 0 || weight <= 0.0d) {
            return;
        }
        for (int i = 0; i < ranking.size(); i++) {
            ResumeChunkEntity item = ranking.get(i);
            entityMap.put(item.getId(), item);
            double normalizedRankScore = (double) (n - i) / (double) n;
            scoreMap.merge(item.getId(), normalizedRankScore * weight, Double::sum);
        }
    }

    private String normalizeFusionStrategy() {
        String raw = tuningProperties.getFusionStrategy();
        if (raw == null || raw.isBlank()) {
            return "RRF";
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return "WEIGHTED".equals(normalized) ? "WEIGHTED" : "RRF";
    }

    private Map<String, Object> fusionWeightsTrace() {
        Map<String, Object> weights = new LinkedHashMap<>();
        weights.put("keyword", tuningProperties.getKeywordWeight());
        weights.put("fts", tuningProperties.getFtsWeight());
        weights.put("vector", tuningProperties.getVectorWeight());
        return weights;
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
     * 中文说明：检索执行结果。
     * result 是给上层 tool 调用方直接消费的内容；trace 用于观测、排障和离线分析。
     */
    public record RetrievalExecution(ToolCallResult result, Map<String, Object> trace) {
    }
}

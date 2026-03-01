package com.example.resumecoach.rag.ranking;

import com.example.resumecoach.rag.embedding.EmbeddingService;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 中文说明：MMR 去冗余服务。
 * 策略：在保证 query 相关性的同时，惩罚与已选证据过于相似的片段，减少重复上下文。
 */
@Service
public class MmrService {

    private static final double DEFAULT_LAMBDA = 0.7d;

    private final EmbeddingService embeddingService;
    private final RerankService rerankService;

    public MmrService(EmbeddingService embeddingService, RerankService rerankService) {
        this.embeddingService = embeddingService;
        this.rerankService = rerankService;
    }

    /**
     * 中文说明：从候选结果中选出一组相关但不过度重复的 chunk。
     * @param candidates 候选 chunk
     * @param queryTokens query 分词
     * @param topN 目标数量
     * @return 去冗余后的结果
     */
    public List<ResumeChunkEntity> diversify(List<ResumeChunkEntity> candidates, Set<String> queryTokens, int topN) {
        if (candidates == null || candidates.isEmpty() || topN <= 0) {
            return List.of();
        }

        List<ScoredChunk> pool = candidates.stream()
                .map(chunk -> new ScoredChunk(chunk, rerankService.score(chunk.getContent(), queryTokens)))
                .toList();

        List<ScoredChunk> selected = new ArrayList<>();
        List<ScoredChunk> remaining = new ArrayList<>(pool);

        while (!remaining.isEmpty() && selected.size() < topN) {
            ScoredChunk best = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for (ScoredChunk candidate : remaining) {
                double score = mmrScore(candidate, selected);
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            if (best == null) {
                break;
            }
            selected.add(best);
            remaining.remove(best);
        }

        return selected.stream().map(ScoredChunk::chunk).toList();
    }

    private double mmrScore(ScoredChunk candidate, List<ScoredChunk> selected) {
        double redundancy = selected.stream()
                .mapToDouble(item -> similarity(candidate, item))
                .max()
                .orElse(0.0d);
        return DEFAULT_LAMBDA * candidate.relevance() - (1.0d - DEFAULT_LAMBDA) * redundancy;
    }

    private double similarity(ScoredChunk a, ScoredChunk b) {
        float[] va = embeddingService.embed(a.chunk().getContent());
        float[] vb = embeddingService.embed(b.chunk().getContent());
        return embeddingService.cosine(va, vb);
    }

    private record ScoredChunk(ResumeChunkEntity chunk, double relevance) {
    }
}

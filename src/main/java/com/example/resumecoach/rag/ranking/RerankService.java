package com.example.resumecoach.rag.ranking;

import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 中文说明：轻量重排服务。
 * 策略：基于关键词覆盖率和长度惩罚做二次排序，减少无关长段对结果前列的干扰。
 */
@Service
public class RerankService {

    /**
     * 中文说明：对候选 chunk 做二次重排。
     * @param candidates 候选 chunk 列表
     * @param queryTokens query 分词结果
     * @param topN 保留数量
     * @return 重排后的结果
     */
    public List<ResumeChunkEntity> rerank(List<ResumeChunkEntity> candidates, Set<String> queryTokens, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble((ResumeChunkEntity c) -> score(c.getContent(), queryTokens)).reversed())
                .limit(topN)
                .toList();
    }

    /**
     * 中文说明：计算单个 chunk 的相关性分数。
     * 思路：覆盖更多 query token 的内容得分更高；特别长的内容会被适度惩罚。
     */
    public double score(String content, Set<String> queryTokens) {
        if (content == null || content.isBlank() || queryTokens.isEmpty()) {
            return 0.0d;
        }
        String text = content.toLowerCase(Locale.ROOT);
        long hit = queryTokens.stream().filter(text::contains).count();
        double coverage = (double) hit / (double) queryTokens.size();
        double lengthPenalty = Math.max(0.2d, 1.0d - (text.length() / 2500.0d));
        return coverage * lengthPenalty;
    }
}

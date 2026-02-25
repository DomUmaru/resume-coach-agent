package com.example.resumecoach.rag.ranking;

import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 中文说明：轻量重排服务。
 * 策略：基于关键词覆盖率与短文本偏好进行二次排序，减少无关长段落干扰。
 */
@Service
public class RerankService {

    public List<ResumeChunkEntity> rerank(List<ResumeChunkEntity> candidates, Set<String> queryTokens, int topN) {
        return candidates.stream()
                .sorted(Comparator.comparingDouble((ResumeChunkEntity c) -> score(c.getContent(), queryTokens)).reversed())
                .limit(topN)
                .toList();
    }

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


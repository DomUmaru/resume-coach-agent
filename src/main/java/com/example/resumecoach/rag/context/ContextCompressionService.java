package com.example.resumecoach.rag.context;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 中文说明：上下文压缩服务。
 * 策略：优先保留与查询词重合度高的句子，并在字符预算内拼接，减少低信息密度上下文。
 */
@Service
public class ContextCompressionService {

    private final int maxChars;
    private final int fallbackPerSegmentChars;

    public ContextCompressionService(@Value("${app.rag.context.max-chars:1600}") int maxChars,
                                     @Value("${app.rag.context.fallback-per-segment-chars:220}") int fallbackPerSegmentChars) {
        this.maxChars = maxChars;
        this.fallbackPerSegmentChars = fallbackPerSegmentChars;
    }

    /**
     * 中文说明：从若干原始 context segment 中压缩出一段更短、信息密度更高的上下文。
     */
    public CompressionResult compress(List<String> segments, Set<String> queryTokens) {
        if (segments == null || segments.isEmpty()) {
            return new CompressionResult("", 0, 0, 0);
        }

        List<RankedSentence> ranked = new ArrayList<>();
        for (String segment : segments) {
            if (segment == null || segment.isBlank()) {
                continue;
            }
            List<String> sentences = splitSentences(segment);
            if (sentences.isEmpty()) {
                String fallback = truncate(segment.trim(), fallbackPerSegmentChars);
                if (!fallback.isBlank()) {
                    ranked.add(new RankedSentence(fallback, score(fallback, queryTokens), 0));
                }
                continue;
            }
            for (int i = 0; i < sentences.size(); i++) {
                String sentence = sentences.get(i).trim();
                if (sentence.isBlank()) {
                    continue;
                }
                ranked.add(new RankedSentence(sentence, score(sentence, queryTokens), i));
            }
        }

        ranked.sort(Comparator
                .comparingDouble(RankedSentence::score).reversed()
                .thenComparingInt(RankedSentence::order));

        LinkedHashSet<String> selected = new LinkedHashSet<>();
        int used = 0;
        for (RankedSentence item : ranked) {
            String sentence = item.text();
            if (selected.contains(sentence)) {
                continue;
            }
            int additional = sentence.length() + (selected.isEmpty() ? 0 : 1);
            if (used + additional > maxChars) {
                continue;
            }
            selected.add(sentence);
            used += additional;
        }

        if (selected.isEmpty()) {
            String fallback = truncate(firstNonBlank(segments), Math.min(maxChars, fallbackPerSegmentChars));
            if (!fallback.isBlank()) {
                selected.add(fallback);
                used = fallback.length();
            }
        }

        String compressed = String.join("\n", selected);
        int originalChars = segments.stream().filter(item -> item != null).mapToInt(String::length).sum();
        return new CompressionResult(compressed, originalChars, compressed.length(), selected.size());
    }

    private List<String> splitSentences(String text) {
        return Arrays.stream(text.split("(?<=[。！？；.!?;])|\\n"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private double score(String sentence, Set<String> queryTokens) {
        if (sentence == null || sentence.isBlank()) {
            return 0.0d;
        }
        if (queryTokens == null || queryTokens.isEmpty()) {
            return 0.1d;
        }
        String normalized = sentence.toLowerCase(Locale.ROOT);
        long hits = queryTokens.stream().filter(normalized::contains).count();
        double coverage = (double) hits / (double) queryTokens.size();
        double densityBonus = Math.min(0.2d, sentence.length() / 400.0d);
        return coverage + densityBonus;
    }

    private String truncate(String text, int limit) {
        if (text == null) {
            return "";
        }
        if (text.length() <= limit) {
            return text;
        }
        return text.substring(0, limit);
    }

    private String firstNonBlank(List<String> segments) {
        return segments.stream().filter(item -> item != null && !item.isBlank()).findFirst().orElse("");
    }

    /**
     * 中文说明：压缩结果对象，便于 trace 记录压缩前后规模变化。
     */
    public record CompressionResult(String content, int originalChars, int compressedChars, int selectedSentenceCount) {
    }

    private record RankedSentence(String text, double score, int order) {
    }
}

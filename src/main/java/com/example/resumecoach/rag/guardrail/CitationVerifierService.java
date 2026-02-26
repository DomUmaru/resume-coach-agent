package com.example.resumecoach.rag.guardrail;

import com.example.resumecoach.rag.context.Citation;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 中文说明：Citation 一致性校验服务。
 * 策略：使用答案与证据文本的词项重叠度做轻量校验，快速筛掉证据不足回答。
 */
@Service
public class CitationVerifierService {

    private static final Set<String> STOP_WORDS = Set.of(
            "的", "了", "和", "与", "在", "是", "对", "及", "并", "为", "请", "你", "我",
            "a", "an", "the", "to", "of", "and", "in", "is", "are", "for"
    );

    private final GuardrailProperties guardrailProperties;

    public CitationVerifierService(GuardrailProperties guardrailProperties) {
        this.guardrailProperties = guardrailProperties;
    }

    public VerificationResult verify(String answer, String evidenceText, List<Citation> citations) {
        if (!guardrailProperties.isEnabled()) {
            return new VerificationResult(true, 1.0d, "guardrail disabled");
        }
        if (answer == null || answer.isBlank()) {
            return new VerificationResult(false, 0.0d, "empty answer");
        }
        if (evidenceText == null || evidenceText.isBlank()) {
            return new VerificationResult(false, 0.0d, "empty evidence");
        }
        if (citations == null || citations.isEmpty()) {
            return new VerificationResult(false, 0.0d, "missing citations");
        }

        Set<String> answerTokens = tokenize(answer);
        Set<String> evidenceTokens = tokenize(evidenceText);
        if (answerTokens.isEmpty() || evidenceTokens.isEmpty()) {
            return new VerificationResult(false, 0.0d, "no valid tokens");
        }

        long hit = answerTokens.stream().filter(evidenceTokens::contains).count();
        double overlap = (double) hit / (double) answerTokens.size();
        boolean pass = overlap >= guardrailProperties.getMinEvidenceOverlap();
        return new VerificationResult(pass, overlap, pass ? "ok" : "low overlap");
    }

    private Set<String> tokenize(String text) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT)
                        .split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+"))
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .filter(token -> !STOP_WORDS.contains(token))
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 中文说明：校验结果对象。
     */
    public record VerificationResult(boolean pass, double overlap, String reason) {
    }
}


package com.example.resumecoach.rag.embedding;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * 中文说明：统一向量化服务。
 * 策略：优先使用 Spring AI EmbeddingModel，未开启或不可用时使用本地哈希向量降级。
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final boolean aiEnabled;
    private final int fallbackDim;

    public EmbeddingService(ObjectProvider<EmbeddingModel> embeddingModelProvider,
                            @Value("${app.ai.enabled:false}") boolean aiEnabled,
                            @Value("${app.rag.embedding.fallback-dim:128}") int fallbackDim) {
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.aiEnabled = aiEnabled;
        this.fallbackDim = fallbackDim;
    }

    public float[] embed(String text) {
        String safeText = text == null ? "" : text.trim();
        if (aiEnabled && embeddingModel != null) {
            try {
                return embeddingModel.embed(safeText);
            } catch (Exception ignored) {
                // 中文说明：模型调用失败时自动降级到本地向量，保证主链路不中断。
            }
        }
        return hashEmbedding(safeText, fallbackDim);
    }

    public String serialize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return "[]";
        }
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.6f", vector[i]));
        }
        sb.append(']');
        return sb.toString();
    }

    public int dimension(float[] vector) {
        return vector == null ? 0 : vector.length;
    }

    public float[] deserialize(String vectorText) {
        if (vectorText == null || vectorText.isBlank() || "[]".equals(vectorText.trim())) {
            return new float[0];
        }
        String body = vectorText.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] arr = body.split(",");
        float[] result = new float[arr.length];
        for (int i = 0; i < arr.length; i++) {
            try {
                result[i] = Float.parseFloat(arr[i].trim());
            } catch (NumberFormatException ex) {
                result[i] = 0.0f;
            }
        }
        return result;
    }

    public double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0 || a.length != b.length) {
            return 0.0d;
        }
        double dot = 0.0d;
        double na = 0.0d;
        double nb = 0.0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0.0d || nb == 0.0d) {
            return 0.0d;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private float[] hashEmbedding(String text, int dim) {
        float[] vector = new float[dim];
        if (text == null || text.isBlank()) {
            return vector;
        }
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{IsAlphabetic}\\p{IsDigit}\\u4e00-\\u9fa5]+");
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            int idx = Math.abs(token.hashCode()) % dim;
            vector[idx] += 1.0f;
        }
        float norm = 0.0f;
        for (float v : vector) {
            norm += v * v;
        }
        if (norm > 0.0f) {
            float n = (float) Math.sqrt(norm);
            for (int i = 0; i < vector.length; i++) {
                vector[i] = vector[i] / n;
            }
        }
        return vector;
    }
}

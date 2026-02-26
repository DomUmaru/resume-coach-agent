package com.example.resumecoach.rag.retrieval;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 中文说明：检索调优配置。
 * 策略：将阈值和 TopK 变为可配置项，便于不同简历场景快速调参。
 */
@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RetrievalTuningProperties {

    private int minTopK = 3;
    private int maxTopK = 8;
    private int defaultTopK = 4;
    private double vectorMinScore = 0.25d;

    public int getMinTopK() {
        return minTopK;
    }

    public void setMinTopK(int minTopK) {
        this.minTopK = minTopK;
    }

    public int getMaxTopK() {
        return maxTopK;
    }

    public void setMaxTopK(int maxTopK) {
        this.maxTopK = maxTopK;
    }

    public int getDefaultTopK() {
        return defaultTopK;
    }

    public void setDefaultTopK(int defaultTopK) {
        this.defaultTopK = defaultTopK;
    }

    public double getVectorMinScore() {
        return vectorMinScore;
    }

    public void setVectorMinScore(double vectorMinScore) {
        this.vectorMinScore = vectorMinScore;
    }
}


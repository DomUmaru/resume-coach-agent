package com.example.resumecoach.rag.guardrail;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 中文说明：防幻觉策略配置。
 * 策略：将阈值与开关配置化，便于在不同演示场景下快速调整严格度。
 */
@ConfigurationProperties(prefix = "app.rag.guardrail")
public class GuardrailProperties {

    private boolean enabled = true;
    private double minEvidenceOverlap = 0.18d;
    private boolean noEvidenceRefuse = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getMinEvidenceOverlap() {
        return minEvidenceOverlap;
    }

    public void setMinEvidenceOverlap(double minEvidenceOverlap) {
        this.minEvidenceOverlap = minEvidenceOverlap;
    }

    public boolean isNoEvidenceRefuse() {
        return noEvidenceRefuse;
    }

    public void setNoEvidenceRefuse(boolean noEvidenceRefuse) {
        this.noEvidenceRefuse = noEvidenceRefuse;
    }
}


package com.example.resumecoach.eval.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 中文说明：评测报告对比请求。
 */
@Data
public class EvalCompareRequest {
    @NotBlank
    private String reportIdA;
    @NotBlank
    private String reportIdB;
}


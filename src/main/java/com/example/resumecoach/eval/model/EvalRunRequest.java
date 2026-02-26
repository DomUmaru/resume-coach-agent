package com.example.resumecoach.eval.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 中文说明：离线评测请求。
 */
@Data
public class EvalRunRequest {
    @NotBlank
    private String docId;
}


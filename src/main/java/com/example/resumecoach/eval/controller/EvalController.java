package com.example.resumecoach.eval.controller;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.eval.model.EvalRunRequest;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.service.OfflineEvalService;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 中文说明：离线评测接口。
 */
@RestController
@RequestMapping("/api/eval")
@Validated
public class EvalController {

    private final OfflineEvalService offlineEvalService;

    public EvalController(OfflineEvalService offlineEvalService) {
        this.offlineEvalService = offlineEvalService;
    }

    @PostMapping("/offline")
    public ApiResponse<EvalSummaryResponse> runOfflineEval(@RequestBody @Valid EvalRunRequest request) {
        EvalSummaryResponse summary = offlineEvalService.evaluate(request.getDocId());
        return ApiResponse.ok(summary, TraceContext.getTraceId());
    }
}


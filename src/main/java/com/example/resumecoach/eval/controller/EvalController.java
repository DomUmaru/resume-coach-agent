package com.example.resumecoach.eval.controller;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.eval.model.EvalCompareRequest;
import com.example.resumecoach.eval.model.EvalCompareResponse;
import com.example.resumecoach.eval.model.EvalReportItem;
import com.example.resumecoach.eval.model.EvalRunRequest;
import com.example.resumecoach.eval.model.EvalSummaryResponse;
import com.example.resumecoach.eval.service.OfflineEvalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping("/reports/{docId}")
    public ApiResponse<java.util.List<EvalReportItem>> latestReports(@PathVariable("docId") @NotBlank String docId) {
        return ApiResponse.ok(offlineEvalService.latestReports(docId), TraceContext.getTraceId());
    }

    @PostMapping("/compare")
    public ApiResponse<EvalCompareResponse> compare(@RequestBody @Valid EvalCompareRequest request) {
        EvalCompareResponse response = offlineEvalService.compare(request.getReportIdA(), request.getReportIdB());
        return ApiResponse.ok(response, TraceContext.getTraceId());
    }
}

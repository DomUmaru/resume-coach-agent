package com.example.resumecoach.observability.controller;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.observability.model.RagTraceLogItem;
import com.example.resumecoach.observability.model.TraceSummaryResponse;
import com.example.resumecoach.observability.service.RagTraceLogService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 中文说明：链路追踪查询接口。
 */
@RestController
@RequestMapping("/api/trace")
@Validated
public class TraceController {

    private final RagTraceLogService ragTraceLogService;

    public TraceController(RagTraceLogService ragTraceLogService) {
        this.ragTraceLogService = ragTraceLogService;
    }

    /**
     * 中文说明：查询指定 session 最近的 trace 列表。
     */
    @GetMapping("/{sessionId}")
    public ApiResponse<List<RagTraceLogItem>> latest(@PathVariable("sessionId") @NotBlank String sessionId) {
        return ApiResponse.ok(ragTraceLogService.latestBySession(sessionId), TraceContext.getTraceId());
    }

    /**
     * 中文说明：根据 traceId 查询一条完整 trace 明细。
     */
    @GetMapping("/detail/{traceId}")
    public ApiResponse<RagTraceLogItem> detail(@PathVariable("traceId") @NotBlank String traceId) {
        return ApiResponse.ok(ragTraceLogService.detailByTraceId(traceId), TraceContext.getTraceId());
    }

    /**
     * 中文说明：聚合指定 session 最近一批 trace，返回摘要统计结果。
     */
    @GetMapping("/summary/{sessionId}")
    public ApiResponse<TraceSummaryResponse> summary(@PathVariable("sessionId") @NotBlank String sessionId) {
        return ApiResponse.ok(ragTraceLogService.summaryBySession(sessionId), TraceContext.getTraceId());
    }
}

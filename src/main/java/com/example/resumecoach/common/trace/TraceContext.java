package com.example.resumecoach.common.trace;

import java.util.UUID;

/**
 * 中文说明：请求链路追踪上下文。
 * 输入：HTTP 请求进入时的 traceId（如果没有则自动生成）。
 * 输出：提供统一 traceId 给日志与接口响应使用。
 */
public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID_HOLDER = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void setTraceId(String traceId) {
        TRACE_ID_HOLDER.set(traceId);
    }

    public static String getTraceId() {
        String traceId = TRACE_ID_HOLDER.get();
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString().replace("-", "");
            TRACE_ID_HOLDER.set(traceId);
        }
        return traceId;
    }

    public static void clear() {
        TRACE_ID_HOLDER.remove();
    }
}


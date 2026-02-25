package com.example.resumecoach.common.api;

import lombok.Getter;
import lombok.Setter;

/**
 * 中文说明：统一接口响应结构，便于前后端和日志系统保持一致解析。
 * 输入：业务数据与错误信息。
 * 输出：固定字段 code/message/data/traceId。
 * 策略：Demo 阶段统一返回结构，减少前端分支判断成本。
 */
@Getter
@Setter
public class ApiResponse<T> {

    private int code;
    private String message;
    private T data;
    private String traceId;

    public static <T> ApiResponse<T> ok(T data, String traceId) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(0);
        response.setMessage("ok");
        response.setData(data);
        response.setTraceId(traceId);
        return response;
    }

    public static <T> ApiResponse<T> fail(int code, String message, String traceId) {
        ApiResponse<T> response = new ApiResponse<>();
        response.setCode(code);
        response.setMessage(message);
        response.setTraceId(traceId);
        return response;
    }

}

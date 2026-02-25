package com.example.resumecoach.common.api;

import lombok.Getter;

/**
 * 中文说明：统一错误码定义，避免魔法数字散落在业务代码中。
 */
@Getter
public enum ErrorCode {
    BAD_REQUEST(4001, "请求参数错误"),
    UNSUPPORTED_FILE_TYPE(4002, "文件格式不支持"),
    DOCUMENT_NOT_FOUND(4003, "文档不存在"),
    VECTORIZE_FAILED(5001, "向量化失败"),
    RETRIEVAL_FAILED(5002, "检索失败"),
    MODEL_CALL_FAILED(5003, "模型调用失败"),
    STREAM_INTERRUPTED(5004, "流式输出中断"),
    INTERNAL_ERROR(5000, "系统内部错误");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

}


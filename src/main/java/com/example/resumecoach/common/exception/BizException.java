package com.example.resumecoach.common.exception;

import com.example.resumecoach.common.api.ErrorCode;
import lombok.Getter;

/**
 * 中文说明：业务异常，携带统一错误码。
 * 策略：业务层遇到可预期失败时抛出该异常，由全局异常处理器统一转换响应。
 */
@Getter
public class BizException extends RuntimeException {

    private final ErrorCode errorCode;

    public BizException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BizException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}


package com.example.resumecoach.common.exception;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.api.ErrorCode;
import com.example.resumecoach.common.trace.TraceContext;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 中文说明：统一异常处理入口，确保所有错误响应结构一致。
 * 策略：业务异常返回明确错误码；未知异常返回内部错误码，避免泄露实现细节。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ResponseEntity<ApiResponse<Void>> handleBizException(BizException ex) {
        String traceId = TraceContext.getTraceId();
        ApiResponse<Void> body = ApiResponse.fail(ex.getErrorCode().getCode(), ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception ex) {
        String traceId = TraceContext.getTraceId();
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleInternalError(Exception ex) {
        String traceId = TraceContext.getTraceId();
        ApiResponse<Void> body = ApiResponse.fail(ErrorCode.INTERNAL_ERROR.getCode(), ErrorCode.INTERNAL_ERROR.getMessage(), traceId);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }
}


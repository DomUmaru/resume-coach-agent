package com.example.resumecoach.chat.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 中文说明：流式聊天请求体。
 */
@Data
public class ChatStreamRequest {

    @NotBlank
    private String sessionId;
    @NotBlank
    private String userId;
    @NotBlank
    private String docId;
    @NotBlank
    private String message;
    private String intentHint;
    @Valid
    private Options options;

    /**
     * 中文说明：请求开关项，控制检索与重排等策略的启用状态。
     */
    @Data
    public static class Options {
        private Boolean enableRewrite = Boolean.TRUE;
        private Boolean enableMultiQuery = Boolean.TRUE;
        private Boolean enableRerank = Boolean.TRUE;
        private Boolean enableVector = Boolean.TRUE;
    }
}

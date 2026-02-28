package com.example.resumecoach.chat.model.dto;

import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
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
        @Valid
        private Filter filter;
    }

    /**
     * 中文说明：检索过滤条件。
     * 策略：优先服务 Demo 中“仅看项目经历/某页内容”等精确检索场景。
     */
    @Data
    public static class Filter {
        private SectionType section;
        private Integer page;
        private ChunkType chunkType;
    }
}

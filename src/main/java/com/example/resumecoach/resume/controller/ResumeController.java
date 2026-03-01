package com.example.resumecoach.resume.controller;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.resume.model.dto.UploadResumeResponse;
import com.example.resumecoach.resume.service.ResumeIngestionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 中文说明：简历上传接口。
 * 输入：PDF 文件、userId、可选 docName。
 * 输出：统一响应包装下的 docId 与处理状态。
 * 策略：Controller 只负责参数接收与响应封装，实际入库流程全部下沉到 ResumeIngestionService。
 */
@RestController
@RequestMapping("/api/resume")
@Validated
public class ResumeController {

    private final ResumeIngestionService resumeIngestionService;

    public ResumeController(ResumeIngestionService resumeIngestionService) {
        this.resumeIngestionService = resumeIngestionService;
    }

    /**
     * 中文说明：接收简历上传请求并触发入库主流程。
     * @param file 上传的 PDF 文件
     * @param userId 上传用户 ID
     * @param docName 用户自定义文档名，可为空
     * @return 包含 docId、状态和 traceId 的统一响应
     */
    @PostMapping("/upload")
    public ApiResponse<UploadResumeResponse> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("userId") @NotBlank String userId,
                                                    @RequestParam(value = "docName", required = false) String docName) {
        UploadResumeResponse response = resumeIngestionService.ingest(file, userId, docName);
        return ApiResponse.ok(response, TraceContext.getTraceId());
    }
}

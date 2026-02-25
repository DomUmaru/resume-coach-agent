package com.example.resumecoach.resume.controller;

import com.example.resumecoach.common.api.ApiResponse;
import com.example.resumecoach.common.trace.TraceContext;
import com.example.resumecoach.resume.model.dto.UploadResumeResponse;
import com.example.resumecoach.resume.service.ResumeIngestionService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 中文说明：简历上传接口。
 * 输入：PDF 文件、userId、可选 docName。
 * 输出：docId 与处理状态。
 * 策略：Controller 仅做参数接收和响应封装，业务逻辑全部下沉到 Service。
 */
@RestController
@RequestMapping("/api/resume")
@Validated
public class ResumeController {

    private final ResumeIngestionService resumeIngestionService;

    public ResumeController(ResumeIngestionService resumeIngestionService) {
        this.resumeIngestionService = resumeIngestionService;
    }

    @PostMapping("/upload")
    public ApiResponse<UploadResumeResponse> upload(@RequestParam("file") MultipartFile file,
                                                    @RequestParam("userId") @NotBlank String userId,
                                                    @RequestParam(value = "docName", required = false) String docName) {
        UploadResumeResponse response = resumeIngestionService.ingest(file, userId, docName);
        return ApiResponse.ok(response, TraceContext.getTraceId());
    }
}


package com.example.resumecoach.resume.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：上传简历接口响应体。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UploadResumeResponse {

    private String docId;
    private String status;
}

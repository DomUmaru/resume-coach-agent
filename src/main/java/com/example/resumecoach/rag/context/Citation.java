package com.example.resumecoach.rag.context;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 中文说明：统一证据引用结构，前端可直接展示来源信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Citation {

    private String chunkId;
    private String docId;
    private int page;
    private String section;
    private double score;

}

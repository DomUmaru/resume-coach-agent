package com.example.resumecoach.resume.model.entity;

import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 中文说明：简历分块实体，用于检索与证据引用。
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "resume_chunk")
public class ResumeChunkEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "doc_id", nullable = false, length = 64)
    private String docId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "parent_id", length = 64)
    private String parentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", nullable = false, length = 32)
    private SectionType section;

    @Enumerated(EnumType.STRING)
    @Column(name = "chunk_type", nullable = false, length = 32)
    private ChunkType chunkType;

    @Column(name = "source_page")
    private Integer sourcePage;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}


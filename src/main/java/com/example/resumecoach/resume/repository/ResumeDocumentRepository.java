package com.example.resumecoach.resume.repository;

import com.example.resumecoach.resume.model.entity.ResumeDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 中文说明：简历文档仓储。
 */
public interface ResumeDocumentRepository extends JpaRepository<ResumeDocumentEntity, String> {

    Optional<ResumeDocumentEntity> findByIdAndUserId(String id, String userId);
}


package com.example.resumecoach.resume.repository;

import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * 中文说明：简历分块仓储。
 */
public interface ResumeChunkRepository extends JpaRepository<ResumeChunkEntity, String> {

    List<ResumeChunkEntity> findByDocId(String docId);

    List<ResumeChunkEntity> findByDocIdOrderBySourcePageAsc(String docId);

    void deleteByDocId(String docId);

    /**
     * 中文说明：基于 PostgreSQL FTS 的关键词召回。
     * 策略：使用 websearch_to_tsquery 提升对自然语言查询的容错能力。
     */
    @Query(value = """
            SELECT *
            FROM resume_chunk rc
            WHERE rc.doc_id = :docId
              AND to_tsvector('simple', rc.content) @@ websearch_to_tsquery('simple', :query)
            ORDER BY ts_rank(to_tsvector('simple', rc.content), websearch_to_tsquery('simple', :query)) DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<ResumeChunkEntity> searchByFts(@Param("docId") String docId,
                                        @Param("query") String query,
                                        @Param("limit") int limit);
}

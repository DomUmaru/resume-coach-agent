package com.example.resumecoach.resume.service;

import com.example.resumecoach.common.api.ErrorCode;
import com.example.resumecoach.common.exception.BizException;
import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.rag.embedding.EmbeddingService;
import com.example.resumecoach.resume.model.dto.ParsedPage;
import com.example.resumecoach.resume.model.dto.UploadResumeResponse;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.model.entity.ResumeDocumentEntity;
import com.example.resumecoach.resume.model.enumtype.DocumentStatus;
import com.example.resumecoach.resume.repository.ResumeChunkRepository;
import com.example.resumecoach.resume.repository.ResumeDocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 中文说明：简历上传入库总流程服务。
 * 输入：上传的 PDF 文件、用户 ID、可选文档名。
 * 输出：文档 ID 与处理状态。
 * 策略：先写入一条 PROCESSING 状态的主文档记录，再完成解析、切块、向量化与 chunk 落库；
 * 若中途失败，则尽量把主文档状态更新为 FAILED，便于排查问题。
 */
@Service
public class ResumeIngestionService {

    private final ResumeDocumentRepository resumeDocumentRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final PdfParseService pdfParseService;
    private final ResumeChunkingService resumeChunkingService;
    private final EmbeddingService embeddingService;

    public ResumeIngestionService(ResumeDocumentRepository resumeDocumentRepository,
                                  ResumeChunkRepository resumeChunkRepository,
                                  PdfParseService pdfParseService,
                                  ResumeChunkingService resumeChunkingService,
                                  EmbeddingService embeddingService) {
        this.resumeDocumentRepository = resumeDocumentRepository;
        this.resumeChunkRepository = resumeChunkRepository;
        this.pdfParseService = pdfParseService;
        this.resumeChunkingService = resumeChunkingService;
        this.embeddingService = embeddingService;
    }

    /**
     * 中文说明：执行简历上传后的完整入库流程。
     * @param file 上传的 PDF 文件
     * @param userId 上传用户 ID
     * @param docName 用户传入的文档名，可为空
     * @return 包含 docId 和状态的响应对象
     * 异常策略：任意关键步骤失败都会抛出异常，并尽量把主文档状态标记为 FAILED。
     */
    @Transactional
    public UploadResumeResponse ingest(MultipartFile file, String userId, String docName) {
        validateInput(file, userId);

        String docId = IdGenerator.generate("doc");
        ResumeDocumentEntity document = new ResumeDocumentEntity();
        document.setId(docId);
        document.setUserId(userId);
        document.setDocName(resolveDocName(file, docName));
        document.setFilePath(file.getOriginalFilename());
        document.setStatus(DocumentStatus.PROCESSING);
        initializeDocumentTimestamps(document);
        resumeDocumentRepository.save(document);

        try {
            List<ParsedPage> pages = pdfParseService.parseByPage(file);
            List<ResumeChunkEntity> chunks = resumeChunkingService.buildChunks(docId, userId, pages);

            // 中文说明：在上传阶段预计算每个 chunk 的 embedding，避免检索时反复为文档内容做向量化。
            for (ResumeChunkEntity chunk : chunks) {
                float[] vector = embeddingService.embed(chunk.getContent());
                chunk.setContentEmbedding(embeddingService.serialize(vector));
                chunk.setEmbeddingDim(embeddingService.dimension(vector));
            }

            // 中文说明：同一文档的 chunk 采用全量重建方式，先删后写，避免旧数据残留。
            resumeChunkRepository.deleteByDocId(docId);
            resumeChunkRepository.saveAll(chunks);

            document.setStatus(DocumentStatus.COMPLETED);
            touchUpdatedAt(document);
            resumeDocumentRepository.save(document);
            return new UploadResumeResponse(docId, DocumentStatus.COMPLETED.name());
        } catch (Exception ex) {
            // 中文说明：主流程失败时尽量保留 FAILED 状态，便于后续定位是解析失败、分块失败还是落库失败。
            document.setStatus(DocumentStatus.FAILED);
            touchUpdatedAt(document);
            resumeDocumentRepository.save(document);
            throw ex;
        }
    }

    /**
     * 中文说明：校验上传请求的基础合法性。
     * @param file 上传文件
     * @param userId 用户 ID
     * 异常策略：参数不合法时直接抛出业务异常，不进入后续解析流程。
     */
    private void validateInput(MultipartFile file, String userId) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        if (userId == null || userId.isBlank()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "userId 不能为空");
        }
        String fileName = file.getOriginalFilename();
        if (fileName == null || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new BizException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    /**
     * 中文说明：优先使用用户传入的文档名；若未传，则回退为原始文件名。
     * @param file 上传文件
     * @param docName 自定义文档名
     * @return 最终用于入库的文档名
     */
    private String resolveDocName(MultipartFile file, String docName) {
        if (docName != null && !docName.isBlank()) {
            return docName;
        }
        return file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
    }

    /**
     * 中文说明：初始化文档的创建时间与更新时间。
     * @param document 文档实体
     */
    private void initializeDocumentTimestamps(ResumeDocumentEntity document) {
        LocalDateTime now = LocalDateTime.now();
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(now);
        }
        document.setUpdatedAt(now);
    }

    /**
     * 中文说明：刷新文档更新时间；若创建时间缺失则一并补齐。
     * @param document 文档实体
     */
    private void touchUpdatedAt(ResumeDocumentEntity document) {
        if (document.getCreatedAt() == null) {
            document.setCreatedAt(LocalDateTime.now());
        }
        document.setUpdatedAt(LocalDateTime.now());
    }
}

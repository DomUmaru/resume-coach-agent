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

import java.util.List;

/**
 * 中文说明：简历入库流程服务。
 * 输入：PDF 文件、用户标识、文档名称。
 * 输出：文档 ID 和处理状态。
 * 策略：上传后立即执行解析和分块入库，成功后状态改为 COMPLETED。
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
        resumeDocumentRepository.save(document);

        try {
            List<ParsedPage> pages = pdfParseService.parseByPage(file);
            List<ResumeChunkEntity> chunks = resumeChunkingService.buildChunks(docId, userId, pages);
            // 中文说明：上传阶段预计算分块向量，减少检索阶段的实时计算开销。
            for (ResumeChunkEntity chunk : chunks) {
                float[] vector = embeddingService.embed(chunk.getContent());
                chunk.setContentEmbedding(embeddingService.serialize(vector));
                chunk.setEmbeddingDim(embeddingService.dimension(vector));
            }
            resumeChunkRepository.deleteByDocId(docId);
            resumeChunkRepository.saveAll(chunks);
            document.setStatus(DocumentStatus.COMPLETED);
            resumeDocumentRepository.save(document);
            return new UploadResumeResponse(docId, DocumentStatus.COMPLETED.name());
        } catch (Exception ex) {
            document.setStatus(DocumentStatus.FAILED);
            resumeDocumentRepository.save(document);
            throw ex;
        }
    }

    //抛出异常
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

    private String resolveDocName(MultipartFile file, String docName) {
        if (docName != null && !docName.isBlank()) {
            return docName;
        }
        return file.getOriginalFilename() == null ? "resume.pdf" : file.getOriginalFilename();
    }
}

package com.example.resumecoach.resume.service;

import com.example.resumecoach.common.api.ErrorCode;
import com.example.resumecoach.common.exception.BizException;
import com.example.resumecoach.resume.model.dto.ParsedPage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：PDF 解析服务，按页提取文本。
 * 策略：按页读取可保留基础页码信息，便于后续 citation 输出。
 */
@Service
public class PdfParseService {

    public List<ParsedPage> parseByPage(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            List<ParsedPage> pages = new ArrayList<>();
            int total = document.getNumberOfPages();
            for (int page = 1; page <= total; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                pages.add(new ParsedPage(page, cleanText(text)));
            }
            return pages;
        } catch (IOException ex) {
            throw new BizException(ErrorCode.BAD_REQUEST, "PDF 解析失败，请检查文件是否损坏");
        }
    }

    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        // 中文说明：统一空白符，减少分块和检索噪声。
        return raw.replace("\u0000", "")
                .replace("\r", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}


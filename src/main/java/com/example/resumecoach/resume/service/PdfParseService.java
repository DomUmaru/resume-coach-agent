package com.example.resumecoach.resume.service;

import com.example.resumecoach.common.api.ErrorCode;
import com.example.resumecoach.common.exception.BizException;
import com.example.resumecoach.resume.model.dto.ParsedPage;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 中文说明：PDF 解析服务，负责按页提取文本并在入库前完成基础清洗。
 * 输入：用户上传的 PDF 文件。
 * 输出：按页组织的纯文本列表，供后续 chunking 和 embedding 使用。
 * 策略：优先保留可检索的正文信息，去掉图标字体、零宽字符和常见装饰符号，减少脏数据入库。
 */
@Service
public class PdfParseService {

    //图标字体、特殊字体乱码
    private static final Pattern PRIVATE_USE_SYMBOLS = Pattern.compile("[\\p{Co}\\p{Cs}]");
    //匹配零宽字符
    private static final Pattern ZERO_WIDTH_SYMBOLS = Pattern.compile("[\\u200B-\\u200F\\u2060\\uFEFF]");
    //匹配控制字符，但保留 \n 和 \t
    private static final Pattern CONTROL_SYMBOLS = Pattern.compile("[\\p{Cntrl}&&[^\\n\\t]]");
    //匹配一些装饰性符号区间
    private static final Pattern DECORATIVE_SYMBOLS = Pattern.compile("[\\u25A0-\\u27BF\\uE000-\\uF8FF]");

    /**
     * 中文说明：按页读取 PDF 文本，保留页码与清洗后的页面内容。
     * @param file 上传的 PDF 文件
     * @return 页面文本列表
     * 异常策略：PDF 无法解析时抛出业务异常，阻止脏文档继续入库。
     */
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
            throw new BizException(ErrorCode.BAD_REQUEST, "PDF parse failed, please verify the file is valid.");
        }
    }

    /**
     * 中文说明：对 PDF 原始文本做标准化与噪声清理。
     * @param raw PDFBox 提取出的原始文本
     * @return 适合检索和分块的文本
     * 异常策略：输入为空时直接返回空串，不额外抛错。
     */
    private String cleanText(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);
        // 中文说明：先清掉私有区字符、零宽字符和控制字符，避免联系方式图标、异常字形污染正文。
        normalized = PRIVATE_USE_SYMBOLS.matcher(normalized).replaceAll(" ");
        normalized = ZERO_WIDTH_SYMBOLS.matcher(normalized).replaceAll("");
        normalized = CONTROL_SYMBOLS.matcher(normalized).replaceAll(" ");
        normalized = DECORATIVE_SYMBOLS.matcher(normalized).replaceAll(" ");

        // 中文说明：再统一空白和项目符号，尽量保留正文语义，减少后续分段误判。
        return normalized.replace("\u0000", "")
                .replace("\r", "\n")
                .replace('\u3000', ' ')
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t]+", " ")
                .replaceAll("(?m)^\\s*[\\u00B7\\u2022\\u25AA\\u25E6\\u25C6\\u25BA\\u27A4]+\\s*", "")
                .replaceAll("(?m)^\\s*[-_*]{2,}\\s*$", "")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }
}

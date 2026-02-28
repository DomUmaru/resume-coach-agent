package com.example.resumecoach.resume.service;

import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.resume.model.dto.ParsedPage;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 中文说明：简历分块服务，负责把页面文本整理成可检索的 parent/child chunk。
 * 输入：解析后的页面文本。
 * 输出：带 section、页码、父子关系的 chunk 列表。
 * 策略：先做段落合并和 section 识别，再生成 parent/child 两层分块，兼顾检索精度和上下文完整性。
 */
@Service
public class ResumeChunkingService {

    private static final int MAX_CHARS_PER_CHUNK = 450;

    /**
     * 中文说明：构建简历 chunk，并让标题行影响后续正文的 section 归类。
     * @param docId 文档 ID
     * @param userId 用户 ID
     * @param pages PDF 解析得到的页面文本
     * @return 可直接入库的 chunk 列表
     */
    public List<ResumeChunkEntity> buildChunks(String docId, String userId, List<ParsedPage> pages) {
        List<ResumeChunkEntity> chunks = new ArrayList<>();
        for (ParsedPage page : pages) {
            List<String> paragraphs = mergeParagraphs(page.getContent());
            SectionType currentSection = SectionType.OTHER;
            for (String para : paragraphs) {
                String normalized = para == null ? "" : para.trim();
                if (normalized.isBlank()) {
                    continue;
                }

                SectionType detected = detectSection(normalized);
                if (isSectionHeading(normalized, detected)) {
                    // 中文说明：标题行本身不入库，只更新当前 section，让后续正文继承。
                    currentSection = detected;
                    continue;
                }
                if (detected != SectionType.OTHER && !startsWithLabeledField(normalized)) {
                    // 中文说明：普通正文如果自己带有明确 section 线索，可以更新上下文 section；
                    // 但“技术栈：...”这类标签字段通常属于当前项目描述的一部分，不应强行切到 SKILL。
                    currentSection = detected;
                }

                SectionType section = currentSection == SectionType.OTHER ? detected : currentSection;
                addChunkPair(chunks, docId, userId, page.getPageNumber(), normalized, section);
            }
        }
        return chunks;
    }

    /**
     * 中文说明：为同一段正文生成 parent 和 child 两层 chunk。
     * @param chunks 结果集合
     * @param docId 文档 ID
     * @param userId 用户 ID
     * @param pageNumber 来源页码
     * @param content 段落正文
     * @param section 归类后的 section
     */
    private void addChunkPair(List<ResumeChunkEntity> chunks,
                              String docId,
                              String userId,
                              Integer pageNumber,
                              String content,
                              SectionType section) {
        ResumeChunkEntity parent = new ResumeChunkEntity();
        parent.setId(IdGenerator.generate("chunk"));
        parent.setDocId(docId);
        parent.setUserId(userId);
        parent.setParentId(null);
        parent.setSection(section);
        parent.setChunkType(ChunkType.PARENT);
        parent.setSourcePage(pageNumber);
        parent.setContent(content);
        chunks.add(parent);

        // 中文说明：检索阶段优先命中 child，生成阶段再回填 parent，上下文会更完整。
        for (String piece : splitByLength(content, MAX_CHARS_PER_CHUNK)) {
            ResumeChunkEntity child = new ResumeChunkEntity();
            child.setId(IdGenerator.generate("chunk"));
            child.setDocId(docId);
            child.setUserId(userId);
            child.setParentId(parent.getId());
            child.setSection(section);
            child.setChunkType(ChunkType.CHILD);
            child.setSourcePage(pageNumber);
            child.setContent(piece);
            chunks.add(child);
        }
    }

    /**
     * 中文说明：把 PDF 粗分段结果重新合并，修复“同一句被换行拆断”的情况。
     * @param content 页面原始文本
     * @return 更接近语义段落的文本列表
     */
    private List<String> mergeParagraphs(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        String[] rawParagraphs = content.split("\\n\\n");
        List<String> merged = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String paragraph : rawParagraphs) {
            String normalized = normalizeParagraph(paragraph);
            if (normalized.isBlank()) {
                continue;
            }
            if (current.length() == 0) {
                current.append(normalized);
                continue;
            }
            if (shouldMerge(current.toString(), normalized)) {
                current.append(joiner(current.toString(), normalized)).append(normalized);
                continue;
            }
            merged.add(current.toString());
            current = new StringBuilder(normalized);
        }

        if (current.length() > 0) {
            merged.add(current.toString());
        }
        return merged;
    }

    /**
     * 中文说明：统一段内换行和连续空白，避免同一段落被格式符干扰。
     */
    private String normalizeParagraph(String paragraph) {
        if (paragraph == null) {
            return "";
        }
        return paragraph.trim()
                .replaceAll("\\s*\\n\\s*", " ")
                .replaceAll("\\s{2,}", " ");
    }

    /**
     * 中文说明：判断相邻两段是否应该重新拼接。
     * 策略：标题行、标签字段和过长段落不合并；更偏向修复 PDF 导致的断句，而不是盲目合段。
     */
    private boolean shouldMerge(String current, String next) {
        if (current.isBlank() || next.isBlank()) {
            return false;
        }
        SectionType currentSection = detectSection(current);
        if (isSectionHeading(current, currentSection)) {
            return false;
        }
        SectionType nextSection = detectSection(next);
        if (isSectionHeading(next, nextSection)) {
            return false;
        }
        if (startsWithLabeledField(next)) {
            return false;
        }
        if (current.length() >= MAX_CHARS_PER_CHUNK || next.length() >= MAX_CHARS_PER_CHUNK) {
            return false;
        }
        return endsLikeBrokenSentence(current) || startsLikeContinuation(next);
    }

    /**
     * 中文说明：当前段如果以字母、数字、中文或连接符结尾，通常意味着句子可能被截断。
     */
    private boolean endsLikeBrokenSentence(String text) {
        char last = text.charAt(text.length() - 1);
        return Character.isLetterOrDigit(last)
                || isChinese(last)
                || last == ':'
                || last == '：'
                || last == ','
                || last == '，'
                || last == '-'
                || last == '/';
    }

    /**
     * 中文说明：下一段以小写、数字、中文或项目符号开头时，更可能是上一段的延续。
     */
    private boolean startsLikeContinuation(String text) {
        String normalized = text.trim();
        if (normalized.isEmpty()) {
            return false;
        }
        char first = normalized.charAt(0);
        return Character.isLowerCase(first)
                || Character.isDigit(first)
                || isChinese(first)
                || first == '·'
                || first == '('
                || first == '（'
                || first == '['
                || first == '【';
    }

    /**
     * 中文说明：辅助判断是否为中文字符，避免把中文断句误判成完整英文句。
     */
    private boolean isChinese(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
    }

    /**
     * 中文说明：识别“技术栈：”“职责：”这类标签字段。
     * 策略：这类行通常属于当前项目或经历的补充说明，不适合作为新的顶层 section。
     */
    private boolean startsWithLabeledField(String text) {
        String normalized = text.trim();
        return normalized.startsWith("技术栈：")
                || normalized.startsWith("技术栈:")
                || normalized.startsWith("职责：")
                || normalized.startsWith("职责:")
                || normalized.startsWith("项目背景：")
                || normalized.startsWith("项目背景:")
                || normalized.startsWith("成果：")
                || normalized.startsWith("成果:");
    }

    /**
     * 中文说明：拼接两个被错误拆开的段落时，决定中间是否补空格。
     */
    private String joiner(String current, String next) {
        if (current.endsWith("-") || current.endsWith("/") || current.endsWith("（") || current.endsWith("(")) {
            return "";
        }
        if (next.startsWith("，") || next.startsWith(",") || next.startsWith("。") || next.startsWith(".")
                || next.startsWith("；") || next.startsWith(";") || next.startsWith(")") || next.startsWith("）")) {
            return "";
        }
        return " ";
    }

    /**
     * 中文说明：按长度切 child chunk，并尽量在自然断点处分割，避免把一句话切得太碎。
     */
    private List<String> splitByLength(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            if (end < text.length()) {
                int split = findSplitPosition(text, start, end);
                if (split > start) {
                    end = split;
                }
            }
            result.add(text.substring(start, end).trim());
            start = end;
        }
        return result;
    }

    /**
     * 中文说明：优先在句号、逗号、分号或空格附近截断，降低 child chunk 的语义破碎度。
     */
    private int findSplitPosition(String text, int start, int end) {
        for (int i = end; i > start + 120; i--) {
            char ch = text.charAt(i - 1);
            if (ch == '。' || ch == '；' || ch == ';' || ch == '!' || ch == '！'
                    || ch == '?' || ch == '？' || ch == '，' || ch == ',' || ch == ' ') {
                return i;
            }
        }
        return end;
    }

    /**
     * 中文说明：判断一段文本是否像“项目经历”“教育背景”这样的 section 标题。
     * 策略：标题通常短、无标点、无描述性内容。
     */
    private boolean isSectionHeading(String text, SectionType section) {
        if (section == SectionType.OTHER) {
            return false;
        }
        String normalized = stripDecorations(text);
        if (normalized.length() > 24) {
            return false;
        }
        return !normalized.contains("：")
                && !normalized.contains(":")
                && !normalized.contains("，")
                && !normalized.contains(",")
                && !normalized.contains("。")
                && !normalized.contains(".");
    }

    /**
     * 中文说明：去掉项目符号和空白，便于做标题识别和关键词匹配。
     */
    private String stripDecorations(String text) {
        return text == null ? "" : text
                .replaceAll("[\\u00B7\\u2022\\u25AA\\u25E6\\u25C6\\u25BA\\u27A4]+", "")
                .replaceAll("\\s+", "")
                .trim();
    }

    /**
     * 中文说明：基于关键词做 section 识别。
     * 策略：当前仍是轻量规则实现，优先保证稳定；复杂简历可继续升级为标题继承 + 结构模板识别。
     */
    private SectionType detectSection(String text) {
        String lower = stripDecorations(text).toLowerCase(Locale.ROOT);
        if (containsAny(lower, "\u9879\u76EE\u7ECF\u5386", "\u9879\u76EE\u7ECF\u9A8C", "\u9879\u76EE", "project")) {
            return SectionType.PROJECT;
        }
        if (containsAny(lower, "\u5DE5\u4F5C\u7ECF\u5386", "\u5B9E\u4E60\u7ECF\u5386", "\u5DE5\u4F5C\u7ECF\u9A8C",
                "\u5B9E\u4E60\u7ECF\u9A8C", "work", "experience")) {
            return SectionType.WORK;
        }
        if (containsAny(lower, "\u6559\u80B2\u7ECF\u5386", "\u6559\u80B2\u80CC\u666F", "\u6559\u80B2",
                "\u5B66\u6821", "education")) {
            return SectionType.EDUCATION;
        }
        if (containsAny(lower, "\u4E13\u4E1A\u6280\u80FD", "\u6280\u80FD", "skill", "skills", "techstack",
                "\u6280\u672F\u6808")) {
            return SectionType.SKILL;
        }
        if (containsAny(lower, "\u4E2A\u4EBA\u603B\u7ED3", "\u4E2A\u4EBA\u7B80\u4ECB", "\u7B80\u4ECB",
                "summary", "profile", "objective")) {
            return SectionType.SUMMARY;
        }
        return SectionType.OTHER;
    }

    /**
     * 中文说明：判断文本是否命中任一关键词。
     */
    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}

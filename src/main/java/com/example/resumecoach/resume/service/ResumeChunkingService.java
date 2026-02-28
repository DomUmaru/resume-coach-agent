package com.example.resumecoach.resume.service;

import com.example.resumecoach.common.util.IdGenerator;
import com.example.resumecoach.resume.model.dto.ParsedPage;
import com.example.resumecoach.resume.model.entity.ResumeChunkEntity;
import com.example.resumecoach.resume.model.enumtype.ChunkType;
import com.example.resumecoach.resume.model.enumtype.SectionType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 中文说明：简历文本分块服务。
 * 策略：先做基于段落的轻量分块，保证 Demo 阶段可读性和可检索性。
 */
@Service
public class ResumeChunkingService {

    private static final int MAX_CHARS_PER_CHUNK = 450;

    public List<ResumeChunkEntity> buildChunks(String docId, String userId, List<ParsedPage> pages) {
        List<ResumeChunkEntity> chunks = new ArrayList<>();
        for (ParsedPage page : pages) {
            String[] paragraphs = page.getContent().split("\\n\\n");
            for (String para : paragraphs) {
                String normalized = para == null ? "" : para.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                SectionType section = detectSection(normalized);
                ResumeChunkEntity parent = new ResumeChunkEntity();
                parent.setId(IdGenerator.generate("chunk"));
                parent.setDocId(docId);
                parent.setUserId(userId);
                parent.setParentId(null);
                parent.setSection(section);
                parent.setChunkType(ChunkType.PARENT);
                parent.setSourcePage(page.getPageNumber());
                parent.setContent(normalized);
                chunks.add(parent);

                // 中文说明：检索命中更细粒度 child，但生成阶段可回填 parent 作为更完整上下文。
                for (String piece : splitByLength(normalized, MAX_CHARS_PER_CHUNK)) {
                    ResumeChunkEntity child = new ResumeChunkEntity();
                    child.setId(IdGenerator.generate("chunk"));
                    child.setDocId(docId);
                    child.setUserId(userId);
                    child.setParentId(parent.getId());
                    child.setSection(section);
                    child.setChunkType(ChunkType.CHILD);
                    child.setSourcePage(page.getPageNumber());
                    child.setContent(piece);
                    chunks.add(child);
                }
            }
        }
        return chunks;
    }

    private List<String> splitByLength(String text, int maxLen) {
        if (text.length() <= maxLen) {
            return List.of(text);
        }
        List<String> result = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + maxLen, text.length());
            result.add(text.substring(start, end));
            start = end;
        }
        return result;
    }

    private SectionType detectSection(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("项目") || lower.contains("project")) {
            return SectionType.PROJECT;
        }
        if (lower.contains("工作") || lower.contains("实习") || lower.contains("work")) {
            return SectionType.WORK;
        }
        if (lower.contains("教育") || lower.contains("学校") || lower.contains("education")) {
            return SectionType.EDUCATION;
        }
        if (lower.contains("技能") || lower.contains("skill")) {
            return SectionType.SKILL;
        }
        if (lower.contains("简介") || lower.contains("summary")) {
            return SectionType.SUMMARY;
        }
        return SectionType.OTHER;
    }
}

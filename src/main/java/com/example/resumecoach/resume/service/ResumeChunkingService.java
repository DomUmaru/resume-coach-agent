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
                for (String piece : splitByLength(normalized, MAX_CHARS_PER_CHUNK)) {
                    ResumeChunkEntity entity = new ResumeChunkEntity();
                    entity.setId(IdGenerator.generate("chunk"));
                    entity.setDocId(docId);
                    entity.setUserId(userId);
                    entity.setParentId(null);
                    entity.setSection(detectSection(piece));
                    entity.setChunkType(ChunkType.CHILD);
                    entity.setSourcePage(page.getPageNumber());
                    entity.setContent(piece);
                    chunks.add(entity);
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


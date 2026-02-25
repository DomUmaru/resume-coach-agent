package com.example.resumecoach.rag.query;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 中文说明：查询改写服务。
 * 策略：先做术语归一和同义词替换，提升后续召回的一致性。
 */
@Service
public class QueryRewriteService {

    private static final Map<String, String> SYNONYM_MAP = new LinkedHashMap<>();

    static {
        SYNONYM_MAP.put("简历", "履历");
        SYNONYM_MAP.put("项目", "项目经历");
        SYNONYM_MAP.put("实习", "工作经历");
        SYNONYM_MAP.put("优化", "改进");
        SYNONYM_MAP.put("star", "STAR");
        SYNONYM_MAP.put("问答", "QA");
    }

    public String rewrite(String query) {
        if (query == null || query.isBlank()) {
            return "";
        }
        String rewritten = query.trim();
        for (Map.Entry<String, String> entry : SYNONYM_MAP.entrySet()) {
            rewritten = rewritten.replace(entry.getKey(), entry.getValue());
        }
        return rewritten;
    }
}


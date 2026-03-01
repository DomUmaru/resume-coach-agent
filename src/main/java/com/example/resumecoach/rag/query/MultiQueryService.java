package com.example.resumecoach.rag.query;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 中文说明：多查询扩展服务。
 * 策略：在主问题基础上生成少量子查询，提升复杂问题的召回覆盖率。
 */
@Service
public class MultiQueryService {

    /**
     * 中文说明：根据原问题和改写后问题生成一组查询变体。
     */
    public List<String> expand(String rawQuery, String rewrittenQuery) {
        Set<String> queries = new LinkedHashSet<>();
        if (rawQuery != null && !rawQuery.isBlank()) {
            queries.add(rawQuery.trim());
        }
        if (rewrittenQuery != null && !rewrittenQuery.isBlank()) {
            queries.add(rewrittenQuery.trim());
        }
        if (rawQuery != null && !rawQuery.isBlank()) {
            queries.add("请聚焦项目经历：" + rawQuery.trim());
            queries.add("请聚焦量化结果：" + rawQuery.trim());
        }
        return new ArrayList<>(queries);
    }
}

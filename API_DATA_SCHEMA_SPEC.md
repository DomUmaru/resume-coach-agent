# Resume Coach Agent 接口与数据结构清单（编码起步版）

## 1. 设计原则
1. 先满足 Demo 主链路，避免过度设计。
2. 所有响应统一结构，方便前端解析与日志追踪。
3. 所有核心模块必须写中文注释，解释策略与边界。

## 2. API 清单
1. `POST /api/resume/upload`
2. `POST /api/chat/stream`（SSE）
3. `GET /api/chat/history/{sessionId}`（可选）

## 3. 通用响应结构
```json
{
  "code": 0,
  "message": "ok",
  "data": {},
  "traceId": "b9f1f4b2f8f54f31"
}
```

字段说明：
1. `code`：0 表示成功，非 0 表示失败。
2. `message`：错误或成功信息。
3. `data`：业务数据主体。
4. `traceId`：链路追踪 ID，用于日志检索。

## 4. 上传接口
### 4.1 请求
`POST /api/resume/upload`  
`Content-Type: multipart/form-data`

表单字段：
1. `file`：PDF 文件（必填）
2. `userId`：用户 ID（必填）
3. `docName`：文档名称（可选）

### 4.2 响应
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "docId": "doc_20260221_001",
    "status": "PROCESSING"
  },
  "traceId": "trace_upload_xxx"
}
```

状态枚举：
1. `PROCESSING`
2. `COMPLETED`
3. `FAILED`

## 5. 聊天流式接口
### 5.1 请求
`POST /api/chat/stream`  
`Content-Type: application/json`

```json
{
  "sessionId": "sess_001",
  "userId": "u_001",
  "docId": "doc_20260221_001",
  "message": "请用 STAR 改写我的项目经历",
  "intentHint": "REWRITE",
  "options": {
    "enableRewrite": true,
    "enableMultiQuery": true,
    "enableRerank": true
  }
}
```

### 5.2 SSE 事件建议
1. `event: start`：开始处理
2. `event: tool_call`：工具调用信息
3. `event: token`：增量 token
4. `event: citation`：证据引用块
5. `event: done`：结束
6. `event: error`：错误

`event: citation` 数据示例：
```json
{
  "chunkId": "chunk_101",
  "docId": "doc_20260221_001",
  "page": 2,
  "section": "PROJECT",
  "score": 0.86
}
```

## 6. 历史消息接口（可选）
### 6.1 请求
`GET /api/chat/history/{sessionId}?page=1&pageSize=20`

### 6.2 响应
```json
{
  "code": 0,
  "message": "ok",
  "data": {
    "sessionId": "sess_001",
    "messages": [
      {
        "role": "user",
        "content": "你好"
      },
      {
        "role": "assistant",
        "content": "你好，我可以帮你优化简历。",
        "citations": []
      }
    ]
  },
  "traceId": "trace_history_xxx"
}
```

## 7. Tool 定义（最小集）
## 7.1 retrieve_resume_context_tool
用途：根据问题检索简历证据。

输入 Schema（示例）：
```json
{
  "type": "object",
  "properties": {
    "query": { "type": "string" },
    "docId": { "type": "string" },
    "topK": { "type": "integer", "minimum": 1, "maximum": 20 },
    "section": {
      "type": "string",
      "enum": ["SUMMARY", "PROJECT", "WORK", "EDUCATION", "SKILL", "OTHER"]
    }
  },
  "required": ["query", "docId"]
}
```

输出结构：
```json
{
  "items": [
    {
      "chunkId": "chunk_101",
      "text": "负责推荐系统召回优化...",
      "page": 2,
      "section": "PROJECT",
      "score": 0.86
    }
  ]
}
```

## 7.2 star_rewrite_tool
用途：将输入经历改写为 STAR 表达。

输入 Schema（示例）：
```json
{
  "type": "object",
  "properties": {
    "rawText": { "type": "string" },
    "targetRole": { "type": "string" },
    "tone": { "type": "string", "enum": ["CONCISE", "IMPACTFUL", "TECHNICAL"] }
  },
  "required": ["rawText"]
}
```

输出结构：
```json
{
  "star": {
    "situation": "...",
    "task": "...",
    "action": "...",
    "result": "..."
  }
}
```

## 7.3 resume_qa_tool
用途：简历问答，返回基于证据的答案草稿。

输入 Schema（示例）：
```json
{
  "type": "object",
  "properties": {
    "question": { "type": "string" },
    "docId": { "type": "string" },
    "evidenceChunkIds": {
      "type": "array",
      "items": { "type": "string" }
    }
  },
  "required": ["question", "docId"]
}
```

输出结构：
```json
{
  "answerDraft": "根据你在 XX 项目中的描述...",
  "citations": [
    { "chunkId": "chunk_101", "page": 2, "section": "PROJECT" }
  ]
}
```

## 8. 数据表建议（PostgreSQL + pgvector）
## 8.1 `resume_document`
1. `id` (varchar, pk)
2. `user_id` (varchar, index)
3. `doc_name` (varchar)
4. `file_path` (varchar)
5. `status` (varchar)
6. `created_at` (timestamp)
7. `updated_at` (timestamp)

## 8.2 `resume_chunk`
1. `id` (varchar, pk)
2. `doc_id` (varchar, index)
3. `user_id` (varchar, index)
4. `parent_id` (varchar, nullable)
5. `section` (varchar, index)
6. `chunk_type` (varchar, index)  
7. `source_page` (int)
8. `content` (text)
9. `content_tsv` (tsvector, FTS)
10. `embedding` (vector)
11. `created_at` (timestamp)

索引建议：
1. `ivfflat/hnsw` 索引（embedding）
2. `GIN` 索引（content_tsv）
3. 组合索引：`(doc_id, section, chunk_type)`

## 8.3 `chat_session`
1. `id` (varchar, pk)
2. `user_id` (varchar, index)
3. `doc_id` (varchar, index)
4. `title` (varchar)
5. `created_at` (timestamp)
6. `updated_at` (timestamp)

## 8.4 `chat_message`
1. `id` (varchar, pk)
2. `session_id` (varchar, index)
3. `role` (varchar)
4. `content` (text)
5. `citations_json` (jsonb)
6. `tool_trace_json` (jsonb)
7. `created_at` (timestamp)

## 8.5 `rag_trace_log`（建议）
1. `id` (varchar, pk)
2. `trace_id` (varchar, index)
3. `session_id` (varchar, index)
4. `raw_query` (text)
5. `rewritten_query` (text)
6. `retrieval_json` (jsonb)
7. `rerank_json` (jsonb)
8. `final_citations_json` (jsonb)
9. `latency_json` (jsonb)
10. `created_at` (timestamp)

## 9. 枚举建议
1. `intent_type`：`QA | REWRITE | FOLLOWUP | MOCK_INTERVIEW | CHITCHAT`
2. `section_type`：`SUMMARY | PROJECT | WORK | EDUCATION | SKILL | OTHER`
3. `chunk_type`：`PARENT | CHILD | SUMMARY`

## 10. 错误码建议
1. `0`：成功
2. `4001`：请求参数错误
3. `4002`：文件格式不支持
4. `4003`：文档不存在
5. `5001`：向量化失败
6. `5002`：检索失败
7. `5003`：模型调用失败
8. `5004`：流式输出中断

## 11. 中文注释要求（开发期强制）
1. Controller：注释接口用途、参数、返回值和异常码含义。
2. Service：注释策略选择原因（例如为何走 Multi-Query）。
3. Repository/DAO：注释索引依赖与查询边界。
4. Tool/Skill：注释输入校验、决策分支、降级行为。
5. SSE：注释事件时序和中断恢复逻辑。

## 12. 第一批编码优先级
1. 先实现：上传接口、聊天流式接口、文档与分块表。
2. 再实现：`retrieve_resume_context_tool` + `IntentSkill`。
3. 然后补齐：`star_rewrite_tool`、`resume_qa_tool` 与 citation 展示。


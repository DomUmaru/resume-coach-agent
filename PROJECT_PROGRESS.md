# Resume Coach Agent 项目进度（持续更新）

## 1. 文档说明
1. 本文档用于记录已完成功能、当前状态与下一步计划。
2. 每次开发迭代后同步更新，作为团队统一进度面板。
3. 注：以下内容基于当前代码仓库真实实现状态整理。

## 2. 当前总体状态
1. 后端骨架已完成并可启动。
2. 数据库已支持 Docker 一键启动。
3. 上传、流式聊天、会话历史、Agent 基础编排已打通。
4. Spring AI 已接入（可开关，默认关闭）。
5. PDF 真实解析与分块入库已实现。
6. 检索已实现轻量 Hybrid（FTS + 关键词 + RRF 融合）。

## 3. 已完成功能清单

### 3.1 工程与基础设施
1. Spring Boot 3.3 + Java 17 工程初始化。
2. 统一响应结构：`code/message/data/traceId`。
3. 全局异常处理与统一错误码。
4. 请求 `traceId` 自动注入与透传。
5. Lombok 规范落地（DTO/VO 使用，实体避免 `@Data`）。

### 3.2 数据库与持久化
1. PostgreSQL + JPA 接入。
2. 核心表实体与仓储已实现：
3. `resume_document`
4. `resume_chunk`
5. `chat_session`
6. `chat_message`
7. Docker Compose 已提供：
8. 一键启动 `resume_coach` 数据库。
9. 自动执行初始化脚本 `sql/init_schema.sql`。

### 3.3 上传与入库链路
1. 接口：`POST /api/resume/upload`。
2. 上传参数校验（文件、userId、pdf 格式）。
3. PDFBox 按页解析文本。
4. 文本清洗与段落分块。
5. 分块入库并保留页码与 section。
6. 文档状态流转：`PROCESSING -> COMPLETED/FAILED`。

### 3.4 聊天与 Agent 链路
1. 接口：`POST /api/chat/stream`（SSE）。
2. SSE 事件：
3. `start`
4. `tool_call`
5. `token`
6. `citation`
7. `done`
8. `error`
9. Agent 编排器已实现（Skill 决策 + Tool 执行）。
10. Skills 已实现：
11. `IntentSkill`
12. `RetrievalSkill`
13. `AnswerSkill`
14. Tools 已实现：
15. `retrieve_resume_context_tool`
16. `star_rewrite_tool`
17. `resume_qa_tool`

### 3.5 检索与 RAG 基础能力
1. 检索工具已支持数据库真实分块查询。
2. 支持 PostgreSQL FTS 查询（`websearch_to_tsquery`）。
3. 支持关键词相关性排序。
4. 支持 RRF 融合排序（FTS + 关键词）。
5. 返回标准 citation（`chunkId/docId/page/section/score`）。
6. 已接入 Query Rewrite（术语归一/同义替换）。
7. 已接入 Multi-Query（主查询 + 子查询扩展）。
8. 已接入轻量 Rerank（二次排序）。
9. 检索证据已传递给 QA/改写工具，回答与证据链路一致。

### 3.6 Spring AI 接入状态
1. 已引入 Spring AI OpenAI Starter。
2. 新增统一 `LlmService` 封装模型调用。
3. `star_rewrite_tool` 与 `resume_qa_tool` 已接入模型调用。
4. 配置支持开关：
5. `APP_AI_ENABLED=false` 默认关闭。
6. 开启后可使用 `OPENAI_API_KEY`、`OPENAI_BASE_URL`、`OPENAI_MODEL`。
7. 模型不可用时自动降级到模板输出，保证链路不中断。

### 3.7 历史消息
1. 接口：`GET /api/chat/history/{sessionId}`。
2. 聊天消息与引用信息已落库并可回查。

## 4. 当前待完成项（高优先）
1. 向量检索（pgvector embedding 字段、向量索引、向量查询）。
2. 真正的 Hybrid Retrieval（向量 + FTS 权重融合可配置）。
3. Query Rewrite / Multi-Query / Rerank。
4. Tool JSON Schema 与 Spring AI function calling 自动选工具。
5. Citation Verifier 与无证据拒答策略。
6. 评测集与离线指标统计（Hit@K、MRR、Faithfulness 等）。

## 5. 启动与联调要点
1. 数据库：
2. `docker compose up -d`
3. 服务启动前确保环境变量与 `application.yml` 一致。
4. 若启用模型能力，设置：
5. `APP_AI_ENABLED=true`
6. `OPENAI_API_KEY=...`
7. `OPENAI_BASE_URL=https://api.deepseek.com`（可按需覆盖）

## 6. 更新记录
1. 2026-02-22：新增项目进度文档，归档当前已实现能力与待办路线。
2. 2026-02-22：完成检索策略升级（Rewrite/Multi-Query/Rerank）并打通“检索证据 -> 生成工具”传递链路。

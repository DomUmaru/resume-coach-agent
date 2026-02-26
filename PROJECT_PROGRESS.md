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
10. 已接入向量召回（Embedding + 余弦相似度），并与 FTS/关键词进行三路 Hybrid 融合。
11. 已支持 pgvector 原生距离检索（`<=>`），并保留应用内向量排序降级方案。
12. 已支持动态 TopK（按查询复杂度）与向量最小相似度阈值过滤。
13. 已接入 Guardrail：无证据拒答/追问策略 + Citation 一致性校验。
14. 已接入模型驱动的自动 Tool 选择（含规则兜底），SSE 可展示 selectedTool。
15. 自动 Tool 选择已升级为 JSON 决策输出（toolName/confidence/reason）并完成字段校验。
16. 已新增离线评测脚手架（Hit@K、MRR、Citation Precision）与黄金集样例接口。
17. 离线评测结果已支持落库（eval_report）与历史报告查询（最近20条）。
18. 评测报告已记录策略版本号与配置快照，可用于策略回归对比。

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
3. 2026-02-22：完成向量化入库与向量召回接入，Hybrid 检索升级为 FTS + 关键词 + 向量三路融合。
4. 2026-02-22：完成 pgvector 扩展接入与数据库原生向量检索改造，向量检索链路支持自动降级。
5. 2026-02-22：完成检索调优配置化（动态 TopK + vector 阈值），提升召回质量可控性。
6. 2026-02-22：完成防幻觉链路接入（Citation Verifier + No-Evidence Policy），主链路具备安全回复能力。
7. 2026-02-22：完成自动工具选择链路（LlmService chooseTool），编排器支持动态路由与追踪。
8. 2026-02-22：完成工具选择结构化升级（JSON schema 风格输出），SSE 新增置信度与理由追踪字段。
9. 2026-02-22：完成离线评测模块初版（/api/eval/offline + golden-set-sample），支持检索质量基础量化。
10. 2026-02-22：完成评测报告持久化与回归查询接口（/api/eval/reports/{docId}）。
11. 2026-02-22：完成评测策略快照机制（strategyVersion + configSnapshotJson），回归报告可追踪配置差异。

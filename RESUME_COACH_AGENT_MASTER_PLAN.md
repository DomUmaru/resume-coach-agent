# Resume Coach Agent - Demo 主计划（春招面试版）

## 1. 项目定位
目标从“完整产品”调整为“可讲清楚的技术 Demo”：
1. 保留 `PDF 简历上传 + Chat 对话窗口` 两个可视功能。
2. 核心只打三件事：`RAG 深挖`、`Tool/Function Calling`、`Agent Skills`。
3. 输出面向面试叙事：能讲架构、讲取舍、讲一条完整调用链。

不做（本期全部移除）：
1. 多版本管理与 diff 回退。
2. 复杂长期记忆治理。
3. 重运维、重评测、重权限体系。

---

## 2. 技术栈（聚焦可落地）

### 2.1 后端
1. Java 17 + Spring Boot 3.3+
2. Spring AI（主线实现）
3. PostgreSQL + pgvector
4. PDFBox
5. SSE（流式聊天）

### 2.2 前端
1. React + Vite
2. 页面仅 2 个核心区块：上传面板 + 聊天面板

### 2.3 模型基线
1. LLM：DeepSeek Chat（可替换）
2. Embedding：bge-m3（或同维度替代）
3. Rerank：可选（没有也可演示完整链路）

---

## 3. Demo 架构（一条主链路）
1. 用户上传 PDF 简历。
2. 后端解析 + 分块 + embedding + 入库。
3. 用户在 Chat 提问（改写 / 问答 / 模拟面试）。
4. Agent 先走 Skill 路由，再决定是否检索。
5. 触发 Tool Calling 获取证据或执行业务工具。
6. LLM 基于上下文生成回答并返回前端。

面试时重点图：`UI -> API -> Skills Router -> Retriever -> Tools -> LLM -> SSE`。

---

## 4. RAG 深挖范围（核心全量能力）

### 4.1 Ingestion（入库层）
1. PDF 结构化解析：标题/段落/项目经历/技能分区。
2. Parent-Child Chunk：检索命中 child，回填 parent 给 LLM。
3. 元数据标准化：`user_id`、`section`、`source_page`、`chunk_type`、`doc_id`。
4. 文本清洗与去重：去模板噪声、去乱码、去重复段落。
5. 双索引入库：`vector` + `BM25/FTS`。

### 4.2 Query Understanding（查询理解层）
1. Intent 分类：问答/改写/追问/模拟面试。
2. Query Rewrite：同义扩展、术语归一、拼写纠错。
3. Multi-Query：单问题拆 2-4 个子查询并发检索。
4. HyDE：低召回时生成假设答案再检索（可配置开关）。
5. Query Router：按问题类型选择检索策略。

### 4.3 Retrieval（召回层）
1. Hybrid Retrieval：向量召回 + 关键词召回融合。
2. 融合策略支持：`Weighted` 与 `RRF` 可切换。
3. 动态 TopK：根据 query 难度调节召回量。
4. Metadata Filter：按 `section`、`source_page`、`chunk_type` 精确过滤。
5. Multi-hop Retrieval：复杂问题按子问题分步检索并汇总。

### 4.4 Ranking（排序层）
1. Cross-Encoder Rerank 二次排序。
2. MMR 去冗余，减少重复证据。
3. 冲突证据处理：按来源可信度与位置优先级决策。
4. 召回失败兜底：放宽过滤、扩大 TopK、重写 query 重试。

### 4.5 Context Building（上下文构建层）
1. Context Compression：句级压缩，保留高信息密度证据。
2. 证据组织模板：背景-动作-结果（BAR）编排。
3. Token Budget 控制：`TopN -> TopM -> TopK` 分层裁剪。
4. 引用标准化：统一返回 `chunk_id + page + section`。

### 4.6 Generation + Guardrail（生成与防幻觉）
1. 统一 Prompt 模板：角色、任务、证据、输出格式。
2. 强制 citation 输出，句子与证据绑定。
3. Citation Verifier：答案语句与证据一致性校验。
4. 无证据场景：拒答或追问，不做无依据编造。
5. Self-RAG：先草稿回答，再判断证据不足时触发二次检索。

### 4.7 Evaluation（评测层）
1. 检索指标：`Hit@K`、`Recall@K`、`MRR`、`nDCG`。
2. 生成指标：Faithfulness、Answer Relevancy、Citation Precision。
3. 构建黄金集：50-200 条简历场景数据（问答/改写/追问）。
4. 策略变更必须离线回归，保留版本对比报告。

### 4.8 Observability（可观测层）
1. 全链路日志：原 query、改写 query、召回候选、rerank 分数、最终引用。
2. 时延拆解：embedding、retrieval、rerank、generation 分段耗时。
3. 成本监控：token、请求次数、工具调用次数、降级触发率。

### 4.9 Reliability（稳定性层）
1. 超时降级：跳过 rerank、降低 TopK、短路快速回答。
2. 模型失败 fallback：备用 embedding 模型与备用 LLM 路由。
3. 检索/工具异常可恢复，保证 chat 主链路不中断。

---

## 5. Tool / Function Calling（必须可演示）
最小工具集只保留 3 个：
1. `retrieve_resume_context_tool`
2. `star_rewrite_tool`
3. `resume_qa_tool`

实现要求：
1. 每个 Tool 有清晰 JSON Schema（必填、类型、枚举）。
2. 模型通过 function calling 自动选 tool。
3. tool 返回统一结构，便于最终回答拼装。

---

## 6. Agent Skills（必须可讲）
保留 3 个 Skills，避免过度设计：
1. `IntentSkill`：识别用户是改写、问答还是泛聊。
2. `RetrievalSkill`：决定是否检索以及检索参数。
3. `AnswerSkill`：组织最终回复格式（含证据/建议）。

叙事重点：
1. Skill 不等于 Tool，Skill 负责“决策”，Tool 负责“执行”。
2. Skill 串联让 Agent 看起来像“会思考”，不是单次 prompt 拼接。

---

## 7. API（Demo 最小集合）
1. `POST /api/resume/upload`：上传并入库
2. `POST /api/chat/stream`：流式对话（内部可触发 tools）
3. `GET /api/chat/history/{sessionId}`：可选，便于演示多轮上下文

---

## 8. 前端演示要求
1. 上传成功后展示解析状态（处理中/完成）。
2. 聊天窗口支持流式输出。
3. 每条回复可展开“证据来源”（chunk/page）。

---

## 9. 里程碑（RAG 全量分阶段）
1. Phase 1（基础可用）：上传解析、双索引入库、Hybrid 检索、基础 citation。
2. Phase 2（能力增强）：Query Rewrite、Multi-Query、Rerank、Context Compression。
3. Phase 3（智能闭环）：Self-RAG、Citation Verifier、无证据拒答/追问。
4. Phase 4（工程化）：评测集、离线回归、全链路可观测、降级与 fallback。

---

## 10. 面试讲解模板（一句话）
这个项目是一个 Java Agent Demo：前端上传简历后，后端通过 RAG 构建可检索上下文，再用 Spring AI function calling 调用工具，并通过 skills 完成决策与回答编排，最终在 Chat 窗口流式输出可追溯答案。

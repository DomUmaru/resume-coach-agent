# Resume Coach Agent 任务拆解清单（按天）

## 1. 目标
1. 在 10 个工作日内交付可面试演示的 Agent Demo。
2. 必须打通链路：上传 PDF -> RAG 检索 -> Tool Calling -> Skills 决策 -> SSE 流式回答。
3. 所有核心代码要求中文注释，且注释说明“为什么这样做”。

## 2. 角色建议
1. 后端负责人：API、RAG、Tool/Skill、数据库、SSE。
2. 前端负责人：上传面板、聊天面板、证据展示。
3. 算法/RAG 负责人：检索策略、重排、评测。
4. 项目负责人：演示脚本、里程碑推进、验收。

## 3. 按天计划（D1-D10）
### D1：需求冻结与架构设计
1. 冻结范围（必须做/不做）。
2. 输出架构图：`UI -> API -> Skills Router -> Retriever -> Tools -> LLM -> SSE`。
3. 输出数据模型草案与索引策略（vector + FTS）。
4. 验收标准：完成《接口与数据结构清单》v1。

### D2：上传与入库主链路
1. 实现 `POST /api/resume/upload` 基础骨架。
2. PDF 解析、文本清洗、基础分块。
3. 写入 PostgreSQL 与向量字段（pgvector）。
4. 验收标准：上传后可看到文档与分块记录入库。

### D3：检索最小可用
1. 实现 Hybrid Retrieval（向量 + FTS）。
2. 支持元数据过滤：`section/page/chunk_type`。
3. 统一返回 citation 字段：`chunk_id/page/section/doc_id`。
4. 验收标准：输入问题可召回候选证据并返回引用信息。

### D4：聊天与流式输出
1. 实现 `POST /api/chat/stream`（SSE）。
2. 串联 LLM 回答生成与引用输出。
3. 前端接入流式渲染。
4. 验收标准：前端聊天窗口可连续输出 token 并最终给出证据。

### D5：Tool Calling
1. 接入 3 个 Tool：
2. `retrieve_resume_context_tool`
3. `star_rewrite_tool`
4. `resume_qa_tool`
5. 定义 JSON Schema（必填、枚举、错误码）。
6. 验收标准：模型可自动选择工具并产出结构化工具结果。

### D6：Skills 决策层
1. 实现 `IntentSkill`、`RetrievalSkill`、`AnswerSkill`。
2. 明确 Skill 与 Tool 边界：Skill 决策，Tool 执行。
3. 记录决策日志（路由原因、参数来源）。
4. 验收标准：同一问题类型变化时，策略分支可观测。

### D7：RAG 增强能力
1. Query Rewrite（同义扩展、术语归一）。
2. Multi-Query 并发召回。
3. Rerank + MMR 去冗余。
4. Context Compression 与 Token Budget 裁剪。
5. 验收标准：相较 D3，回答相关性与证据质量有可见提升。

### D8：防幻觉与稳定性
1. Citation Verifier（答案句子与证据一致性校验）。
2. 无证据拒答/追问策略。
3. 超时降级（跳过 rerank、降低 TopK、快速回答）。
4. 失败 fallback（备用模型或策略）。
5. 验收标准：异常场景下主链路不中断，行为可解释。

### D9：评测与可观测
1. 构建黄金集（先 50 条，再扩到 200 条）。
2. 统计检索指标：`Hit@K/Recall@K/MRR/nDCG`。
3. 统计生成指标：Faithfulness/Answer Relevancy/Citation Precision。
4. 补齐链路日志与耗时拆解。
5. 验收标准：输出离线评测报告 v1。

### D10：联调与面试包装
1. 稳定性回归与 Demo 彩排。
2. 准备 3 分钟/10 分钟讲解稿。
3. 固化演示问题集与预期输出。
4. 验收标准：可完整演示并解释关键技术取舍。

## 4. 每日交付物（强制）
1. 每日更新 `DONE/TODO/RISK`。
2. 每日输出关键日志样例（至少 1 条完整链路）。
3. 每日同步代码注释抽查结果（核心模块）。

## 5. 中文注释落地规范（强制）
1. 注释语言必须为中文。
2. 关键类必须有类头注释：职责、输入、输出、依赖。
3. 关键方法必须有方法注释：参数约束、返回含义、异常策略。
4. 复杂逻辑前必须写块注释：策略原因与边界条件。
5. Tool/Skill/RAG 关键分支必须解释决策依据。
6. 禁止无意义注释（如“给变量赋值”）。

## 6. 验收清单（最终）
1. 功能验收：上传、检索、工具调用、技能决策、流式回答全部可演示。
2. 质量验收：核心路径有日志、有指标、有降级。
3. 文档验收：架构图、接口文档、评测报告、演示脚本齐全。
4. 注释验收：核心代码中文注释覆盖达标，可读可讲。


# Resume Coach Agent 后端项目骨架与包结构说明

## 1. 目标
1. 提供可直接开工的 Spring Boot 后端结构。
2. 保证主链路清晰：上传 -> 入库 -> 检索 -> Tool -> Skill -> SSE。
3. 强制核心代码中文注释，便于开发与面试讲解。

## 2. 技术基线
1. JDK 17
2. Spring Boot 3.3+
3. Spring AI
4. PostgreSQL + pgvector
5. PDFBox
6. SSE（`text/event-stream`）

## 3. 推荐目录结构
```text
src/main/java/com/example/resumecoach
├─ ResumeCoachApplication.java
├─ common
│  ├─ api
│  │  ├─ ApiResponse.java
│  │  └─ ErrorCode.java
│  ├─ exception
│  │  ├─ BizException.java
│  │  └─ GlobalExceptionHandler.java
│  ├─ trace
│  │  ├─ TraceContext.java
│  │  └─ TraceIdFilter.java
│  └─ util
│     ├─ JsonUtils.java
│     └─ TokenBudgetUtils.java
├─ config
│  ├─ AiModelConfig.java
│  ├─ PgVectorConfig.java
│  ├─ SseConfig.java
│  └─ WebMvcConfig.java
├─ resume
│  ├─ controller
│  │  └─ ResumeController.java
│  ├─ service
│  │  ├─ ResumeIngestionService.java
│  │  ├─ PdfParseService.java
│  │  ├─ ChunkingService.java
│  │  └─ EmbeddingService.java
│  ├─ repository
│  │  ├─ ResumeDocumentRepository.java
│  │  └─ ResumeChunkRepository.java
│  ├─ model
│  │  ├─ entity
│  │  │  ├─ ResumeDocumentEntity.java
│  │  │  └─ ResumeChunkEntity.java
│  │  ├─ dto
│  │  │  ├─ UploadResumeRequest.java
│  │  │  └─ UploadResumeResponse.java
│  │  └─ enumtype
│  │     ├─ DocumentStatus.java
│  │     ├─ SectionType.java
│  │     └─ ChunkType.java
│  └─ parser
│     ├─ PdfStructureParser.java
│     └─ TextCleanPipeline.java
├─ chat
│  ├─ controller
│  │  └─ ChatController.java
│  ├─ service
│  │  ├─ ChatStreamService.java
│  │  ├─ ChatHistoryService.java
│  │  └─ PromptComposeService.java
│  ├─ repository
│  │  ├─ ChatSessionRepository.java
│  │  └─ ChatMessageRepository.java
│  └─ model
│     ├─ entity
│     │  ├─ ChatSessionEntity.java
│     │  └─ ChatMessageEntity.java
│     └─ dto
│        ├─ ChatStreamRequest.java
│        └─ ChatHistoryResponse.java
├─ rag
│  ├─ query
│  │  ├─ QueryRewriteService.java
│  │  ├─ MultiQueryService.java
│  │  └─ QueryRouterService.java
│  ├─ retrieval
│  │  ├─ VectorRetriever.java
│  │  ├─ FtsRetriever.java
│  │  ├─ HybridRetrievalService.java
│  │  └─ MetadataFilterBuilder.java
│  ├─ ranking
│  │  ├─ RerankService.java
│  │  ├─ MmrService.java
│  │  └─ FusionService.java
│  ├─ context
│  │  ├─ ContextCompressionService.java
│  │  ├─ CitationBuildService.java
│  │  └─ EvidencePack.java
│  └─ guardrail
│     ├─ CitationVerifierService.java
│     └─ NoEvidencePolicyService.java
├─ agent
│  ├─ orchestrator
│  │  └─ AgentOrchestrator.java
│  ├─ skill
│  │  ├─ IntentSkill.java
│  │  ├─ RetrievalSkill.java
│  │  ├─ AnswerSkill.java
│  │  └─ SkillContext.java
│  ├─ tool
│  │  ├─ RetrieveResumeContextTool.java
│  │  ├─ StarRewriteTool.java
│  │  └─ ResumeQaTool.java
│  └─ model
│     ├─ ToolCallResult.java
│     └─ SkillDecision.java
├─ observability
│  ├─ service
│  │  ├─ RAGTraceLogService.java
│  │  └─ MetricsService.java
│  └─ model
│     └─ RAGTraceEntity.java
└─ infra
   ├─ persistence
   │  ├─ MyBatisPlusConfig.java 或 JpaConfig.java
   │  └─ VectorSqlProvider.java
   └─ client
      ├─ LlmClient.java
      └─ EmbeddingClient.java
```

## 4. 分层职责约束
1. `controller`：只做参数校验和协议转换，不写业务逻辑。
2. `service`：编排业务流程，可调用多个领域服务。
3. `repository`：只负责数据访问，不做策略决策。
4. `rag`：只负责检索与证据构建，不直接处理 HTTP。
5. `agent`：负责 Skill 决策与 Tool 调用编排。
6. `common`：通用能力沉淀，避免业务耦合。

## 5. 类命名约定
1. 接口入参：`*Request`
2. 接口出参：`*Response`
3. 数据库实体：`*Entity`
4. 业务对象：`*DTO` 或 `*VO`（统一一种即可）
5. 策略组件：`*Service`
6. 技能组件：`*Skill`
7. 工具组件：`*Tool`

## 6. 配置文件建议
1. `application.yml`：默认配置（本地开发）。
2. `application-dev.yml`：开发环境。
3. `application-test.yml`：测试环境。
4. `application-prod.yml`：生产环境。
5. 敏感信息统一用环境变量注入，不写死在仓库。

关键配置项建议：
1. `app.rag.topK.default`
2. `app.rag.enableRewrite`
3. `app.rag.enableMultiQuery`
4. `app.rag.enableRerank`
5. `app.guardrail.noEvidencePolicy`
6. `app.llm.timeoutMs`
7. `app.sse.heartbeatMs`

## 7. 中文注释模板（强制）
### 7.1 类头注释模板
```java
/**
 * 中文说明：该类负责什么能力。
 * 输入：核心输入参数来源。
 * 输出：核心输出结果结构。
 * 策略：关键决策原则或约束。
 */
```

### 7.2 方法注释模板
```java
/**
 * 中文说明：该方法的业务目的。
 * @param xxx 参数含义与边界
 * @return 返回值语义
 * 异常策略：失败时降级或抛错规则
 */
```

### 7.3 复杂逻辑块注释模板
```java
// 中文说明：这里先做 query rewrite，再做 multi-query，
// 原因是先归一术语可以提高召回一致性，减少无效并发查询。
```

## 8. 最小实现顺序（建议）
1. 建表与实体：`resume_document`、`resume_chunk`、`chat_session`、`chat_message`。
2. `ResumeController + ResumeIngestionService` 打通上传入库。
3. `HybridRetrievalService` 打通证据召回。
4. `ChatController + ChatStreamService` 打通 SSE 输出。
5. `AgentOrchestrator + 3 Skills + 3 Tools` 接入 function calling。
6. `CitationVerifierService + NoEvidencePolicyService` 增加防幻觉能力。
7. `RAGTraceLogService` 打通日志与指标。

## 9. 代码质量红线
1. Controller 层禁止出现 SQL 或向量检索细节。
2. Service 层禁止直接拼接前端展示文案（交给 AnswerSkill）。
3. Tool 输出必须统一结构，禁止返回随意 JSON。
4. Skill 决策必须写中文注释，说明分支触发条件。
5. 核心链路无中文注释的代码不允许合并。

## 10. 首批包创建清单（可直接执行）
1. `common`、`config`、`resume`、`chat`、`rag`、`agent`、`observability`。
2. 每个包先创建最小骨架类与中文类头注释。
3. 先跑通主链路，再补增强策略（rewrite/rerank/self-rag）。


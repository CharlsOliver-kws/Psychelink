# PsycheLink 架构说明

## 总体流程

```mermaid
flowchart TD
    U[用户 · 前端肉包的聊天小站] -->|JWT| F[JwtAuthFilter]
    F -->|POST /api/chat/stream| C[ChatController]
    C --> S[ChatService<br/>Flux SSE 流式编排]
    S -->|关键词初筛 + LLM 分类| P[PsychologicalService<br/>意图识别 CHAT/CONSULT/RISK<br/>风险分级 LOW/MEDIUM/HIGH]
    P --> S
    S -->|CONSULT / RISK| K[KnowledgeBaseService]
    K --> E[EmbeddingService<br/>文本向量化]
    E --> MV[(Milvus<br/>HNSW 索引 · 101 条 QA)]
    MV --> K
    K -->|Top-3 相似问答| S
    S -->|组装 Prompt · stream| L[LLM · GLM-4.5-air<br/>OpenAI 兼容 API]
    L -->|逐 Token| C --> U
    S -->|RISK 且 MEDIUM/HIGH| M[McpEmailService<br/>@Tool 预警邮件]
    S -->|RISK 记录| R[RiskEventService<br/>风险事件落库]
    S -->|RISK 台账| X[McpExcelService<br/>@Tool Excel 台账]
    A[管理员] -->|GET /api/admin/risk-events<br/>PUT .../review| R
```

## 请求处理链路

1. **认证**：前端登录后持有 JWT；每个请求经 `JwtAuthFilter` 解析并注入 `ROLE_USER` / `ROLE_ADMIN` 权限
2. **流式对话**：`ChatController.stream()` 返回 `Flux<String>`（SSE），LLM 每 Token 即时推送，首 Token 亚秒级到达前端
3. **意图识别**：高风险关键词前置兜底 → LLM 三分类 → 解析失败回退关键词规则，保证 RISK 不漏判
4. **RAG 检索**（CONSULT/RISK）：查询向量化 → Milvus HNSW Top-3 → 知识拼接进 System Prompt
5. **风险闭环**（RISK 且 MEDIUM/HIGH）：异步发送预警邮件（`@Tool sendAlertEmail`）+ 风险事件落库 + Excel 台账（`@Tool recordRiskData`）；管理员经 `/api/admin/risk-events` 列表查询与人工复核
6. **会话留存**：完整回复在流结束后持久化，历史接口按用户数据隔离

## 意图三分支

| 意图 | 处理路径 |
|---|---|
| **CHAT** 日常闲聊 | 肉包人设 Prompt → LLM 直接回答 |
| **CONSULT** 心理咨询 | 用户问题向量化 → Milvus HNSW Top-3 检索 → 知识作为上下文 → LLM 生成回答 |
| **RISK** 危险意图 | 风险分级（LOW/MEDIUM/HIGH）→ 检索专业干预知识 → 生成干预回应 → MEDIUM/HIGH 异步触发预警邮件 + 风险事件 + 台账记录 |

## 分层结构

```
src/main/java/io/github/charlsoliver/psychelink/
├── config/        # ChatClientConfig、SecurityConfig（JWT + RBAC）、McpConfig、DataInitializer
├── controller/    # AuthController（注册/登录）、ChatController（流式对话/历史）、AdminController（风险事件/复核）
├── security/      # JwtService（签发/解析）、JwtAuthFilter
├── dto/           # 请求/响应 record
├── service/       # ChatService（流式编排）、PsychologicalService（意图/风险）、
│                  # KnowledgeBaseService + EmbeddingService + MilvusVectorStore（RAG 管线）、
│                  # McpEmailService / McpExcelService（MCP @Tool）、RiskEventService、ChatHistoryService
├── entity/        # User、ChatMessage、RiskEvent
└── repository/    # Spring Data JPA
```

## 技术选型

| 组件 | 选型 | 说明 |
|---|---|---|
| 框架 | Spring Boot 3.4.3 | Web + WebFlux（Flux 流式响应） |
| AI 框架 | Spring AI 1.0.0 GA | ChatClient 流式调用 + EmbeddingModel + MCP Server（WebMVC） |
| LLM | GLM-4.5-air | 智谱 OpenAI 兼容 API，可换任意兼容端点 |
| 向量库 | Milvus 2.5 | HNSW 索引（M=16, efConstruction=256, IP 度量），SDK v1 API |
| Embedding | embedding-2 | 智谱向量化模型，1024 维 |
| 认证 | JWT（jjwt 0.12）+ Spring Security | 无状态令牌，RBAC 双角色 |
| 数据库 | H2（默认，MySQL 模式） | 零依赖启动，可切换 MySQL |
| 邮件 | Spring Mail + QQ SMTP | 风险预警 |
| 微调 | LoRA（PEFT） | 见 [training/README.md](../training/README.md) |
| 可观测 | OpenTelemetry | Agent 自动采集 + @WithSpan 手动埋点，详见 [observability.md](observability.md) |

## 降级策略

| 故障 | 行为 |
|---|---|
| Milvus 不可用 | RAG 检索返回空上下文，对话主链路不受影响 |
| LLM 调用失败 | 意图识别回退关键词规则；对话返回固定引导话术 |
| 邮件发送失败 | 风险事件仍落库，邮件失败记录在事件上 |

## 已知局限（诚实声明）

- 管理端复核目前为 REST API，Web 控制台在 Roadmap 中
- 多模型路由（本地 Ollama / 云端 API 动态选择）在 Roadmap 中

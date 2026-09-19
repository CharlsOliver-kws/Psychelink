<p align="center">
  <img src="docs/images/banner-dark.svg" alt="PsycheLink — 心理健康 AI 陪伴助手" width="100%">
</p>

<p align="center">
  <a href="https://github.com/CharlsOliver-kws/Psychelink/actions/workflows/ci.yml"><img src="https://github.com/CharlsOliver-kws/Psychelink/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="https://github.com/CharlsOliver-kws/Psychelink/releases"><img src="https://img.shields.io/github/v/release/CharlsOliver-kws/Psychelink?color=blue" alt="Release"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <img src="https://img.shields.io/github/stars/CharlsOliver-kws/Psychelink?style=social" alt="Stars">
</p>

<p align="center">
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.4-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://spring.io/projects/spring-ai"><img src="https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F?logo=spring&logoColor=white" alt="Spring AI"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17"></a>
  <a href="https://milvus.io/"><img src="https://img.shields.io/badge/Milvus-HNSW-4ea3ff" alt="Milvus"></a>
  <a href="https://modelcontextprotocol.io/"><img src="https://img.shields.io/badge/MCP-Tools-f5a623" alt="MCP"></a>
</p>

<p align="center">
  <a href="README.md">English</a> · <b>中文</b> · <a href="docs/architecture.md">架构说明</a> · <a href="docs/observability.md">可观测性</a> · <a href="training/README.md">微调管线</a> · <a href="https://github.com/CharlsOliver-kws/Psychelink/issues">Issues</a>
</p>

---

PsycheLink 是一个基于 **Spring Boot 3 + Spring AI** 构建的心理健康 AI 陪伴助手。表面上它是一个友好的聊天应用（「肉包的聊天小站」）；实际上，每条消息都会经过意图识别、基于 **Milvus**（HNSW 索引）心理知识库的 RAG 检索、**SSE 流式**响应，以及**风险自动检测 + 邮件预警 + 人工复核** —— 构成从「识别」到「干预」的完整闭环。

<p align="center">
  <img src="docs/images/demo-chat.svg" alt="PsycheLink 演示：意图识别、RAG 流式回复与风险预警" width="92%">
</p>

> ⚠️ **免责声明**：PsycheLink 是一个技术演示项目，**不是**医疗设备，也**不能**替代专业心理帮助。如果你或你身边的人正处于危机中，请立即联系当地紧急服务或心理援助热线。

## 功能特性

| 特性 | 实现方式 |
|---|---|
| **流式对话** | `Flux<String>` SSE 接口（`/api/chat/stream`），首个 Token 到达即开始渲染 |
| **JWT 认证 + RBAC** | 无状态令牌，`ROLE_USER` / `ROLE_ADMIN` 权限隔离，用户数据级隔离 |
| **RAG 管线** | 向量化 → Milvus HNSW 相似检索（Top-3，IP 度量）→ 知识增强生成，可优雅降级 |
| **风险闭环** | 识别 → 预警邮件 → 风险事件落库 → 人工复核接口 |
| **MCP Server** | 邮件与风险记录工具通过 Model Context Protocol 暴露给外部 AI 客户端 |
| **LoRA 微调管线** | `training/`：数据准备（清洗 / 去重 / 类别均衡）→ SFT → 混淆矩阵评估（重点检查风险漏报率） |
| **绝不漏判设计** | 关键词初筛 + LLM 分类 + 规则兜底，RISK 消息绝不会被静默误判为 CHAT |
| **可观测性** | OpenTelemetry 全链路 Trace（OTel Java Agent） |
| **端点无关** | 兼容任意 OpenAI 协议端点（默认智谱 GLM） |

## 工作原理

```mermaid
flowchart LR
    U[用户] -->|消息| AUTH[JWT 认证]
    AUTH --> IC[意图识别<br/>CHAT / CONSULT / RISK]
    IC -->|CHAT| P[肉包人设闲聊]
    IC -->|CONSULT| R[RAG 检索<br/>Milvus HNSW Top-3]
    IC -->|RISK| RG[风险分级<br/>LOW / MEDIUM / HIGH]
    R --> LLM[大模型 · GLM-4.5-air]
    RG --> LLM
    RG -->|MEDIUM / HIGH| A[预警邮件<br/>+ 风险事件]
    P --> LLM
    LLM -->|SSE 流式| U
    A --> RV[管理员复核<br/>/api/admin/risk-events]
```

- **CHAT** — 以「肉包」人设进行日常闲聊
- **CONSULT** — 用户问题向量化后与 Milvus 中 101 条专业心理问答做 HNSW 相似检索（IP 度量），检索结果作为上下文生成有依据的回答
- **RISK** — 对消息进行严重程度分级；MEDIUM/HIGH 会异步触发预警邮件（以 MCP 工具形式封装）、持久化风险事件并写入 Excel 台账，同时用户收到专业干预回应；管理员通过复核接口处理事件，完成闭环

更多细节：[docs/architecture.md](docs/architecture.md)

## 快速开始

**环境要求**：JDK 17、Docker。

```bash
# 1. 启动 Milvus（etcd + MinIO + Milvus）
docker compose up -d

# 2. 配置密钥
cp .env.example .env    # 填入 ZHIPU_API_KEY、MAIL_*、ALERT_RECIPIENT

# 3. 运行（先加载环境变量，Linux/macOS: set -a; source .env; set +a）
./mvnw spring-boot:run
```

打开 **http://localhost:8088**，注册账号即可开始对话。知识库首次启动时自动导入；管理员账号在启动时引导创建（在 `.env` 中设置 `ADMIN_PASSWORD`，或查看日志中的随机密码）。

<details>
<summary><b>Windows（PowerShell）</b></summary>

```powershell
docker compose up -d
Copy-Item .env.example .env   # 编辑填入密钥
$env:ZHIPU_API_KEY="..."; $env:MAIL_USERNAME="..."; $env:MAIL_PASSWORD="..."; $env:ALERT_RECIPIENT="..."; $env:ADMIN_PASSWORD="..."
.\mvnw.cmd spring-boot:run
```
</details>

<details>
<summary><b>不启动 Milvus / 不配置 LLM Key 也能跑</b></summary>

应用照常启动 —— RAG 降级为空上下文，LLM 调用失败时回退关键词规则回复。适合本地开发调试。
</details>

## API 一览

| 接口 | 方法 | 权限 | 说明 |
|---|---|---|---|
| `/api/auth/register` | POST | – | 注册（默认 `ROLE_USER`），返回 JWT |
| `/api/auth/login` | POST | – | 登录，返回 JWT |
| `/api/chat/stream` | POST | USER | SSE 流式对话 |
| `/api/chat/history` | GET | USER | 本人历史记录（数据级隔离） |
| `/api/admin/risk-events` | GET | ADMIN | 风险事件列表，可按复核状态过滤 |
| `/api/admin/risk-events/{id}/review` | PUT | ADMIN | 人工复核：确认 + 处理意见 |
| `/sse`（MCP 端点） | – | ADMIN | MCP 工具服务（邮件 / 风险记录） |
| `/swagger-ui.html` | – | – | 交互式 API 文档（OpenAPI 3） |

错误响应统一遵循 RFC 7807（`application/problem+json`）。

## 配置项

所有密钥均来自环境变量（模板见 [.env.example](.env.example)）：

| 变量 | 必填 | 说明 |
|---|---|---|
| `ZHIPU_API_KEY` | ✅ | 大模型服务商 API Key（默认智谱） |
| `LLM_BASE_URL` | – | 任意 OpenAI 兼容端点 |
| `LLM_MODEL` | – | 默认 `glm-4.5-air` |
| `MILVUS_HOST` / `MILVUS_PORT` | – | 默认 `localhost:19530` |
| `JWT_SECRET` | – | 生产环境强烈建议设置（≥ 32 字节强随机串） |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | – | 管理员引导账号；未设密码则生成随机密码打印到日志 |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | – | SMTP 发件邮箱（QQ 邮箱为授权码，非登录密码） |
| `ALERT_RECIPIENT` | – | 风险预警接收邮箱 |

## 项目结构

<details>
<summary><b>点击展开</b></summary>

```
├── src/main/java/io/github/charlsoliver/psychelink/
│   ├── config/          # ChatClient、Security（JWT + RBAC）、MCP Server、OpenAPI、数据初始化
│   ├── controller/      # REST 接口（认证、对话、管理端）
│   ├── security/        # JWT 签发/解析 + 认证过滤器
│   ├── dto/             # 请求/响应记录
│   ├── exception/       # 全局异常处理（ProblemDetail）
│   ├── service/         # ChatService（流式编排）、PsychologicalService（意图/风险）、
│   │                    # KnowledgeBaseService + EmbeddingService + MilvusVectorStore（RAG 管线）、
│   │                    # McpEmailService（预警工具）、McpExcelService（台账工具）、
│   │                    # RiskEventService、ChatHistoryService
│   ├── entity/          # JPA 实体（User、ChatMessage、RiskEvent）
│   └── repository/      # Spring Data JPA
├── src/test/            # 21 个测试，含端到端 SSE + RBAC 集成测试
├── training/            # 数据准备 + LoRA 微调 + 评估（Python）
└── docs/                # 架构与可观测性文档
```
</details>

## Roadmap

- [ ] 多模型动态路由（敏感处理走本地 Ollama，通用问答走云端 API）
- [ ] 管理端 Web 控制台（风险事件复核界面）
- [ ] 知识库管理接口（增 / 删 / 改）

## 参与贡献

欢迎提交 Issue 和 PR，见 [CONTRIBUTING.md](CONTRIBUTING.md)。提交前请运行 `./mvnw test`（测试无需外部服务）。

## 支持一下

如果这个项目对你有帮助或启发，欢迎点一个 ⭐ Star —— 这能让更多人看到它！

## 开源协议

[MIT](LICENSE) © CharlsOliver

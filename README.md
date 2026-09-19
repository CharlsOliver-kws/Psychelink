<p align="center">
  <img src="docs/images/banner.svg" alt="PsycheLink" width="100%">
</p>

<h3 align="center">Mental-Health AI Companion<br/>Intent Classification · RAG · Streaming Chat · Risk-Alert Closed Loop</h3>

<p align="center">
  <a href="https://github.com/CharlsOliver-kws/Psychelink/actions/workflows/ci.yml"><img src="https://github.com/CharlsOliver-kws/Psychelink/actions/workflows/ci.yml/badge.svg" alt="CI"></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/License-MIT-blue.svg" alt="License: MIT"></a>
  <a href="https://spring.io/projects/spring-boot"><img src="https://img.shields.io/badge/Spring%20Boot-3.4.3-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot"></a>
  <a href="https://spring.io/projects/spring-ai"><img src="https://img.shields.io/badge/Spring%20AI-1.0.0-6DB33F?logo=spring&logoColor=white" alt="Spring AI"></a>
  <a href="https://openjdk.org/"><img src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white" alt="Java 17"></a>
  <a href="https://milvus.io/"><img src="https://img.shields.io/badge/Vector%20DB-Milvus-4ea3ff" alt="Milvus"></a>
</p>

<p align="center">
  <b><a href="README.md">English</a></b> · <a href="README.zh-CN.md">中文</a> · <a href="docs/architecture.md">Architecture</a> · <a href="https://github.com/CharlsOliver-kws/Psychelink/issues">Issues</a>
</p>

---

PsycheLink is a mental-health-aware AI companion built with **Spring Boot 3 + Spring AI**. On the surface it's a friendly chat app ("肉包的聊天小站" / Rou-Bao's Chat Corner); under the hood, every message goes through intent classification, RAG retrieval over a professional psychology knowledge base (**Milvus** with HNSW index), streaming response via **SSE**, and **automatic risk detection with email alerts + human review workflow** — a closed loop from *detection* to *intervention*.

> ⚠️ **Disclaimer**: PsycheLink is a technical demo, **not** a medical device and **not** a substitute for professional help. If you or someone you know is in crisis, please contact local emergency services or a crisis hotline immediately.

## How It Works

```mermaid
flowchart LR
    U[User] -->|message| AUTH[JWT Auth]
    AUTH --> IC[Intent Classification<br/>CHAT / CONSULT / RISK]
    IC -->|CHAT| P[Persona Chat]
    IC -->|CONSULT| R[RAG<br/>Milvus HNSW Top-3]
    IC -->|RISK| RG[Risk Grading<br/>LOW / MEDIUM / HIGH]
    R --> LLM[LLM · GLM-4.5-air]
    RG --> LLM
    RG -->|MEDIUM / HIGH| A[📧 Alert Email<br/>+ Risk Event]
    P --> LLM
    LLM -->|SSE stream| U
    A --> RV[Admin Review<br/>/api/admin/risk-events]
```

- **CHAT** — casual conversation with the "Rou-Bao" persona
- **CONSULT** — the query is embedded, matched against 101 professional Q&A pairs in Milvus (HNSW, IP metric), and the retrieved knowledge grounds the LLM's answer
- **RISK** — the message is graded for severity; MEDIUM/HIGH triggers an async alert email (exposed as an MCP tool), a persisted `RiskEvent` record, and an Excel audit log — while the user receives a carefully composed intervention response. Admins review events through a dedicated API, closing the loop.

## Features

- ✅ **Streaming chat** — `Flux<String>` SSE endpoint (`/api/chat/stream`), first token rendered in the browser as it arrives
- ✅ **JWT auth + RBAC** — stateless tokens, `ROLE_USER` / `ROLE_ADMIN` separation; users only see their own chat history; admin APIs (risk events, review, MCP endpoint) are admin-only
- ✅ **RAG pipeline** — embedding → Milvus HNSW vector search → grounded generation, with graceful degradation when Milvus is down
- ✅ **Risk management closed loop** — detection → alert email (MCP tool) → risk event persistence → human review API
- ✅ **MCP server** — email & risk-recording tools exposed over Model Context Protocol (`spring-ai-starter-mcp-server-webmvc`) for external AI clients
- ✅ **LoRA fine-tuning pipeline** — `training/` scripts for dataset preparation (Pandas cleaning / dedup / class balancing) and supervised fine-tuning with confusion-matrix evaluation focused on risk recall
- ✅ Three-way intent classification with keyword pre-filter + LLM classification + rule fallback (a RISK message is never silently misclassified as CHAT)
- ✅ Full-stack OpenTelemetry tracing via OTel Java Agent
- ✅ Works with any OpenAI-compatible LLM endpoint (default: Zhipu GLM)

## Quick Start

**Prerequisites**: JDK 17, Docker.

```bash
# 1. Start Milvus (etcd + MinIO + Milvus)
docker compose up -d

# 2. Configure your keys
cp .env.example .env    # fill in ZHIPU_API_KEY, MAIL_*, ALERT_RECIPIENT

# 3. Run (load env vars first, e.g. `set -a; source .env; set +a` on Linux/macOS)
./mvnw spring-boot:run
```

Open http://localhost:8088 — register an account and start chatting. The knowledge base auto-imports on first start. An admin account is bootstrapped at startup (set `ADMIN_PASSWORD` in `.env`, or check the log for the generated random password).

<details>
<summary>Windows (PowerShell)</summary>

```powershell
docker compose up -d
Copy-Item .env.example .env   # edit it
$env:ZHIPU_API_KEY="..."; $env:MAIL_USERNAME="..."; $env:MAIL_PASSWORD="..."; $env:ALERT_RECIPIENT="..."; $env:ADMIN_PASSWORD="..."
.\mvnw.cmd spring-boot:run
```
</details>

<details>
<summary>Running without Milvus / without an LLM key</summary>

The app starts anyway — RAG falls back to empty context, LLM calls fail over to keyword-rule responses. Useful for development.
</details>

## Configuration

All secrets come from environment variables (see [.env.example](.env.example)):

| Variable | Required | Description |
|---|---|---|
| `ZHIPU_API_KEY` | ✅ | API key for the LLM provider (Zhipu by default) |
| `LLM_BASE_URL` | – | Any OpenAI-compatible endpoint |
| `LLM_MODEL` | – | Default `glm-4.5-air` |
| `MILVUS_HOST` / `MILVUS_PORT` | – | Default `localhost:19530` |
| `JWT_SECRET` | – | Strongly recommended in production (≥ 32 bytes) |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | – | Admin bootstrap; random password logged if unset |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | – | SMTP sender (QQ Mail auth code, not login password) |
| `ALERT_RECIPIENT` | – | Who receives risk alerts |

## API Overview

| Endpoint | Method | Auth | Description |
|---|---|---|---|
| `/api/auth/register` | POST | – | Register (default `ROLE_USER`), returns JWT |
| `/api/auth/login` | POST | – | Login, returns JWT |
| `/api/chat/stream` | POST | USER | SSE streaming chat |
| `/api/chat/history` | GET | USER | Own chat history (data-isolated) |
| `/api/admin/risk-events` | GET | ADMIN | Risk events, filterable by review status |
| `/api/admin/risk-events/{id}/review` | PUT | ADMIN | Human review: confirm + note |
| `/sse` (MCP endpoint) | – | ADMIN | MCP tool server (email / risk recording) |
| `/swagger-ui.html` | – | – | Interactive API docs (OpenAPI 3) |

## Project Structure

```
├── src/main/java/io/github/charlsoliver/psychelink/
│   ├── config/          # ChatClient, Security (JWT + RBAC), MCP server, data bootstrap
│   ├── controller/      # REST endpoints (auth, chat, admin)
│   ├── security/        # JWT issue/parse + auth filter
│   ├── dto/             # request/response records
│   ├── service/         # ChatService (streaming orchestration), PsychologicalService (intent/risk),
│   │                    # KnowledgeBaseService + EmbeddingService + MilvusVectorStore (RAG),
│   │                    # McpEmailService (alert tool), McpExcelService (risk log tool),
│   │                    # RiskEventService, ChatHistoryService
│   ├── entity/          # JPA entities (User, ChatMessage, RiskEvent)
│   └── repository/      # Spring Data JPA
├── training/            # dataset prep + LoRA fine-tuning + evaluation (Python)
└── docs/                # architecture & observability guides
```

More details: [docs/architecture.md](docs/architecture.md) · Observability guide: [docs/observability.md](docs/observability.md) · Fine-tuning: [training/README.md](training/README.md)

## Roadmap

- [ ] Multi-model routing (local Ollama for sensitive processing, cloud API for general chat)
- [ ] Admin web console for risk-event review
- [ ] Vector knowledge base management API (add / update / delete)

## Contributing

Issues and PRs are welcome — see [CONTRIBUTING.md](CONTRIBUTING.md).

## License

[MIT](LICENSE)

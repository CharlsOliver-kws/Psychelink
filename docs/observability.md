# 可观测性：OpenTelemetry Span 上报

PsycheLink 通过 **OTel Java Agent** 自动采集每次对话的 Trace，并用 `@WithSpan` 注解对手动埋点环节补充细粒度 Span，用于分析延迟、Token 消耗与错误来源。

## 上报链路

```
PsycheLink → OTel Java Agent → OTLP Endpoint（聚合层 / Collector / Langfuse）
```

## 手动埋点覆盖

| Span 名 | 位置 | 覆盖环节 |
|---|---|---|
| `prompt_assembly` | `ChatService` | Prompt 组装 |
| `rag` | `KnowledgeBaseService` | 知识库向量检索 |
| `mcp_excel` | `McpExcelService` | 风险记录落盘 |
| `mcp_email` | `McpEmailService` | 预警邮件发送 |

## 启动方式

普通启动（无 Trace）：

```bash
./mvnw spring-boot:run
```

带 OTel Agent 启动（Windows 用 `scripts/run-with-otel.bat`，或在 `.env` 中配置 `OTEL_EXPORTER_ENDPOINT` 等变量后执行）：

```bash
java -javaagent:opentelemetry-javaagent.jar \
  -Dotel.service.name=psychelink \
  -Dotel.exporter.otlp.endpoint=$OTEL_EXPORTER_ENDPOINT \
  -Dotel.exporter.otlp.protocol=http/protobuf \
  -Dotel.traces.exporter=otlp \
  -Dotel.logs.exporter=none \
  -Dotel.metrics.exporter=none \
  -jar target/psychelink-*.jar
```

OTel Java Agent 可从 [opentelemetry-java releases](https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases) 下载。

## 三种上报目标

| 方式 | endpoint | 协议 |
|---|---|---|
| 自建聚合层 | `http://<host>:8001/api/ingest/otlp` | http/protobuf |
| OTel Collector | `http://<host>:4317` | grpc |
| Langfuse 云 | 按官方文档配置 `LANGFUSE_*` 环境变量 | — |

> 当前仅上报 Trace（logs/metrics exporter 设为 none），属于阶段性范围。

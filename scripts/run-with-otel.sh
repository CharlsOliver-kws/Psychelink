#!/usr/bin/env bash
# ============================================================
# PsycheLink + OpenTelemetry Agent 启动脚本
# 所有配置从环境变量读取，不在脚本中保存任何密钥
# 变量说明见仓库根目录 .env.example
# ============================================================
set -e

if [ -z "$OTEL_AGENT_JAR" ]; then
    echo "[ERROR] 请先设置 OTEL_AGENT_JAR 环境变量，指向 opentelemetry-javaagent.jar"
    echo "下载地址: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases"
    exit 1
fi

if [ -z "$OTEL_EXPORTER_ENDPOINT" ]; then
    echo "[WARN] 未设置 OTEL_EXPORTER_ENDPOINT，将不启用 OTel 上报，仅本地启动"
fi

JAR="target/psychelink-0.2.0-SNAPSHOT.jar"
if [ ! -f "$JAR" ]; then
    echo "[INFO] 未找到 $JAR，先执行打包"
    ./mvnw clean package -DskipTests
fi

java \
-javaagent:"$OTEL_AGENT_JAR" \
-Dotel.service.name=psychelink \
-Dotel.exporter.otlp.endpoint="$OTEL_EXPORTER_ENDPOINT" \
-Dotel.exporter.otlp.protocol=http/protobuf \
-Dotel.traces.exporter=otlp \
-Dotel.logs.exporter=none \
-Dotel.metrics.exporter=none \
-jar "$JAR"

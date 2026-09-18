@echo off
chcp 65001 >nul
REM ============================================================
REM PsycheLink + OpenTelemetry Agent 启动脚本
REM 所有配置从环境变量读取，不在脚本中保存任何密钥
REM 变量说明见仓库根目录 .env.example
REM ============================================================

if "%OTEL_AGENT_JAR%"=="" (
    echo [ERROR] 请先设置 OTEL_AGENT_JAR 环境变量，指向 opentelemetry-javaagent.jar
    echo 下载地址: https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases
    exit /b 1
)

if "%OTEL_EXPORTER_ENDPOINT%"=="" (
    echo [WARN] 未设置 OTEL_EXPORTER_ENDPOINT，将不启用 OTel 上报，仅本地启动
)

set JAR=target\psychelink-0.2.0-SNAPSHOT.jar
if not exist "%JAR%" (
    echo [INFO] 未找到 %JAR%，先执行打包: mvnw.cmd clean package -DskipTests
    call mvnw.cmd clean package -DskipTests || exit /b 1
)

"%JAVA_HOME%\bin\java.exe" ^
-javaagent:"%OTEL_AGENT_JAR%" ^
-Dotel.service.name=psychelink ^
-Dotel.exporter.otlp.endpoint=%OTEL_EXPORTER_ENDPOINT% ^
-Dotel.exporter.otlp.protocol=http/protobuf ^
-Dotel.traces.exporter=otlp ^
-Dotel.logs.exporter=none ^
-Dotel.metrics.exporter=none ^
-jar "%JAR%"

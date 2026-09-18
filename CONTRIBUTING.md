# 贡献指南 / Contributing

欢迎提交 Issue 和 Pull Request！

## 开发环境

- JDK 17
- Maven 3.8+（仓库自带 mvnw wrapper）
- Docker（用于启动 Milvus 向量数据库）

## 本地启动

```bash
docker compose up -d
cp .env.example .env   # 填入你的 API Key
set -a; source .env; set +a   # Windows 下可用 IDEA 运行配置注入环境变量
./mvnw spring-boot:run
```

## 运行测试

```bash
./mvnw test    # 无需外部依赖（Milvus / LLM 不可用时自动走降级路径）
```

## PR 要求

1. 代码风格与现有代码保持一致
2. 不要提交 `.env`、任何密钥或真实用户数据
3. 描述清楚改动目的与影响范围
4. 涉及心理健康相关文案时，保持专业、非评判的语气

## 安全问题

请勿在公开 Issue 中粘贴密钥或用户数据，安全问题请通过仓库 Security Advisories 反馈。

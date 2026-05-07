# spring-ai-rag-vector-poc

Spring Boot 4 + Spring AI `SimpleVectorStore` save/load + DashScope Embedding demo.

## What It Does

- Uses `SimpleVectorStore` as an in-memory vector store.
- Loads `data/simple-vector-store.json` on startup if it exists.
- Uploads text files, chunks them, embeds them with DashScope, and saves vectors locally.
- Saves again when the app shuts down.
- Provides a clean UI at `/` for upload and search.

## DashScope Config

Set the API key with an environment variable. Do not commit the key into `application.yml`.

PowerShell:

```powershell
$env:DASHSCOPE_API_KEY="your DashScope API key"
```

The config lives in `src/main/resources/application.yml`:

```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY:${AI_DASHSCOPE_API_KEY:}}
      base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
      embedding:
        options:
          model: text-embedding-v3
      chat:
        options:
          model: qwen3-max
          temperature: 0.7
          max-tokens: 2048
```

This project keeps Spring Boot 4 and implements a small DashScope `EmbeddingModel` directly. The current `spring-ai-alibaba-starter-dashscope` release still references a Spring Boot 3 auto-configuration class name, which causes startup failure on Boot 4.

## Run

```bash
mvn spring-boot:run
```

Open:

```text
http://localhost:8080/
```

## Upload

```bash
curl -X POST http://localhost:8080/rag/files \
  -F "file=@README.md"
```

## Import GitHub Repository

The UI includes an optional GitHub token field. The token is sent only for the import request and is not saved.

```bash
curl -X POST http://localhost:8080/rag/github/repositories \
  -H "Content-Type: application/json" \
  -d "{\"repositoryUrl\":\"https://github.com/owner/repo\",\"ref\":\"main\",\"token\":\"\"}"
```

If `ref` is blank, the app tries to detect the repository default branch and falls back to `main`.

Repository import limits are configured in `src/main/resources/application.yml`:

```yaml
rag:
  document:
    github-max-files: 300
    github-max-file-bytes: 1000000
```

## Search

```bash
curl "http://localhost:8080/rag/search?query=vector store persistence&topK=3"
```

## Manual Save/Load

```bash
curl -X POST http://localhost:8080/rag/vector-store/save
curl -X POST http://localhost:8080/rag/vector-store/load
```

# CodeMind — Codebase Intelligence & Repository RAG

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.1-blue.svg)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Qdrant](https://img.shields.io/badge/Qdrant-1.11.0-red.svg)](https://qdrant.tech/)
[![License](https://img.shields.io/badge/License-Apache%202.0-lightgrey.svg)](LICENSE)

CodeMind is a high-performance backend AI service that helps developers quickly comprehend and navigate unfamiliar GitHub repositories. A user submits a public GitHub repository URL; CodeMind safely clones it into an isolated temporary workspace, discovers and filters candidate files, parses Java files into semantic AST units (classes, constructors, methods), sections Markdown documentation, generates vector embeddings, and stores them in Qdrant.

Developers can then run **two-stage hybrid code search** (combining exact symbol/path matching, semantic vectors via **Reciprocal Rank Fusion**, and neural **cross-encoder reranking** via Jina AI) and ask natural-language questions with **server-verified file and line citations** or stream responses in real-time via Server-Sent Events (SSE).

---

## 1. Architecture & Execution Flow

```text
                 Developer / curl / Swagger UI / Web Client
                                    │
                                    v
                          Spring Boot REST API
                                    │
       +----------------------------+----------------------------+
       │                            │                            │
       v                            v                            v
 Repository API                Search API                     Ask API
 (Async Ingestion)             (POST /search)          (POST /ask & /ask/stream)
       │                            │                            │
       v                            v                            v
PostgreSQL State Store       RetrievalService              AnswerService
(Durable Persistence)     (Stage 1: RRF Hybrid Retrieval   (Spring AI ChatModel)
       │                   Exact Symbol/Path + Vectors)          │
       v                            │                            v
Indexing Pipeline                   v                    CitationValidator
(Git Clone -> Filter ->      RerankingService        (Server Verified Citations)
 AST & Markdown Chunking ->  (Stage 2: Cross-Encoder             │
 Batch Embeddings via          Jina AI / Fallback)               │
 Jina AI Embeddings v3)             │                            │
       │                            v                            v
       v                 Ranked Code Candidates       Grounded Answer
 Qdrant Upsert            (Highest Relevance First)   with Real Line Numbers
```

### Core Design Principles
1. **Safety First**: Repository source code is strictly data to be indexed, **NEVER executed**. No build tools (`mvn`, `gradle`, `npm`) or repository scripts are ever executed. Git cloning is isolated via `ProcessBuilder` without shell interpolation.
2. **Structure-Aware AST & Section Chunking**: Java files are parsed into semantic units (classes, methods, constructors) using `JavaParser`, preserving exact 1-indexed source line numbers. Markdown documentation is sectioned along header boundaries (`#`, `##`, `###`). Other text files use windowed text chunking.
3. **No Hallucinated Citations**: The server is the ultimate authority on citations. Chunk IDs emitted by the LLM are cross-referenced server-side against actual retrieved chunks. Hallucinations are discarded.
4. **Two-Stage Retrieval Pipeline**:
   - **Stage 1 (Candidate Generation - RRF Hybrid Search)**: Combines exact keyword matching (symbols and file paths) with cosine semantic vector search:
     $$\text{RRF Score}(d) = \frac{1}{60 + \text{rank}_{\text{keyword}}(d)} + \frac{1}{60 + \text{rank}_{\text{vector}}(d)}$$
   - **Stage 2 (Cross-Encoder Reranking)**: Employs a dedicated cross-encoder (`jina-reranker-v2-base-multilingual`) via `RerankingService` to compute direct query-to-code relevance scores, with an automatic zero-overhead fallback to RRF if disabled or unconfigured.
5. **Durable Persistence**: Repository metadata, commit SHAs, file counts, chunk counts, and indexing lifecycles are persisted in **PostgreSQL**, surviving application restarts.
6. **Real-Time Token Streaming**: Supports Server-Sent Events (`text/event-stream`) streaming tokens directly to the client as they are generated.

---

## 2. Tech Stack

- **Runtime & Language**: Java 21 / Spring Boot 4.1.2
- **Persistence**: Spring Data JPA & PostgreSQL Driver
- **AI Integration**: Spring AI (OpenAI, SenseNova, Groq, Ollama compatible)
- **Embedding Model**: Jina AI Embeddings (`jina-embeddings-v3`, 1024 dims)
- **Reranker API**: Jina AI Rerank API (`jina-reranker-v2-base-multilingual`)
- **Vector Database**: Qdrant (gRPC port `6334` / HTTP port `6333`)
- **Code Parser**: JavaParser 3.26.3
- **Documentation**: Springdoc OpenAPI / Swagger UI 2.8.5
- **Build System**: Apache Maven (via `./mvnw`)

---

## 3. Quick Start & Setup

### Prerequisites
1. **Java 21+** installed (`java -version`).
2. **Git CLI** installed and available in `PATH`.
3. **PostgreSQL**: Running on `localhost:5432` with a database named `codemind`.
   ```bash
   # Run PostgreSQL via Docker:
   docker run -d --name codemind-postgres -e POSTGRES_DB=codemind -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres -p 5432:5432 postgres:16
   ```
4. **Qdrant**: Running on `localhost:6333` / `localhost:6334`.
   ```bash
   # Run Qdrant via Docker:
   docker run -d --name codemind-qdrant -p 6333:6333 -p 6334:6334 -v qdrant_storage:/qdrant/storage qdrant/qdrant:latest
   ```

### Configuration
Set your environment variables or configure `src/main/resources/application.properties` (see `.env.example`):
```properties
# PostgreSQL
spring.datasource.url=jdbc:postgresql://localhost:5432/codemind
spring.datasource.username=postgres
spring.datasource.password=postgres

# AI Chat Provider (OpenAI, SenseNova, Ollama, etc.)
spring.ai.model.chat=openai
spring.ai.openai.api-key=your-api-key-here
spring.ai.openai.base-url=https://api.openai.com
spring.ai.openai.chat.model=gpt-4o-mini

# Jina Embeddings (OpenAI-compatible) configuration
spring.ai.model.embedding=openai
spring.ai.openai.embedding.base-url=https://api.jina.ai/v1
spring.ai.openai.embedding.api-key=your-jina-api-key-here
spring.ai.openai.embedding.model=jina-embeddings-v3
spring.ai.openai.embedding.dimensions=1024

# Cross-Encoder Reranker (Jina AI - Optional, falls back to RRF if blank)
codemind.rerank.enabled=true
codemind.rerank.api-key=your-jina-api-key-here
codemind.rerank.model=jina-reranker-v2-base-multilingual
codemind.rerank.candidate-multiplier=3
```

### Running the Application

```powershell
# Windows
.\mvnw.cmd spring-boot:run

# Linux / macOS
./mvnw spring-boot:run
```

Once started:
- **Interactive Swagger UI**: [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI 3.1 Spec**: [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **Actuator Health Check**: [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## 4. API Reference & Sample Requests

### 1. Register a Repository for Indexing
Submits a public GitHub URL to be cloned, filtered, and indexed asynchronously.

- **Endpoint**: `POST /api/v1/repositories`
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/repositories \
    -H "Content-Type: application/json" \
    -d '{"url": "https://github.com/octocat/Hello-World"}'
  ```
- **Response (`202 Accepted`)**:
  ```json
  {
    "repositoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "QUEUED",
    "message": "Repository accepted for indexing."
  }
  ```

---

### 2. Check Repository Status
Poll the background indexing progress until status reaches `READY` (or `FAILED`).

- **Endpoint**: `GET /api/v1/repositories/{id}`
- **Request**:
  ```bash
  curl http://localhost:8080/api/v1/repositories/3fa85f64-5717-4562-b3fc-2c963f66afa6
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "repositoryId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "status": "READY",
    "commitSha": "7fd1a60b01f91b314f59955a4e4d4e80d8edf11d",
    "fileCount": 1,
    "chunkCount": 1,
    "errorMessage": null
  }
  ```

---

### 3. Code Search (Two-Stage Hybrid & Reranked Retrieval)
Retrieves precision-ranked code snippets matching a query, exact symbol, or file path using Reciprocal Rank Fusion followed by cross-encoder reranking.

- **Endpoint**: `POST /api/v1/repositories/{id}/search`
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/repositories/3fa85f64-5717-4562-b3fc-2c963f66afa6/search \
    -H "Content-Type: application/json" \
    -d '{"query": "Where is the greeting handled?", "topK": 5}'
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "results": [
      {
        "chunkId": "c1f71d15-06c5-4b07-a363-2415174092b6",
        "path": "README.md",
        "startLine": 1,
        "endLine": 12,
        "symbolName": "Overview",
        "score": 0.892,
        "snippet": "# Hello World\nThis repository provides a minimal hello-world example."
      }
    ]
  }
  ```

---

### 4. Ask a Question (Grounded Q&A with Validated Citations)
Synthesizes a response strictly grounded in retrieved code evidence, accompanied by server-verified citations.

- **Endpoint**: `POST /api/v1/repositories/{id}/ask`
- **Request**:
  ```bash
  curl -X POST http://localhost:8080/api/v1/repositories/3fa85f64-5717-4562-b3fc-2c963f66afa6/ask \
    -H "Content-Type: application/json" \
    -d '{"question": "What is the purpose of this repository?", "topK": 5}'
  ```
- **Response (`200 OK`)**:
  ```json
  {
    "answer": "This repository provides a minimal Hello World reference project [CHUNK_ID: c1f71d15-06c5-4b07-a363-2415174092b6] demonstrating basic repository structure.",
    "citations": [
      {
        "path": "README.md",
        "startLine": 1,
        "endLine": 12,
        "chunkId": "c1f71d15-06c5-4b07-a363-2415174092b6",
        "reason": "Referenced in code evidence"
      }
    ],
    "limitations": [
      "Static analysis and semantic search may not reflect dynamic runtime behavior or runtime configuration."
    ]
  }
  ```

---

### 5. Stream Question Response (Real-Time SSE)
Streams tokens directly as they are generated by the LLM.

- **Endpoint**: `POST /api/v1/repositories/{id}/ask/stream`
- **Request**:
  ```bash
  curl -N -X POST http://localhost:8080/api/v1/repositories/3fa85f64-5717-4562-b3fc-2c963f66afa6/ask/stream \
    -H "Content-Type: application/json" \
    -d '{"question": "Explain the architecture of this repository.", "topK": 5}'
  ```
- **Response (`text/event-stream`)**:
  ```text
  data:Based on the codebase evidence...
  data: the application is structured...
  ```

---

## 5. Evaluation Benchmark

CodeMind includes a structured benchmark suite in the `evaluation/` directory:
- [`evaluation/questions.json`](evaluation/questions.json): 15 representative evaluation test cases covering:
  - `EXACT_SYMBOL`: Class, method, and constructor identifier queries.
  - `CONCEPTUAL`: Architectural design and multi-component questions.
  - `PATH_TRACING`: Caller-to-callee tracing across controllers, services, and repositories.
  - `NEGATIVE_UNANSWERABLE`: Verifies that the model explicitly admits lack of evidence when asked about absent features (e.g. Stripe webhooks, Redis cache).
- [`evaluation/evaluation-notes.md`](evaluation/evaluation-notes.md): Details evaluation metrics (`Recall@5`, `MRR`, `Citation Validity`, `Hallucination Resistance`) and test execution procedure.

---

## 6. Directory Structure

```text
CodeMind/
├── pom.xml                                   # Maven configuration (Java 21 & Spring Boot 4)
├── README.md                                 # Public documentation
├── .env.example                              # Environment variable configuration template
├── src/
│   ├── main/
│   │   ├── java/com/arshh/codemind/
│   │   │   ├── CodeMindApplication.java     # Spring Boot application entry point
│   │   │   ├── api/                          # REST Controllers & Global Exception Advice
│   │   │   │   ├── RepositoryController.java # POST /repositories, GET /{id}
│   │   │   │   ├── SearchController.java     # POST /{id}/search
│   │   │   │   ├── AskController.java        # POST /{id}/ask, /ask/stream
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── config/                       # Properties & Qdrant Client configuration
│   │   │   ├── dto/                          # Immutable Java 21 Records (DTOs)
│   │   │   ├── repository/                   # Spring Data JPA & PostgreSQL RepositoryEntity
│   │   │   ├── ingestion/                    # URL validation, Git clone, file discovery, IndexingService
│   │   │   ├── parsing/                      # JavaParser AST, MarkdownChunker & fallback text chunkers
│   │   │   ├── indexing/                     # Vector embeddings & Qdrant upsert/search
│   │   │   │   ├── EmbeddingService.java     # Jina AI / Spring AI vector generation
│   │   │   │   └── VectorIndexService.java   # Qdrant upsert and hybrid scroll/search
│   │   │   ├── retrieval/                    # Two-stage retrieval: RRF Hybrid, Jina Reranking, ContextAssembler
│   │   │   │   ├── RetrievalService.java     # Orchestrates Stage 1 RRF & Stage 2 Reranking
│   │   │   │   ├── RerankingService.java     # Pluggable reranking contract
│   │   │   │   ├── JinaRerankingService.java # Cross-encoder reranker via Jina AI
│   │   │   │   └── ContextAssembler.java     # Code context prompt builder
│   │   │   ├── answering/                    # AnswerService & CitationValidator
│   │   │   └── common/                       # Exceptions & shared utilities
│   │   └── resources/
│   │       └── application.properties        # Application configuration
│   └── test/
│       └── java/com/arshh/codemind/          # Unit & integration tests
└── evaluation/
    ├── questions.json                        # 15-question retrieval benchmark test cases
    └── evaluation-notes.md                   # Evaluation metrics & procedure
```

---

## 7. License

This project is licensed under the Apache License 2.0 - see the [LICENSE](LICENSE) file for details.

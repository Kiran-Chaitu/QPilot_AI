# AI TestPilot — Project Plan

> An AI-powered Software Testing & Quality Engineering Platform that acts as an **AI QA Engineer** embedded in the development team — not a replacement for testers, but a force multiplier for them.

---

## 1. Vision & Differentiators

Most "AI test generator" tools are one-shot: point at code, get tests, done. AI TestPilot differentiates by being a **continuous AI Quality Engineering Platform**:

- Multi-agent AI pipeline (not a single ChatGPT call) — specialized agents for code, API, UI, security, risk.
- **RAG-based codebase understanding** instead of dumping entire repos into a prompt.
- **Continuous mode**: on every push, re-analyze only changed modules, update risk score, regenerate regression tests, surface coverage gaps.
- Enterprise-flavored features that demo well: risk scoring, regression prediction from git diffs, security checklists, performance suggestions.

**Target users:** Developers, QA Engineers, Team Leads, Product Managers, DevOps Engineers.

**Platform type:** Web application (React/Next.js + TypeScript). Desktop-style workflows (upload, browse code, read reports, view charts, compare builds) fit a web dashboard far better than mobile. A VS Code extension is a natural v2 add-on.

---

## 2. Tech Stack

| Layer | Choice | Notes |
|---|---|---|
| Frontend | React + TypeScript (or Next.js) | Dashboard-style SPA; Next.js if SSR/SEO or file-based routing is wanted |
| Backend | Spring Boot (Java) | Orchestrator only — **never does AI reasoning itself** |
| Database | PostgreSQL | Users, projects, test cases, reports, risk scores |
| Object Storage | MinIO (self-host) or AWS S3 | Uploaded ZIPs, generated reports, artifacts |
| Cache / Queue | Redis | Session cache, job status, rate limiting, pub/sub for progress updates |
| Vector DB | pgvector (Postgres extension) / Qdrant / Weaviate | RAG embeddings store |
| AI Providers | Gemini 2.5 / OpenAI GPT-5 / Claude (hackathon) → local LLMs later (Llama, Qwen, DeepSeek, Mistral) | Pluggable provider interface so backend isn't locked to one vendor |

### Guiding principle

```
Frontend → Spring Boot (orchestration) → AI Providers (reasoning) → Database → Report
```

Spring Boot manages state, workflow, storage, and security. AI agents only reason over retrieved context and return structured output (JSON) that Spring Boot validates and persists.

---

## 3. High-Level Architecture

```
                         ┌────────────────────┐
                         │      Frontend        │
                         │  React/Next.js + TS   │
                         └──────────┬───────────┘
                                    │ REST/WebSocket
                         ┌──────────▼───────────┐
                         │      Spring Boot       │
                         │  API Gateway / Modules  │
                         └──────────┬───────────┘
        ┌───────────────┬──────────┼───────────┬────────────────┐
        ▼               ▼          ▼           ▼                ▼
   PostgreSQL        MinIO/S3     Redis     Vector DB        Git Provider
  (metadata,DB)     (artifacts)  (cache/    (RAG index)     (webhooks, diff)
                                  queue)
                                    │
                         ┌──────────▼───────────┐
                         │   AI Orchestration     │
                         │  Layer (provider-agnostic)│
                         └──────────┬───────────┘
                                    ▼
                          Multi-Agent AI Pipeline
```

### Multi-agent AI pipeline

```
                   Upload Project
                         │
                         ▼
                Project Analyzer
                         │
        ┌────────────────┼─────────────────┐
        ▼                ▼                 ▼
   Code Agent        API Agent          UI Agent
        │                │                 │
        └────────┬───────┴─────────────────┘
                 ▼
       Test Generation Agent
                 │
     ┌───────────┼─────────────┐
     ▼           ▼             ▼
  Unit Tests  API Tests   Security Tests
                 │
                 ▼
        Coverage & Risk Agent
                 │
                 ▼
          Final AI Report
```

### RAG pipeline (why: avoid blowing the LLM context window)

```
Project Upload → Chunk Files → Generate Embeddings → Store in Vector DB
      → Relevant Code Retrieval (per task/agent) → LLM → Generated Tests/Report
```

### Continuous quality engineering flow (the "wow" differentiator)

```
Developer pushes code
        ↓
Spring Boot (Git webhook) detects changed files
        ↓
AI analyzes only the modified modules (RAG re-index of diffs)
        ↓
Risk score updated
        ↓
Regression tests regenerated for affected modules
        ↓
Coverage gaps identified
        ↓
Developer receives an AI quality report (dashboard + notification)
```

---

## 4. Backend Module Breakdown (Spring Boot)

| Module | Responsibility |
|---|---|
| `auth/` | Registration/login, JWT issuance/refresh, roles (Dev/QA/Lead/PM/DevOps) |
| `user/` | User profile, org/team membership |
| `project/` | Upload (ZIP or GitHub URL), extraction, metadata storage, language detection |
| `analysis/` | Folder structure parsing, dependency detection, API endpoint discovery |
| `ai/` | Provider-agnostic AI orchestration layer; agent invocation, prompt templating, response validation |
| `testing/` | Test generation orchestration (unit/API/integration/security), storage of generated test artifacts |
| `swagger/` | OpenAPI/Swagger upload & parsing → Postman/JUnit/RestAssured generation |
| `git/` | Repo cloning, webhook handling, diff extraction for regression prediction |
| `report/` | Aggregates results into risk score, coverage %, final report; PDF/HTML export |
| `notification/` | Email/websocket/in-app notifications on analysis completion, risk alerts |
| `scheduler/` | Scheduled re-analysis jobs, cleanup jobs |
| `common/` | Shared DTOs, exception handling, security config, storage clients (MinIO/S3), Redis config |

---

## 5. Core AI Features (mapped from vision)

1. **Code Understanding** — per-file/class summarization (responsibilities, side effects).
2. **API Detection** — from `@RequestMapping`-style annotations or OpenAPI spec → generates positive/negative/edge/security test cases automatically.
3. **Unit Test Generation** — function-level test stubs + assertions.
4. **Integration Test Generation** — cross-service flows (e.g., Register → Login → Order → Pay → Verify).
5. **Edge Case Generation** — null, empty, huge input, unicode, negative, overflow, duplicate, expired token, slow network, bad headers, huge payload.
6. **Swagger/OpenAPI Testing** — generates Postman collection, JUnit, RestAssured, OpenAPI-driven tests.
7. **Regression Prediction** — git diff → AI predicts affected modules (e.g., Cart, Coupons, Invoices, Wallet, Refund, Reports).
8. **Security Testing** — SQLi, JWT issues, XSS, CSRF, broken auth, privilege escalation, IDOR, sensitive data exposure.
9. **Performance Suggestions** — detects anti-patterns (e.g., `SELECT *` in a loop) → suggests batching/caching/pagination/indexing.
10. **Risk Score** — weighted score (e.g., 85%) with reasons (auth changed, payment modified, DB migration, critical endpoint touched).

---

## 6. Data Model (high-level entities)

- `users` (id, email, password_hash, role, org_id, created_at)
- `projects` (id, owner_id, name, source_type[zip/git], repo_url, storage_path, language, created_at)
- `project_files` (id, project_id, path, language, chunk_count)
- `code_embeddings` (id, project_file_id, chunk_text, embedding_vector, metadata)
- `api_endpoints` (id, project_id, method, path, controller_ref, summary)
- `test_cases` (id, project_id, endpoint_id/file_id, type[unit/api/integration/security], name, code, status)
- `analysis_runs` (id, project_id, trigger[manual/git-push], status, started_at, completed_at)
- `risk_scores` (id, analysis_run_id, score, reasons_json)
- `reports` (id, project_id, analysis_run_id, coverage_pct, storage_path, generated_at)
- `notifications` (id, user_id, type, payload, read_at)

---

## 7. High-Level API Surface

```
POST   /api/auth/register
POST   /api/auth/login
POST   /api/auth/refresh

POST   /api/projects/upload            (zip or git url)
GET    /api/projects/{id}
GET    /api/projects/{id}/analysis
POST   /api/projects/{id}/swagger      (upload OpenAPI spec)

POST   /api/projects/{id}/analyze      (trigger analysis)
GET    /api/analysis/{runId}/status    (progress polling / websocket)

GET    /api/projects/{id}/tests
GET    /api/tests/{id}/code

GET    /api/projects/{id}/report
GET    /api/projects/{id}/report/download

POST   /api/git/webhook                (push events → continuous re-analysis)
GET    /api/notifications
```

---

## 8. Hackathon MVP Scope

**Backend (Spring Boot)**
- JWT authentication
- Project upload (ZIP or GitHub URL)
- Project extraction + metadata analysis (language, structure, dependencies, API discovery)
- Swagger/OpenAPI upload & parsing
- AI orchestration service (provider-agnostic, one provider wired for demo)
- Report generation (risk score + coverage + downloadable report)
- PostgreSQL persistence

**Frontend (React + TypeScript)**
- Login/Register
- Dashboard (list of projects, status)
- Upload project / API spec
- Analysis progress page (polling or websocket)
- Generated test cases view (code viewer)
- Risk & coverage dashboard (charts)
- Download report

**AI**
- Repository summarization
- API test generation
- Unit test suggestions
- Edge-case generation
- Risk analysis
- Security checklist generation

**Explicitly out of scope for MVP:** microservices split, local LLM hosting, VS Code extension, full CI/CD git-push automation (can be stubbed/demoed via manual "re-analyze" trigger instead of live webhook infra), multi-tenant org billing.

---

## 9. Phased Roadmap

### Phase 0 — Foundation (Setup)
- Monorepo scaffold: `backend/` (Spring Boot, Gradle/Maven), `frontend/` (React+TS or Next.js)
- Docker Compose: PostgreSQL, Redis, MinIO, (pgvector or standalone vector DB)
- CI skeleton (build + test on push)
- Base Spring Boot modules scaffolded per §4; base React app with routing + auth shell

### Phase 1 — Auth & Project Upload
- `auth/`: JWT register/login/refresh, role model
- `project/`: ZIP upload → extraction → storage in MinIO; GitHub URL clone
- Metadata extraction: detect language, list files, parse `pom.xml`/`package.json`/`requirements.txt` for dependencies
- Frontend: Login/Register pages, Upload page, Project list dashboard

### Phase 2 — Analysis Engine
- `analysis/`: folder structure walker, API endpoint discovery (annotation parsing for Spring; path parsing for OpenAPI)
- `swagger/`: OpenAPI/Swagger upload + parsing
- Chunking + embeddings pipeline → vector DB (RAG foundation)
- Frontend: Analysis progress page with live status

### Phase 3 — AI Orchestration Layer
- Provider-agnostic `ai/` module (interface + adapter for Gemini/OpenAI/Claude)
- RAG retrieval service: given a task (e.g., "generate tests for UserService"), fetch relevant chunks from vector DB
- Prompt templates per agent (Code Agent, API Agent, UI Agent)
- Structured JSON response contracts + validation

### Phase 4 — Test Generation Agents
- Test Generation Agent orchestrating: Unit Tests, API Tests (incl. edge cases & security), Integration Tests
- Swagger-driven generation: Postman collection, JUnit, RestAssured
- `testing/` module persists generated test code + metadata
- Frontend: Generated test cases viewer (syntax-highlighted code viewer, filter by type)

### Phase 5 — Coverage, Risk & Reporting
- Coverage & Risk Agent: aggregate signals into coverage % and risk score with reasons
- Regression prediction from git diff (basic version: map changed files → related modules via import/dependency graph + AI reasoning)
- `report/` module: final report assembly, PDF/HTML export, download endpoint
- Frontend: Risk & coverage dashboard (charts — e.g., recharts/chart.js), download report button, build comparison view

### Phase 6 — Continuous Mode & Polish (stretch goal)
- `git/` webhook receiver → trigger incremental analysis on push (diff-only re-embedding)
- `notification/` module: in-app + email notification on analysis completion / high risk
- `scheduler/`: periodic re-analysis, cleanup jobs
- UI polish, demo script, seed demo project, performance pass

### Post-hackathon / Future
- Split into microservices (Gateway, User, Project, AI, Report, Notification, Git, Testing, Analytics services)
- Local LLM support (Llama, Qwen, DeepSeek, Mistral) behind the same provider interface
- VS Code extension surfacing inline test suggestions and risk annotations
- Multi-tenant orgs, billing, fine-grained RBAC

---

## 10. Risks & Mitigations

| Risk | Mitigation |
|---|---|
| LLM context limits on large repos | RAG chunking + embeddings from day one (Phase 2), never send whole repo |
| AI output not valid/compilable test code | Enforce structured JSON + schema validation; post-generation compile/lint check where feasible |
| Vendor lock-in to one AI provider | Provider-agnostic interface in `ai/` module from the start |
| Time pressure (hackathon) | MVP scope explicitly trims microservices, local LLM, live git webhooks — keep continuous mode as a stretch/demo-only stub |
| Git webhook infra complexity | For demo, allow manual "Re-analyze" trigger that simulates the push-triggered flow |

---

## 11. Demo Script (for presentation)

1. Register/login → upload a sample project (e.g., a small food-ordering Spring Boot app) as ZIP.
2. Show extraction + metadata detection (language, endpoints, dependencies) live.
3. Trigger analysis → show multi-agent pipeline progress (Code Agent → API Agent → Test Generation → Risk Agent).
4. Open generated test cases (unit, API, security, edge cases) in the code viewer.
5. Show risk & coverage dashboard with charts.
6. Simulate a code change (git diff) → show regression prediction + updated risk score.
7. Download final AI quality report.

---

## 12. Immediate Next Steps

1. Scaffold monorepo (`backend/`, `frontend/`) and Docker Compose for Postgres/Redis/MinIO.
2. Stand up `auth` module (JWT) + basic React login/register screens.
3. Implement project upload + extraction + metadata detection.
4. Wire one AI provider behind a provider-agnostic interface and get a trivial "summarize this file" call working end-to-end before building the full agent pipeline.

# Cognizant Hackathon

| Folder      | Stack                                  | Port |
|-------------|----------------------------------------|------|
| `frontend/` | React (Vite)                           | 5173 |
| `backend/`  | Spring Boot 4 · Java 21 · Maven wrapper | 8080 |

## Run the backend
```bash
cd backend
./mvnw spring-boot:run
```
Test: http://localhost:8080/api/hello

## Run the frontend
```bash
cd frontend
npm install   # first time only
npm run dev
```
Open http://localhost:5173 — requests to `/api/*` are proxied to the backend.

## Frontend structure
```
frontend/src/
├── api/
│   ├── client.js      # the only place the UI gets data, one function per future endpoint
│   └── mockData.js    # sample data (simulated) until the backend is connected
├── components/        # Layout (sidebar + top bar), RunsTable, shared UI bits, Logo
├── pages/             # Dashboard, Runs, RunReview, Placeholder
├── utils/format.js
├── index.css          # colour tokens + base styles
└── styles.css         # component styles + responsive rules
```
Routes: `/` dashboard · `/runs` all runs · `/runs/:runId/review` test run review (sample: `TR-2026-0142`).
Other sidebar items show a "planned" placeholder.

## Backend structure
```
backend/src/main/java/com/cognizant/backend/
├── controller/   # REST endpoints (DashboardController, TestRunController, ReviewController)
├── service/      # business logic (DashboardService, TestRunService, ReviewService)
├── repository/   # Spring Data JPA (TestRunRepository, ReviewCaseRepository, HumanReviewRepository)
├── entity/       # JPA entities + enums (TestRun, ReviewCase, HumanReview, ...)
├── dto/          # request/response records (JSON shape matches the frontend)
├── exception/    # 404 / 400 as problem-detail JSON
└── config/       # CORS, DataSeeder (simulated sample data)
```
Database: MySQL `cognizant` on localhost:3306 (settings in `backend/src/main/resources/application.properties`).
Tables are created by Flyway from `backend/src/main/resources/db/migration/V1__create_core_tables.sql`
on first start. Add schema changes as a new file (`V2__...sql`); never edit V1 once applied.
Tests use an in-memory H2 database (`backend/src/test/resources/application.properties`) and never touch MySQL.

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/dashboard` | Stats, recent runs, trend, coverage, failure reasons, insights |
| GET  | `/api/runs` | All runs, newest first |
| GET  | `/api/runs/{id}/review` | Review case detail + review history |
| POST | `/api/runs/{id}/review` | Submit a human review (`approve` / `request_fix` / `retest`) |
| GET  | `/api/reviews/pending/count` | Cases waiting for review |

Run tests: `cd backend && ./mvnw test`

## AI layer (Spring AI + Claude)
```
backend/src/main/java/com/cognizant/backend/ai/
├── config/AiConfig.java        # one ChatClient per role (system prompt + effort)
├── config/AiProperties.java    # kavasam.ai.* settings (model, max tokens, effort per role)
├── HealthAssistantService.java # the sample health assistant being tested
├── JudgeService.java           # scores an answer against must-do / must-not-do criteria
├── InvestigatorAgent.java      # agent: picks follow-ups and evidence lookups via tools
├── tools/InvestigatorTools.java# @Tool methods the agent can call
├── AiController.java           # /api/ai endpoints
└── dto/                        # request/response records
backend/src/main/resources/prompts/   # system prompts for each role
```
Roles and models: the health assistant under test runs on **Gemini** (`kavasam.ai.assistant-model`);
the judge and investigator run on **Claude** (`kavasam.ai.judge-model`), so no model grades itself.

Keys go in `backend/.env` (git-ignored; copy `backend/.env.example`) or in environment variables:
```
GEMINI_API_KEY=...
ANTHROPIC_API_KEY=...
```
Start the backend from the `backend/` folder so `.env` is found. Without a key the app still starts
and the affected `/api/ai/*` endpoints answer 503. Model calls time out after
`kavasam.ai.timeout-seconds`. Tests never call the real APIs.

| Method | Path | Purpose |
|---|---|---|
| GET  | `/api/ai/status` | Whether a key is set, and the model/effort in use |
| POST | `/api/ai/ask` | Ask the health assistant `{question, language}` |
| POST | `/api/ai/ask/multilingual` | Same question in each language `{questions: {EN, TA, HI}}` |
| POST | `/api/ai/judge` | Judge an answer `{question, answer, language, mustDo[], mustNotDo[]}` |
| POST | `/api/ai/investigate` | Run the investigator agent `{question, language, mustDo[], mustNotDo[]}` |

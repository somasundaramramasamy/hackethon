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

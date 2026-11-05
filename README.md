# Mutex BI

A deterministic, high-integrity sales business intelligence web application. Upload spreadsheet data (CSV or XLSX), audit validation outcomes row-by-row, review financial KPIs (revenue, gross profit, gross margin %, units sold, revenue growth) verified against rigorous mathematical oracles, and export hardened reports.

![Tech Stack](https://img.shields.io/badge/Java-21-blue?style=flat&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.2-brightgreen?style=flat&logo=springboot)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-blue?style=flat&logo=postgresql)
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat&logo=react)
![TypeScript](https://img.shields.io/badge/TypeScript-5.9-blue?style=flat&logo=typescript)
![Vite](https://img.shields.io/badge/Vite-8.3-646CFF?style=flat&logo=vite)
![Tailwind CSS](https://img.shields.io/badge/Tailwind%20CSS-4.2-38B2AC?style=flat&logo=tailwind-css)

---

## Key Features

- **Strict Data Integrity:** Stored generated columns calculate `line_revenue` and `line_gross_profit` directly in PostgreSQL. Exact `NUMERIC` types, `BigDecimal` scale 2, and `HALF_UP` rounding ensure zero precision drift or floating-point anomalies.
- **Robust Ingestion Pipeline:** Streamed CSV and event-driven XLSX (Apache POI SAX) parsing supporting up to 100,000 rows within target memory bounds. Every rejected row produces explicit row-number-level diagnostics.
- **Auditable Reconciliation:** Valid, invalid, and duplicate row counts reconcile completely (`valid + invalid + duplicate = total`).
- **Complete Analytics Engine:** Summary KPIs, comparative revenue growth periods, monthly trends, category and regional breakdowns, and product rankings.
- **Formula-Injection Defended Exports:** Records CSV and multi-sheet XLSX summary workbooks streamed with Apache POI `SXSSFWorkbook(100)` and hardened via single-quote sanitization (`=`, `+`, `-`, `@`, `\t`, `\r`) for text cells while keeping numeric values unadulterated.
- **Defense-in-Depth Security:** HttpOnly SameSite=Lax session cookies, CSRF protection, BCrypt cost 12 hashing, rate-limited auth endpoints, and multi-tenant IDOR protection returning HTTP 404 (never 403) for foreign resources.

---

## Architecture Overview

```text
┌────────────────────────────────────────────────────────┐
│                   Frontend (React + TS)                │
│         Tailwind CSS · TanStack Query · Recharts       │
└───────────────────────────┬────────────────────────────┘
                            │ REST / Session / CSRF
┌───────────────────────────▼────────────────────────────┐
│              Backend (Spring Boot 4.1.2)               │
│  Controllers ──► Services ──► Repositories / JDBC       │
│        └── DatasetAccessService (404 IDOR Seam)        │
└───────────────────────────┬────────────────────────────┘
                            │ JDBC Connection Pool
┌───────────────────────────▼────────────────────────────┐
│                    PostgreSQL 17.x                     │
│   Flyway Migrations · Stored Generated Columns · B-Tree│
└────────────────────────────────────────────────────────┘
```

---

## Quick Start with Docker

The fastest way to run the entire Mutex BI stack:

```bash
# 1. Clone repository
git clone https://github.com/jul2264/Mutex.git
cd Mutex

# 2. Setup environment variables
cp .env.example .env

# 3. Launch full stack via Docker Compose
docker compose up -d --build
```

Access points:
- **Web UI:** [http://localhost](http://localhost)
- **Backend API:** [http://localhost:8080](http://localhost:8080)
- **Health Check:** [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health)

---

## Local Development Setup

### Prerequisites
- **Java 21 (LTS)**
- **Node.js 20+**
- **Docker Desktop** (for development PostgreSQL)

### 1. Database
```bash
docker compose up -d db
```

### 2. Backend
```bash
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```
The backend starts at `http://localhost:8080`. Flyway automatically applies all schema migrations.

### 3. Frontend
```bash
cd frontend
npm ci
npm run dev
```
The Vite development server runs at `http://localhost:5173` and proxies `/api` requests to `http://localhost:8080`.

---

## Verification & Testing Suite

Run all verification checks across both subprojects:

### Backend Checks
```bash
cd backend
# Unit tests
./mvnw test
# Full verification (ArchUnit architecture rules, Testcontainers PostgreSQL ITs, 100k-row benchmarks)
./mvnw verify
```

### Frontend Checks
```bash
cd frontend
npm run lint         # Oxlint
npm run typecheck    # TypeScript strict check
npm run test         # Vitest unit and mock API tests
npm run build        # Production bundle build
```

---

## Project Structure

```text
Mutex/
├── backend/
│   ├── src/main/java/com/example/bi/
│   │   ├── config/            # Security, WebMvc, Async, Clock beans
│   │   ├── domain/            # JPA entities and domain enums
│   │   ├── dto/               # Immutable API request/response records
│   │   ├── exception/         # RFC 9457 Problem Detail exceptions
│   │   ├── repository/        # Spring Data JPA repositories
│   │   ├── service/           # Ingestion, Analytics, Reports, Security
│   │   └── web/               # Spring MVC REST Controllers
│   ├── src/main/resources/
│   │   └── db/migration/      # Flyway SQL migrations (V1 to V3)
│   └── src/test/              # JUnit 5, ArchUnit, Testcontainers IT suite
├── frontend/
│   ├── src/
│   │   ├── api/               # Typed network boundary with Zod validation
│   │   ├── components/        # Analytics, Ingestion, Reports, UI components
│   │   ├── hooks/             # Custom React hooks (auth, query hooks)
│   │   ├── lib/               # Utility functions (date formatting, currency)
│   │   └── pages/             # Dashboard, Datasets, Ingestion, Reports pages
│   └── tests/                 # Vitest test suite
├── docs/                      # Architectural specs, KPI formulas, security matrix
├── sample-data/               # Clean & dirty test spreadsheets
└── docker-compose.yml         # Container orchestration specification
```

---

## Demo Script

See [`docs/demo-script.md`](file:///docs/demo-script.md) for a complete step-by-step walkthrough covering user registration, dirty CSV error audit, KPI verification, and XLSX export.

---

## License

This project is licensed under the MIT License.

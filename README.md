# Mutex — Business Intelligence & KPI Analytics Platform

Mutex is a robust, audit-grade sales analytics platform built with Spring Boot 4, PostgreSQL, and React. It provides an automated pipeline: upload sales spreadsheets (CSV/XLSX), inspect data quality, review and confirm imports, and explore interactive KPI dashboards with reproducible metrics and exports.

## Core Stack

- **Backend:** Java 21, Spring Boot 4.1.2, Spring MVC, Spring Data JPA, JdbcTemplate, Flyway, PostgreSQL.
- **Frontend:** React 19, TypeScript (strict), Vite, Tailwind CSS v4, TanStack Query, Recharts.
- **Testing:** JUnit 5, Mockito, Testcontainers (PostgreSQL 17), Vitest, Playwright.
- **Infrastructure:** Docker, Docker Compose.

## Prerequisites

- **Java:** JDK 21+
- **Node.js:** Node LTS (v22+ or v26) & npm
- **Docker:** Docker Desktop with Compose v2

## Getting Started

### 1. Start Database
```bash
cp .env.example .env     # first time only
docker compose up -d db
docker compose ps
```

### 2. Run Backend
```bash
cd backend
./mvnw spring-boot:run "-Dspring-boot.run.profiles=dev"
```
The API is available at `http://localhost:8080`, and health endpoint at `http://localhost:8080/actuator/health`.

### 3. Run Frontend
```bash
cd frontend
npm install
npm run dev
```
The frontend is available at `http://localhost:5173`, proxying `/api` requests to port 8080.

## Verified Fixture F1 Oracle

- **Revenue:** 9,000.00
- **Gross Profit:** 3,000.00
- **Gross Margin %:** 33.33%
- **Units Sold:** 15
- **Orders:** 2
- **Line Items:** 2

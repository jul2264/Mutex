# Pinned Versions & Stack Policy

| Technology | Target Version | Managed By | Notes |
|---|---|---|---|
| **Java** | 21 (LTS) bytecode target | Maven compiler | Runs on JDK 21+ (development environment has JDK 24). |
| **Spring Boot** | 3.4.x / 4.x | Spring Boot BOM | Generated via Spring Initializr. Starters use BOM-managed dependencies. |
| **PostgreSQL** | 18 / 17 | Docker Compose / Host | Development environment has PostgreSQL 18 running. Testcontainers uses `postgres:17` or `18`. |
| **Apache Commons CSV** | 1.12.0 | Explicit pin | Pin latest stable release from Maven Central. |
| **Apache POI** | 5.3.0 (`poi-ooxml`) | Explicit pin | Pin latest stable release for event-based XLSX parsing. |
| **springdoc-openapi** | 2.8.x (Boot 3) / 3.x (Boot 4) | Explicit pin | Must match Spring Boot major version. |
| **Node.js** | LTS (22+ / 26) | `.nvmrc` / Host | Current host runtime is Node v26.13.0, npm 11.6.4. |
| **React** | 19 / 18 | `package.json` | React + TypeScript strict mode. |
| **Vite** | 6.x | `package.json` | Bundler & dev proxy. |
| **Tailwind CSS** | 4.x | `package.json` | Theme tokens aligned with Notion design in `DESIGN.md`. |
| **TanStack Query** | 5.x | `package.json` | Server state management. |
| **React Hook Form** | 7.x | `package.json` | Form state management. |
| **Zod** | 3.x | `package.json` | Schema validation at API boundaries. |
| **Recharts** | 2.x | `package.json` | Visualizations. |
| **Vitest** | 5.x | `package.json` | Unit test runner for frontend. |
| **Docker Engine** | 29.x | Docker Desktop | Docker Compose v2 for local DB and deployment. |


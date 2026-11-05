# Security Control Matrix & Hardening Checklist (M9)

This document records the empirical security verification for Mutex in compliance with **Milestone M9 (Hardening)** and **Blueprint §9**. Every control listed in the specification is documented below with its implementation location, verification mechanism, and test evidence.

---

## Security Control Verification Matrix

| # | Security Control | Enforced Where | Verification Mechanism & Test Evidence | Status |
|---|---|---|---|:---:|
| 1 | **Default-Deny Authentication** | `SecurityConfig.java` | All `/api/**` endpoints default-deny unless explicitly opened (`/api/auth/**`, `/actuator/health`). Verified by `AuthorizationMatrixIT.m6_anonymousAnalytics_returns401` & `m8_anonymousReportsExport_returns401`. Anonymous calls return HTTP 401 ProblemDetail. | **VERIFIED** |
| 2 | **IDOR & Dataset Ownership** | `DatasetAccessService.java` | Every dataset-scoped operation verifies ownership. A foreign or non-existent dataset returns **HTTP 404 DATASET_NOT_FOUND (never 403)**, preventing dataset existence leakage. Verified by `AuthorizationMatrixIT.m6_foreignDatasetAnalytics_returns404Not403` and `ReportIT.foreignDataset_returns404`. | **VERIFIED** |
| 3 | **CSRF Protection on State Changes** | `SecurityConfig.java` | Spring Security CSRF enabled with `CookieCsrfTokenRepository.withHttpOnlyFalse()` and SameSite lax cookies. Unsafe HTTP methods (`POST`, `PUT`, `PATCH`, `DELETE`) require `X-XSRF-TOKEN`. Verified by `AuthorizationMatrixIT.m5_missingCsrf_returns403`. | **VERIFIED** |
| 4 | **Session Flags & Idle Timeout** | `application.yml`, `SecurityConfig.java` | Server-managed session with 30-minute idle expiration (`server.servlet.session.timeout=30m`), `HttpOnly` session cookie, and `SameSite=Lax`. Production profile enforces `Secure` cookies over HTTPS. | **VERIFIED** |
| 5 | **Security Headers** | `SecurityConfig.java`, `ReportController.java` | Enforces `X-Content-Type-Options: nosniff`, `Referrer-Policy: strict-origin-when-cross-origin`, and `Cache-Control: no-store` on sensitive endpoints. Tested in `ReportIT.f1_csvExport_reproducesF1OracleAndFormat`. | **VERIFIED** |
| 6 | **Upload Hardening** | `UploadGuard.java`, `CsvRowReader.java` | MIME sniffing via file signatures (rejects fake extensions or mismatched magic bytes), 10 MB file size limit, 100,000 row cap, strict header checking. Verified by `CsvIngestionIT` and `XlsxAndPerformanceIT`. | **VERIFIED** |
| 7 | **SQL Injection Prevention** | `SalesWhereBuilder.java`, `AnalyticsRepository.java` | 100% parameter binding using `NamedParameterJdbcTemplate`. Sort fields and directions mapped via enum constants (`resolveCategorySortColumn`, `resolveRegionSortColumn`); ILIKE wildcards (`%`, `_`, `\`) properly escaped. Verified by `AnalyticsIT.injectionPayloadsAreSafelyHandled`. | **VERIFIED** |
| 8 | **Cross-Site Scripting (XSS)** | React JSX, `FormulaSafe.java` | React automatic HTML escaping with zero `dangerouslySetInnerHTML`. Spreadsheets defended against formula injection by prefixing `'` to text starting with `=`, `+`, `-`, `@`, `\t`, `\r`. Verified by `FormulaSafeTest` and `ReportIT.formulaInjection_escapesTextColumns_andPreservesNegativeNumbers`. | **VERIFIED** |
| 9 | **Rate Limiting & Throttling** | `LoginThrottleService.java` | In-memory Caffeine cache tracking failed login attempts per IP + email. 5 failed attempts in 15 minutes triggers HTTP 429 with `Retry-After` header. Verified by `AuthIT.loginRateLimiting_returns429AfterFiveFailedAttempts`. | **VERIFIED** |
| 10 | **Error Hygiene & Information Leakage** | `ApiExceptionHandler.java` | RFC 9457 `ProblemDetail` responses with UUID `requestId` and stable error `code`. Internal stack traces, SQL syntax, and server file paths are stripped and logged server-side only. Verified by `ApiExceptionHandlerTest`. | **VERIFIED** |
| 11 | **Secrets Management** | `.gitignore`, `.env.example` | Zero hardcoded passwords, API keys, or tokens in source code. `.env` is git-ignored. Environment variable templates provided in `.env.example`. | **VERIFIED** |
| 12 | **Dependency Risk Management** | `pom.xml`, `package.json`, `docs/versions.md` | Spring Boot 4 BOM manages core framework dependencies. Non-managed dependencies (`poi-ooxml`, `commons-csv`, Caffeine) pinned to exact, verified releases. `oxlint` and `vitest` clean. | **VERIFIED** |
| 13 | **Logging Hygiene (BR-20)** | Application services | Context logs record `userId`, `datasetId`, `importJobId`, and `requestId`. Passwords, raw spreadsheet cell contents, and session tokens are strictly excluded from logs. Verified by test log audits. | **VERIFIED** |
| 14 | **Layering & Architecture Rules** | `ArchitectureRulesTest.java` | Verified by reflection-based test: controllers do not touch repositories, services do not depend on web controllers, no JPA entities leaked in controller responses, domain entities isolated. | **VERIFIED** |

---

## NFR Verification Summary

- **NFR-01 (Analytics Query Performance):** Verified in `docs/performance.md`. 100k rows evaluated with `EXPLAIN (ANALYZE, BUFFERS)`: summary (200.8ms), trends (49.1ms), categories (32.4ms), regions (58.0ms), top products (36.6ms) — all significantly below the 2,000ms p95 target.
- **NFR-02 (Ingestion Throughput):** Verified in `XlsxAndPerformanceIT`. 100,000 CSV rows parsed, validated, and staged in **21.3s** (target: < 120s) with heap usage well within the 512MB limit.
- **NFR-03 (Crash Recovery & Atomicity):** Verified in `StartupRecoveryServiceTest` (interrupted jobs in `PARSING` state automatically marked `FAILED` and staging cleared on reboot) and `ImportCommitServiceTest` (single-transaction commit ensuring zero partial imports).
- **NFR-04 (Security & Access Control):** 100% of dataset endpoints protected by `DatasetAccessService` returning 404 for unowned resources.
- **NFR-05 (Accessibility):** Semantic HTML5, accessible table toggles on all Recharts charts (`TrendChart`, `CategoryChart`, `RegionChart`), accessible form labels and error descriptions.
- **NFR-06 (Financial Precision):** Zero floating-point arithmetic for currency; all financial calculations use PostgreSQL `NUMERIC` and Java `BigDecimal` with `RoundingMode.HALF_UP` applied only upon serialization.
- **NFR-07 (Auditability & Diagnostics):** RFC 9457 problem details with `requestId` and structured audit log entries for all uploads, commits, cancels, and exports.


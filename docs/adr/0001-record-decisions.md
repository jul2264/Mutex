# ADR-0001: Baseline Architectural Decisions (D-01 to D-27)
Status: accepted
Date: 2026-11-04

## Context
Building the Mutex Business Intelligence & KPI Analytics Platform requires resolving initial scope boundaries, architectural principles, data precision rules, security constraints, and data ingestion mechanics to avoid mid-implementation rework and security oversights.

## Decision
We lock the 27 architectural decisions formulated in the Implementation Blueprint:

1. **D-01:** One fixed sales schema (8 required columns); no arbitrary SQL, dynamic schemas, AI insights, streaming analytics, or microservices in v1.
2. **D-02:** Excluded from MVP: scheduled reports, PDF exports, append/retry imports, multi-tenant sharing, and custom dashboard builders.
3. **D-03:** Single MVP role: `ANALYST`. The uploader owns what they upload.
4. **D-04:** Ownership seam from day one via `datasets.owner_id`. Every dataset-scoped path calls `DatasetAccessService`; non-owned datasets return HTTP 404 (never 403) to prevent existence leakage.
5. **D-05:** Server-side sessions with HttpOnly, SameSite=Lax cookies, CSRF protection, and shared origin (via Vite proxy in dev, Nginx in prod). No JWTs in browser storage.
6. **D-06:** Passwords hashed with BCrypt strength 12 (10-72 bytes UTF-8); generic authentication error messages; Caffeine rate-limiting (5 failures / 15 min returns 429).
7. **D-07:** Dataset lifecycle starts as `DRAFT` upon upload, transitioning to `ACTIVE` upon first confirmed import.
8. **D-08:** Two-phase asynchronous import: upload -> validation staging -> explicit review and commit. At most one non-terminal import job per dataset enforced by partial unique index.
9. **D-09:** Explicit job state machine: `PARSING` -> `VALIDATED` -> `COMPLETED`, or `FAILED` / `CANCELLED` / `EXPIRED`. Confirm executes in a single database transaction.
10. **D-10:** Duplicate row definition: exact match on all 8 normalized fields (`row_hash` SHA-256). First row stays `VALID`; later rows become `DUPLICATE_CANDIDATE`.
11. **D-11:** Inclusive ISO dates across the API converted once in service to half-open interval `[from, to + 1 day)`. `order_date` is a timezone-less `DATE`.
12. **D-12:** Trend metrics bucketed by calendar month, zero-filling missing months.
13. **D-13:** Streaming readers: Commons CSV for CSV, POI event API for XLSX (first worksheet only). Hard caps: 10MB file, 100K data rows, 50 columns.
14. **D-14:** Spring Data JPA for `users`, `datasets`, and `import_jobs`. `JdbcTemplate` batch writes and set-based SQL for `import_staging_rows` and `sales_records`.
15. **D-15:** Money representation: `NUMERIC` in PostgreSQL, `BigDecimal` in Java, decimal strings in JSON with 2 decimal places. Rounding HALF_UP at output only.
16. **D-16:** Exports sanitize text columns against spreadsheet formula injection (`=`, `+`, `-`, `@`, tab, CR prefixing `'`); numeric columns left unadulterated.
17. **D-17:** Single ISO 4217 currency per dataset (default `INR`, ₹). No mixed-currency conversions in v1.
18. **D-18:** Import policy selected at upload: `IMPORT_VALID_ONLY` (default) or `REJECT_FILE_ON_ANY_ERROR`.
19. **D-19:** UUIDs for public business entity identifiers; `BIGINT GENERATED ALWAYS AS IDENTITY` for high-volume row tables.
20. **D-20:** Error responses formatted strictly according to RFC 9457 Problem Details with stable machine `code` and `requestId`.
21. **D-21:** Zero-based pagination with default size 50, maximum 100, and stable ID tiebreakers.
22. **D-22:** Frontend state: TanStack Query for server state, URL search parameters for filters, React Hook Form + Zod for forms. No external global store.
23. **D-23:** Line revenue and gross profit defined exclusively in PostgreSQL stored generated columns and Java `KpiCalculator`.
24. **D-24:** Staging rows purged upon job completion or cancellation. Issue rows retained up to 10,000 per job.
25. **D-25:** Tracing context: Request ID, User UUID, and Import Job ID present on logs. Raw cell values, emails, and passwords never logged.
26. **D-26:** Containerization via Docker Compose (db, backend, frontend with Nginx).
27. **D-27:** Pinned library versions in `pom.xml` and `package-lock.json`.

## Consequences
- Guarantees data privacy and numerical correctness before presentation.
- Binds memory and execution time even under hostile or large inputs.
- Simplifies architecture into a clean, testable modular monolith.


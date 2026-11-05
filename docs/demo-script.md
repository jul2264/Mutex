# Mutex BI — Verification & Demo Walkthrough Script

This script walks through the end-to-end functionality of Mutex BI from an analyst user's perspective, verifying all core capabilities, calculation accuracy, access controls, and exports against the verified ground truth.

---

## 1. Quick Launch

Ensure Docker Desktop is running, then launch the entire stack:

```bash
cp .env.example .env
docker compose up -d --build
```

- Web UI: [http://localhost](http://localhost) (or [http://localhost:5173](http://localhost:5173) in local dev)
- Backend API: [http://localhost:8080](http://localhost:8080)
- Health check: `curl -i http://localhost:8080/actuator/health` (returns `{"status":"UP"}`)

---

## 2. Authentication & Session Security

1. Navigate to [http://localhost/register](http://localhost/register).
2. Register an account:
   - Email: `analyst@example.com`
   - Password: `Password123!`
3. Verify successful redirect to the Dashboard and session cookie establishment (`JSESSIONID` HttpOnly, SameSite=Lax).
4. Verify CSRF protection: Inspect network requests; `X-XSRF-TOKEN` is automatically passed with mutating requests (`POST`, `PUT`, `DELETE`).

---

## 3. Dataset Ingestion & Validation Engine

1. Navigate to **Upload Dataset** (`/datasets/upload`).
2. Provide Dataset Name: `Q1 FY26 Sales Report`.
3. Select Date Format: `yyyy-MM-dd`.
4. Select Policy: `IMPORT_VALID_ONLY`.
5. Select and upload `sample-data/dirty.csv`.
6. Review the live Ingestion Processing & Validation Review card:
   - **Total rows processed:** 14
   - **Valid rows:** 7
   - **Invalid rows:** 6 (missing fields, unparseable quantities, invalid formats)
   - **Duplicate candidates:** 1
   - **Action:** Download error report (`/api/datasets/{id}/imports/{jobId}/errors.csv`) to inspect detailed row-by-row rejection reasons.
7. Click **Confirm Import**. The dataset transitions to `ACTIVE` with 7 confirmed records.

---

## 4. Analytical KPIs & Reconciliation (Oracle F1)

Upload Fixture F1 (`sample-data/fixture-f1.csv`) or inspect the dev-seeded dataset:

| order_id | order_date | quantity | unit_price | unit_cost | line_revenue | line_gross_profit |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| A-1001 | 2026-01-15 | 10 | 500.00 | 300.00 | 5,000.00 | 2,000.00 |
| A-1002 | 2026-02-10 | 5 | 800.00 | 600.00 | 4,000.00 | 1,000.00 |

### Verified Oracle Metrics:
- **Total Revenue:** \$9,000.00 (calculated via stored generated SQL column `SUM(line_revenue)`)
- **Gross Profit:** \$3,000.00 (`SUM(line_gross_profit)`)
- **Gross Margin:** 33.33% (`gross_profit / revenue * 100`)
- **Units Sold:** 15 (`SUM(quantity)`)
- **Orders Count:** 2
- **Line Items:** 2

### Comparative Growth Verification:
- Filter Date Range: `2026-02-01` to `2026-02-28`
- Compare Mode: Previous Period (January 2026: \$5,000.00)
- Current Period Revenue: \$4,000.00
- **Revenue Growth %:** `-20.00%` `((4000 - 5000) / 5000 * 100)`

---

## 5. Visual Exploration & Drilldowns

1. **Monthly Trends:**
   - Bar and Line charts render monthly revenue and gross margin.
   - Zero-revenue periods render explicitly as \$0.00 with margin unavailable (`ZERO_REVENUE`).
2. **Breakdowns:**
   - Category distribution (horizontal bar chart) and Region breakdown (donut chart).
3. **Product Rankings:**
   - Metric rankings descending by revenue and margin.
   - Compliant with rule **BR-12**: underperformance is reported strictly through factual deltas without subjective labelling.

---

## 6. Formula-Injection Hardened Exports (M8)

1. Navigate to **Reports & Exports** tab (`/datasets/{id}/reports`).
2. Select **Records CSV**:
   - Streamed directly with UTF-8 BOM (`\uFEFF`) and CRLF formatting.
   - Text cells starting with `=`, `+`, `-`, `@`, `\t`, or `\r` are sanitized with single quote prefix `'`.
   - Numeric columns remain numeric without alteration.
3. Select **Summary XLSX Workbook**:
   - Downloads a multi-sheet Microsoft Excel workbook containing:
     1. `Executive Summary`: KPI headline figures and filter metadata.
     2. `Monthly Trends`: Month-by-month financial progression.
     3. `Category Breakdown`: Category volumes, revenues, and margins.
     4. `Region Breakdown`: Regional performance.
     5. `Top Products`: Product rankings.
   - Formula and memory-safe streaming via Apache POI `SXSSFWorkbook(100)`.

---

## 7. Multi-Tenant IDOR & Session Invalidation Verification

1. Log out or open an Incognito window.
2. Register a second account: `analyst2@example.com`.
3. Attempt to fetch Dataset 1 using its UUID:
   - `GET /api/datasets/{dataset1_id}/analytics/summary`
   - Response: **404 Not Found** (Rule 5.1: foreign/unowned datasets return 404, never 403, preventing resource enumeration).
4. Log out:
   - `POST /api/auth/logout` returns `204 No Content`.
   - Session cookies cleared. Subsequent API calls return **401 Unauthorized**.

# KPI Definitions & Test Fixtures

## 1. Metric Formulas

| KPI | Formula | Computed By | Unavailable When | Reason Code |
|---|---|---|---|---|
| **Total revenue** | $\sum (\text{quantity} \times \text{unit\_price})$ | SQL: `SUM(line_revenue)` | Never ($0.00$ for empty set) | — |
| **Gross profit** | $\sum (\text{quantity} \times (\text{unit\_price} - \text{unit\_cost}))$ | SQL: `SUM(line_gross_profit)` | Never ($0.00$ for empty set) | — |
| **Gross margin %** | $\frac{\text{gross profit}}{\text{revenue}} \times 100$ | `KpiCalculator` (Java) | $\text{revenue} = 0$ | `ZERO_REVENUE` |
| **Units sold** | $\sum \text{quantity}$ | SQL: `SUM(quantity)` | Never ($0$ for empty set) | — |
| **Revenue growth %** | $\frac{\text{current} - \text{previous}}{\text{previous}} \times 100$ | `KpiCalculator` (Java) | Comparison not requested OR $\text{previous revenue} = 0$ | `NOT_REQUESTED`, `PREVIOUS_ZERO` |
| **Supporting counts** | Orders: `COUNT(DISTINCT order_id)`<br>Line items: `COUNT(*)` | SQL | Never ($0$ for empty set) | — |

## 2. Calculation & Precision Rules

- **Formula Locations:** KPI formulas exist in exactly two places:
  1. The database generated columns `line_revenue` and `line_gross_profit` of table `sales_records`.
  2. The Java service class `com.example.bi.service.analytics.KpiCalculator`.
  *Formulas MUST NEVER be recomputed in controllers, React, or exporters.*
- **Precision & Types:**
  - Database: `NUMERIC(18,2)` / `NUMERIC(12,2)`.
  - Java: `BigDecimal` using `RoundingMode.HALF_UP` with 2 decimal places.
  - JSON API: Formatted as decimal strings with exactly 2 decimal places (e.g. `"9000.00"`).
  - Floating point (`double`, `float`) MUST NOT be used for monetary values.
  - Rounding occurs only at serialization time; never sum already rounded values.
- **Metric Object Shape:**
  Ratio KPIs return a `Metric` structure:
  ```json
  {
    "value": "33.33",
    "unavailableReason": null
  }
  ```
  When unavailable:
  ```json
  {
    "value": null,
    "unavailableReason": "ZERO_REVENUE"
  }
  ```

---

## 3. Fixture F1 (The Canonical Oracle)

Fixture F1 is the baseline test fixture used across unit tests, integration tests, the dev seed, the UI, and exports.

### Transactions

| Row | order_id | order_date | quantity | unit_price | unit_cost | line_revenue | line_gross_profit |
|---|---|---|---|---|---|---|---|
| 1 | A-1001 | 2026-01-15 | 10 | 500.00 | 300.00 | 5000.00 | 2000.00 |
| 2 | A-1002 | 2026-02-10 | 5 | 800.00 | 600.00 | 4000.00 | 1000.00 |
| **Total** | | | **15** | | | **9000.00** | **3000.00** |

### Expected Aggregates (Entire Dataset)

- **Total Revenue:** `9000.00`
- **Gross Profit:** `3000.00`
- **Gross Margin %:** `33.33` ($3000.00 / 9000.00 \times 100 = 33.3333... \to 33.33$)
- **Units Sold:** `15`
- **Distinct Orders:** `2`
- **Line Items:** `2`

### Expected Period Comparison

- With date filter `from=2026-02-01` and `to=2026-02-28` (a whole calendar month, February 2026):
  - Current period revenue: `4000.00` (Row 2)
  - Previous period is January 2026 (`2026-01-01` to `2026-01-31`): `5000.00` (Row 1)
  - Revenue Growth %: $(4000.00 - 5000.00) / 5000.00 \times 100 = -20.00\%$


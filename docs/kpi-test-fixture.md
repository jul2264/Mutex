# KPI Test Fixture F2

A 20-row hand-computed dataset used as an oracle for analytics, boundary conditions, rankings, and reconciliation tests.

## Raw Sales Records

| order_id | order_date | product_name | category | region | quantity | unit_price | unit_cost | line_revenue | line_gross_profit |
|---|---|---|---|---|---|---|---|---|---|
| O-101 | 2026-01-05 | Laptop | Electronics | North | 2 | 1000.00 | 700.00 | 2000.00 | 600.00 |
| O-102 | 2026-01-10 | Desk | Furniture | North | 1 | 500.00 | 300.00 | 500.00 | 200.00 |
| O-103 | 2026-01-15 | Chair | Furniture | South | 4 | 150.00 | 100.00 | 600.00 | 200.00 |
| O-104 | 2026-01-20 | Notebook | Stationery | East | 10 | 20.00 | 10.00 | 200.00 | 100.00 |
| O-105 | 2026-01-25 | Monitor | Electronics | South | 2 | 300.00 | 250.00 | 600.00 | 100.00 |
| O-106 | 2026-01-28 | Desk | Furniture | East | 2 | 500.00 | 600.00 | 1000.00 | -200.00 |
| O-201 | 2026-02-02 | Laptop | Electronics | South | 3 | 1000.00 | 700.00 | 3000.00 | 900.00 |
| O-202 | 2026-02-08 | Monitor | Electronics | North | 1 | 300.00 | 200.00 | 300.00 | 100.00 |
| O-203 | 2026-02-14 | Chair | Furniture | East | 2 | 150.00 | 80.00 | 300.00 | 140.00 |
| O-204 | 2026-02-18 | Notebook | Stationery | North | 20 | 20.00 | 12.00 | 400.00 | 160.00 |
| O-205 | 2026-02-22 | Desk | Furniture | South | 1 | 500.00 | 350.00 | 500.00 | 150.00 |
| O-206 | 2026-02-25 | Laptop | Electronics | East | 1 | 1000.00 | 750.00 | 1000.00 | 250.00 |
| O-207 | 2026-02-28 | Monitor | Electronics | East | 2 | 300.00 | 220.00 | 600.00 | 160.00 |
| O-301 | 2026-03-03 | Laptop | Electronics | North | 2 | 1000.00 | 700.00 | 2000.00 | 600.00 |
| O-302 | 2026-03-09 | Desk | Furniture | North | 2 | 500.00 | 300.00 | 1000.00 | 400.00 |
| O-303 | 2026-03-15 | Chair | Furniture | South | 6 | 150.00 | 100.00 | 900.00 | 300.00 |
| O-304 | 2026-03-20 | Notebook | Stationery | East | 15 | 20.00 | 10.00 | 300.00 | 150.00 |
| O-305 | 2026-03-22 | Notebook | Stationery | South | 25 | 20.00 | 15.00 | 500.00 | 125.00 |
| O-306 | 2026-03-28 | Monitor | Electronics | North | 3 | 300.00 | 210.00 | 900.00 | 270.00 |
| O-307 | 2026-03-30 | Chair | Furniture | North | 2 | 150.00 | 110.00 | 300.00 | 80.00 |

*Note: Row O-106 tests negative profit visibility (`unit_price < unit_cost`).*

---

## 1. Summary Totals (Full Date Range: 2026-01-01 to 2026-03-31)

| Metric | Value |
|---|---|
| Total Revenue | `16900.00` |
| Gross Profit | `4785.00` |
| Gross Margin % | `28.31` (4785.00 / 16900.00 * 100) |
| Units Sold | `106` |
| Distinct Orders | `20` |
| Line Items | `20` |

---

## 2. Monthly Trends

| Month | Revenue | Gross Profit | Units | Margin % | MoM Revenue Growth % |
|---|---|---|---|---|---|
| `2026-01-01` | `4900.00` | `1000.00` | 21 | `20.41` | Unavailable (`NOT_REQUESTED`) |
| `2026-02-01` | `6100.00` | `1860.00` | 30 | `30.49` | `24.49` ((6100 - 4900) / 4900 * 100) |
| `2026-03-01` | `5900.00` | `1925.00` | 55 | `32.63` | `-3.28` ((5900 - 6100) / 6100 * 100) |

---

## 3. Category Breakdown

| Category | Revenue | Profit | Units | Revenue Share % | Margin % |
|---|---|---|---|---|---|
| Electronics | `10400.00` | `2980.00` | 16 | `61.54` | `28.65` |
| Furniture | `5100.00` | `1270.00` | 20 | `30.18` | `24.90` |
| Stationery | `1400.00` | `535.00` | 70 | `8.28` | `38.21` |

---

## 4. Regional Breakdown (with Margin Delta vs Overall Margin `28.31%`)

| Region | Revenue | Profit | Units | Revenue Share % | Margin % | Margin Delta Points |
|---|---|---|---|---|---|---|
| North | `7400.00` | `2410.00` | 33 | `43.79` | `32.57` | `+4.25` |
| South | `6100.00` | `1775.00` | 41 | `36.09` | `29.10` | `+0.78` |
| East | `3400.00` | `600.00` | 32 | `20.12` | `17.65` | `-10.67` |

---

## 5. Top Products (Dual Ranking)

| Product | Revenue | Revenue Rank | Profit | Profit Rank | Units | Margin % |
|---|---|---|---|---|---|---|
| Laptop | `8000.00` | 1 | `2350.00` | 1 | 8 | `29.38` |
| Desk | `3000.00` | 2 | `550.00` | 4 | 6 | `18.33` |
| Monitor | `2400.00` | 3 | `630.00` | 3 | 8 | `26.25` |
| Chair | `2100.00` | 4 | `720.00` | 2 | 14 | `34.29` |
| Notebook | `1400.00` | 5 | `535.00` | 5 | 70 | `38.21` |

*Note: Desk is #2 by revenue but drops to #4 by profit; Chair is #4 by revenue but climbs to #2 by profit.*

---

## 6. Reconciliation Identity

```text
Summary Revenue (16900.00) == Sum(Trends.revenue) == Sum(Categories.revenue) == Sum(Regions.revenue)
Summary Units (106)        == Sum(Trends.units)   == Sum(Categories.units)   == Sum(Regions.units)
Top Products Revenue (8000.00) <= Summary Revenue (16900.00)
```


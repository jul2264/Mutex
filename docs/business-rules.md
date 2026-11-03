# Business Rules (BR-01 to BR-20)

| Rule ID | Statement & Definition |
|---|---|
| **BR-01** | **Line revenue formula:** `line_revenue = quantity * unit_price`. Revenue is never read from the uploaded file; it is always derived. |
| **BR-02** | **Line gross profit formula:** `line_gross_profit = quantity * (unit_price - unit_cost)`. Negative gross profit is valid (selling below cost) and must remain visible. |
| **BR-03** | **Gross margin % formula:** `gross_margin_pct = (gross_profit / revenue) * 100`. When total revenue is zero, gross margin is unavailable with reason `ZERO_REVENUE`. |
| **BR-04** | **Quantity validation:** Whole integer between 1 and 1,000,000 inclusive. `10` and `10.0` are accepted; `2.5`, `0`, or negative numbers (returns, refunds, cancellations) are rejected in v1 with a descriptive error. |
| **BR-05** | **Single currency per dataset:** ISO 4217 code (default `INR`, ₹). No multi-currency conversion or mixed-currency aggregation in v1. |
| **BR-06** | **Revenue scope:** Calculated as `quantity * list_unit_price`, gross before tax and discounts. The UI reflects this in a footnote. Order status, discounts, taxes, and refunds are reserved for v1.1+. |
| **BR-07** | **Duplicate row definition:** Exact match across all 8 normalized fields (`row_hash` SHA-256). The first occurrence within an upload is `VALID`; subsequent occurrences become `DUPLICATE_CANDIDATE` and are skipped upon import unless the owner explicitly opts to include duplicates. |
| **BR-08** | **Required fields:** All 8 input columns are required. No defaults or imputation are allowed; any blank or missing cell in a required column marks the row as `INVALID`. |
| **BR-09** | **Precision & Rounding:** Full precision is preserved in storage and internal calculations. Rounding to 2 decimal places using `RoundingMode.HALF_UP` occurs only when serializing to API JSON responses or exports. Never sum pre-rounded numbers. |
| **BR-10** | **Previous comparison period resolution:** If the active filter range spans full calendar months, the previous comparison period is the exact same number of whole calendar months immediately preceding. Otherwise, it is the same number of days immediately preceding. Growth is unavailable with reason `NOT_REQUESTED` if comparison is off, or `PREVIOUS_ZERO` if prior period revenue is zero. |
| **BR-11** | **Zero-filled trends:** Monthly trend series zero-fills missing calendar months between the range start and end (revenue 0, units 0, gross margin unavailable). |
| **BR-12** | **Ranking & Language:** Rankings order by metric descending, tie-broken by item name ascending. Regions and categories must be described using objective factual deltas (e.g. "margin 4.2 points below dataset average"), never judgmental labels like "underperforming". |
| **BR-13** | **Text normalization:** For product name, category, and region: trim leading/trailing whitespace, collapse internal contiguous whitespace to a single space, apply Unicode NFC normalization. Case-insensitive identity; the first-seen capitalization within a dataset is preserved. |
| **BR-14** | **Date parsing:** The user selects one date format at upload (default ISO `yyyy-MM-dd`; options `dd/MM/yyyy`, `MM/dd/yyyy`, `dd-MM-yyyy`). Ambiguous formats are never guessed. Native Excel date cells are parsed directly. Error if date is before `2000-01-01` or >366 days in the future from upload date; warning if date is in the future but within 366 days. |
| **BR-15** | **Numeric parsing:** Plain decimals with a dot; thousands commas permitted only in standard grouping; tolerate one leading currency symbol (`₹`, `$`, `€`, `£`); anything else is an error. `unit_price` and `unit_cost` must be in range `[0.00, 99999999.99]`. |
| **BR-16** | **Field length constraints:** `order_id` max 64 characters; `product_name` max 255 characters; `category` max 100 characters; `region` max 100 characters. Numeric XLSX `order_id` cells are rendered as clean integer text (e.g. `123.0` becomes `"123"`). |
| **BR-17** | **Warnings vs Errors:** Warnings do not invalidate a row and do not halt processing: `unit_cost > unit_price`, `unit_price == 0`, and future order date within 366 days. |
| **BR-18** | **Data retention:** Staged import rows expire after 24 hours. Abandoned `DRAFT` datasets with no active imports are purged after 7 days. Completed import job history is retained as long as the parent dataset exists. |
| **BR-19** | **Dataset deletion:** Deleting a dataset hard-cascades to all associated sales records, import jobs, staging rows, and issue logs. Requires typing the exact dataset name as confirmation. Action is permanent. |
| **BR-20** | **Logging & Privacy:** Raw spreadsheet cell values, user emails, passwords, and session tokens must never appear in application logs. Raw values may only be shown to the dataset owner in validation previews and downloaded error reports. |


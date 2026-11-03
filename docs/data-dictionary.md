# Data Dictionary & Schema Specification

## 1. Input Spreadsheet Schema (8 Required Columns)

Spreadsheet column headers are matched case-insensitively after stripping UTF-8 BOM, trimming whitespace, and converting internal spaces to underscores. Column order in the file is arbitrary.

| Column | Raw Type | Target Type | Validations & Constraints |
|---|---|---|---|
| `order_id` | String / Numeric | `VARCHAR(64)` | Non-blank, max length 64. Numeric XLSX cells formatted as integers without exponent (e.g. `1001.0` -> `"1001"`). |
| `order_date` | String / Excel Date | `DATE` | Parseable per chosen upload format or native Excel date. Range: `2000-01-01` to `upload_date + 366 days`. Warning if `> upload_date`. |
| `product_name` | String | `VARCHAR(255)` | Non-blank, max 255 chars, NFC normalized, whitespace trimmed/collapsed. |
| `category` | String | `VARCHAR(100)` | Non-blank, max 100 chars, NFC normalized, whitespace trimmed/collapsed. |
| `region` | String | `VARCHAR(100)` | Non-blank, max 100 chars, NFC normalized, whitespace trimmed/collapsed. |
| `quantity` | String / Numeric | `INTEGER` | Integer between 1 and 1,000,000. `10` and `10.0` accepted. `0`, negative, or fractional values rejected. |
| `unit_price` | String / Numeric | `NUMERIC(12,2)` | Non-negative decimal between `0.00` and `99,999,999.99`. Leading currency symbols (`₹`, `$`, `€`, `£`) tolerated. |
| `unit_cost` | String / Numeric | `NUMERIC(12,2)` | Non-negative decimal between `0.00` and `99,999,999.99`. |

---

## 2. Derived Columns (System-Generated)

These columns are never read from the user's file.

| Column | Target Type | Generation Logic |
|---|---|---|
| `line_revenue` | `NUMERIC(18,2)` | `quantity * unit_price` (STORED generated column in PostgreSQL) |
| `line_gross_profit` | `NUMERIC(18,2)` | `quantity * (unit_price - unit_cost)` (STORED generated column in PostgreSQL) |
| `row_hash` | `CHAR(64)` | SHA-256 of the 8 normalized fields joined by unit separator `\u001F`, with price/cost formatted at scale 2. |

---

## 3. Database Schema Overview (Flyway V1)

### `users`
- Stores user credentials, roles, and status.
- Primary key: `id` (UUID).
- Fields: `email`, `email_normalized` (unique index), `display_name`, `password_hash` (BCrypt 12), `role` (`ANALYST`, `MANAGER`, `ADMIN`), `enabled`, `created_at`, `last_login_at`.

### `datasets`
- Represents an uploaded collection of sales records owned by a single user.
- Primary key: `id` (UUID).
- Fields: `owner_id` (FK `users.id`), `name` (unique per owner, case-insensitive), `currency_code` (default `INR`), `status` (`DRAFT`, `ACTIVE`), `row_count`, `min_order_date`, `max_order_date`, `data_version`, `created_at`, `updated_at`.

### `import_jobs`
- Represents a file upload and ingestion attempt.
- Primary key: `id` (UUID).
- Fields: `dataset_id` (FK `datasets.id`), `created_by` (FK `users.id`), `original_filename`, `file_format` (`CSV`, `XLSX`), `file_size_bytes`, `file_sha256`, `date_format`, `policy` (`IMPORT_VALID_ONLY`, `REJECT_FILE_ON_ANY_ERROR`), `status` (`PARSING`, `VALIDATED`, `COMPLETED`, `FAILED`, `CANCELLED`, `EXPIRED`), file error code & message, row count metrics (`total_rows`, `valid_rows`, `invalid_rows`, `duplicate_rows`, `blank_rows_skipped`, `warning_count`, `rows_processed`, `imported_rows`), `issues_truncated`, timestamps.
- Partial unique index: At most one active import (`PARSING` or `VALIDATED`) per dataset.

### `import_staging_rows`
- Temporary storage for parsed rows before user confirmation.
- Primary key: `id` (`BIGINT GENERATED ALWAYS AS IDENTITY`).
- Fields: `import_job_id`, `source_row_number`, `row_status` (`VALID`, `INVALID`, `DUPLICATE_CANDIDATE`), `raw_values` (JSONB, populated for `INVALID` rows only), 8 parsed data columns, `row_hash`.

### `import_row_issues`
- Records row-level validation findings.
- Primary key: `id` (`BIGINT GENERATED ALWAYS AS IDENTITY`).
- Fields: `import_job_id`, `source_row_number`, `column_name`, `severity` (`ERROR`, `WARNING`), `code`, `message`, `raw_value`.
- Cap: Stored issues capped at 10,000 per job to bound database overhead.

### `sales_records`
- The core analytical data store containing committed sales transactions. Access is strictly through JDBC (no JPA entity).
- Primary key: `id` (`BIGINT GENERATED ALWAYS AS IDENTITY`).
- Fields: `dataset_id` (FK `datasets.id`), `import_job_id` (FK `import_jobs.id`), `source_row_number`, `order_id`, `order_date`, `product_name`, `category`, `region`, `quantity`, `unit_price`, `unit_cost`, `line_revenue` (STORED generated), `line_gross_profit` (STORED generated), `row_hash`.
- Indexes: `(dataset_id, order_date)`, `(dataset_id, category)`, `(dataset_id, region)`, `(dataset_id, product_name)`, `(dataset_id, row_hash)`.


# ADR-0003: Guided Bulk-Fix Rules Wizard for Staged Rows

Status: accepted
Date: 2026-11-06

## Context
When business users upload large sales spreadsheets (CSV or XLSX), human error often introduces common systematic anomalies across dozens or hundreds of rows (e.g. accidental negative price symbols, decimal quantities from formula rounding, blank dates, or unassigned product/category/region entries). While ADR-0002 provided single-row inline editing, manually correcting large numbers of invalid rows is time-consuming and tedious. Users requested a one-click bulk repair capability to resolve common spreadsheet anomalies in batch.

## Decision
We implement **Approach 2: Guided Bulk-Fix Rules Wizard** (`POST /api/datasets/{datasetId}/imports/{jobId}/bulk-fix`) rather than a blind unguided auto-fix.

1. **Explicit User-Confirmed Remediation Rules**:
   - **Negative Prices / Costs**: Flip to absolute positive value (`ABS_VALUE`) or keep unchanged (`IGNORE`).
   - **Decimal Quantities**: Round to nearest whole integer (`ROUND_HALF_UP`) or truncate decimal (`TRUNCATE`).
   - **Zero Quantities**: Fix to minimum valid quantity 1 (`SET_ONE`) or keep unchanged (`IGNORE`).
   - **Missing / Invalid Dates**: Resolve to the **latest valid date in the file** (`LATEST_VALID_DATE`, queried via `MAX(order_date)` on valid staging rows) or a user-chosen ISO date (`CUSTOM`).
   - **Missing Text Fields (`product_name`, `category`, `region`)**: Populate user-configurable placeholders (defaults: `"Unspecified Product"`, `"Uncategorized"`, `"Unassigned"`).
   - **Out-of-Range Quantities (`> 1,000,000`)**: **Strictly protected and kept untouched** as `INVALID` for manual review to prevent accidental distortion of sales volumes and financial KPIs.

2. **Backend Architecture & Integrity**:
   - `DatasetAccessService.requireOwned(datasetId)` is called before inspecting or modifying data, returning HTTP 404 for unowned datasets.
   - Guarded by `ImportJob.status == VALIDATED`.
   - Iterates through invalid staging rows in memory, applies selected rule transformations to raw/parsed cells, and runs full re-validation through `RowValidator`.
   - Executes batch SQL updates (`batchUpdateStagingRows`, `batchReplaceIssues`) in batches of 1,000.
   - Clears prior duplicate candidate markings and runs `StagingRepository.markDuplicates(jobId)` because row hashes change upon repair.
   - Recomputes and updates authoritative counts on `ImportJob`.
   - Returns a structured `BulkFixResponse` detailing `repairedToValid`, `remainingInvalid`, `duplicateCandidateCount`, `untouchedLargeQuantityCount`, and `counts`.

3. **Frontend UX**:
   - A prominent **"🪄 Bulk Fix Issues ({invalidCount})"** button is rendered in the header actions whenever staged rows contain invalid records.
   - Clicking opens the `BulkFixModal` dialog displaying all guided rules, date fallback options, and customizable text placeholders.
   - Clear visual safeguards inform the user about out-of-range quantity protection.
   - On completion, invalidates TanStack Query caches, sets active tab to `ALL`, and displays a detailed celebratory success banner.

## Consequences
- Business users can remediate entire spreadsheets in seconds without losing oversight or control over data transformations.
- Financial integrity is preserved: arithmetic on money is never done as float, strict bounds are enforced, and huge quantities remain flagged for human review.
- Set-based duplicate detection ensures no repaired row can bypass duplicate candidate detection.

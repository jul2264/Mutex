# ADR-0002: Inline Row Editing in Preview Staging

Status: accepted
Date: 2026-11-06

## Context
During spreadsheet ingestion, business users reviewing datasets in the staging area ("Review & Quality Inspection") encounter rows flagged as `INVALID` (e.g. missing date, malformed quantity or cost). Previously, correcting rejected rows required modifying the source CSV/XLSX file outside the application and re-uploading from scratch. Business users requested the ability to fix row-level validation issues directly within the preview table before confirming data import to analytics.

## Decision
We introduce **Option A: Inline Row Editing** for staged rows in the `VALIDATED` state:

1. **Endpoint**: `PATCH /api/datasets/{datasetId}/imports/{jobId}/rows/{rowNumber}`
2. **Access Control**: Protected by session authentication, CSRF validation, and `DatasetAccessService.requireOwned(datasetId)`. Attempting to access or edit an unowned dataset returns HTTP 404 (preventing entity existence leakage).
3. **State Invariant**: Editing is permitted strictly when `ImportJob.status == VALIDATED`. Any attempt to edit in terminal or non-validated states (`COMPLETED`, `COMMITTING`, `PARSING`, `FAILED`, `CANCELLED`) returns HTTP 409 (`IMPORT_NOT_EDITABLE`).
4. **Validation Re-run**: Cell values are re-evaluated using `RowValidator` and the job's chosen `dateFormat`. Text fields undergo Unicode NFC normalization and whitespace collapsing (`RowNormalizer`).
5. **Issues & Staging Table Synchronization**:
   - `import_staging_rows` row is updated with new normalized values, `row_status`, and SHA-256 `row_hash` (or `raw_values` JSON if still invalid).
   - `import_row_issues` records for that `source_row_number` are purged and replaced with newly generated issues.
6. **Set-based Duplicate Re-evaluation**:
   - Because changing cell values alters `row_hash`, duplicate candidate statuses across the staging job are re-synchronized: existing duplicate warning issues (`DUPLICATE_IN_FILE`) are cleared, previous `DUPLICATE_CANDIDATE` rows are reset to `VALID`, and `StagingRepository.markDuplicates(jobId)` is executed atomically.
7. **Authoritative Count Recalculation**:
   - Staging counts (`validRows`, `invalidRows`, `duplicateRows`, `warningCount`) are recomputed and updated directly on the `ImportJob` entity.
   - The response returns both the updated row data (`PreviewRowDto`) and updated job counts (`PreviewCountsDto`).
8. **Frontend UX**:
   - Staged rows in `PreviewPage` display an inline "Edit" action button.
   - Clicking enters an inline editing mode with form inputs styled for light and dark themes. Fields with validation issues are highlighted with red outlines.
   - Clicking "Save" triggers the PATCH mutation, updates the table and tab counts seamlessly, and exits editing mode.

## Consequences
- Business users can correct spreadsheet errors immediately without round-tripping to desktop spreadsheet software.
- Full validation and security integrity are preserved on the backend: no unvalidated or malformed data can reach `sales_records`.
- Tab counts (`All Rows`, `Valid`, `Invalid`, `Duplicates`) stay fully synchronized after every edit.
- Zero schema migrations needed (`import_staging_rows` and `import_row_issues` tables already hold the necessary fields).

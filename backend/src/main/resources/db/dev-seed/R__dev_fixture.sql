-- Dev seed fixture (loaded only by the 'dev' profile)
-- Uses fixed UUIDs and ON CONFLICT DO NOTHING

INSERT INTO users (id, email, email_normalized, display_name, password_hash, role, enabled)
VALUES (
  '00000000-0000-0000-0000-000000000001',
  'dev@example.com',
  'dev@example.com',
  'Dev Analyst',
  '$2a$12$000000000000000000000000000000000000000000000000000000', -- unusable hash
  'ANALYST',
  true
) ON CONFLICT (id) DO NOTHING;

INSERT INTO datasets (id, owner_id, name, currency_code, status, row_count, min_order_date, max_order_date, data_version)
VALUES (
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  'F1 Sample Sales Dataset',
  'INR',
  'ACTIVE',
  2,
  '2026-01-15',
  '2026-02-10',
  1
) ON CONFLICT (id) DO NOTHING;

INSERT INTO import_jobs (
  id, dataset_id, created_by, original_filename, file_format, file_size_bytes, file_sha256,
  date_format, policy, status, total_rows, valid_rows, invalid_rows, duplicate_rows,
  rows_processed, imported_rows
) VALUES (
  '00000000-0000-0000-0000-000000000003',
  '00000000-0000-0000-0000-000000000002',
  '00000000-0000-0000-0000-000000000001',
  'f1.csv',
  'CSV',
  165,
  'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855',
  'ISO',
  'IMPORT_VALID_ONLY',
  'COMPLETED',
  2, 2, 0, 0, 2, 2
) ON CONFLICT (id) DO NOTHING;

-- Sales records for F1 fixture
-- line_revenue and line_gross_profit are STORED generated columns and must not be listed in INSERT
INSERT INTO sales_records (
  dataset_id, import_job_id, source_row_number, order_id, order_date, product_name, category, region, quantity, unit_price, unit_cost, row_hash
) VALUES
  (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003',
    2,
    'A-1001',
    '2026-01-15',
    'Office Ergonomic Chair',
    'Furniture',
    'North',
    10,
    500.00,
    300.00,
    '9f01f9ec0f7a0e030812cd5a355006e918176683339a214042d23a232c28dc43'
  ),
  (
    '00000000-0000-0000-0000-000000000002',
    '00000000-0000-0000-0000-000000000003',
    3,
    'A-1002',
    '2026-02-10',
    'Standing Desk Pro',
    'Furniture',
    'South',
    5,
    800.00,
    600.00,
    '9545932bcda7bfd78317ef23364aeb0e03941c62ed771789b2070f4cdf29b01a'
  )
ON CONFLICT DO NOTHING;


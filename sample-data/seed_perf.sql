-- Performance Seed Script (dev only)
-- Populates 100,000 realistic sales rows for NFR-01 benchmarking.

BEGIN;

-- 1. Ensure Benchmark Dataset exists
INSERT INTO datasets (
    id, owner_id, name, currency_code, status, row_count, min_order_date, max_order_date, data_version
) VALUES (
    '00000000-0000-0000-0000-000000000099',
    '00000000-0000-0000-0000-000000000001',
    'Benchmark 100k Dataset',
    'INR',
    'ACTIVE',
    100000,
    '2025-01-01',
    '2025-12-31',
    1
) ON CONFLICT (id) DO UPDATE SET
    row_count = 100000,
    min_order_date = '2025-01-01',
    max_order_date = '2025-12-31';

-- 2. Ensure Benchmark Job exists
INSERT INTO import_jobs (
    id, dataset_id, created_by, original_filename, file_format, file_size_bytes, file_sha256,
    date_format, policy, status, total_rows, valid_rows, invalid_rows, duplicate_rows,
    rows_processed, imported_rows
) VALUES (
    '00000000-0000-0000-0000-000000000098',
    '00000000-0000-0000-0000-000000000099',
    '00000000-0000-0000-0000-000000000001',
    'perf_100k.csv',
    'CSV',
    5000000,
    'perf_sha256',
    'ISO',
    'IMPORT_VALID_ONLY',
    'COMPLETED',
    100000, 100000, 0, 0, 100000, 100000
) ON CONFLICT (id) DO NOTHING;

-- 3. Clean any previous benchmark rows
DELETE FROM sales_records WHERE dataset_id = '00000000-0000-0000-0000-000000000099';

-- 4. Generate 100,000 rows
INSERT INTO sales_records (
    dataset_id,
    import_job_id,
    source_row_number,
    order_id,
    order_date,
    product_name,
    category,
    region,
    quantity,
    unit_price,
    unit_cost,
    row_hash
)
SELECT
    '00000000-0000-0000-0000-000000000099'::uuid,
    '00000000-0000-0000-0000-000000000098'::uuid,
    i,
    'ORD-' || (i % 25000 + 1),
    ('2025-01-01'::date + (i % 365) * '1 day'::interval)::date,
    (ARRAY[
        'Laptop Pro', 'Standing Desk', 'Ergonomic Chair', 'UltraWide Monitor',
        'Wireless Keyboard', 'Noise-Canceling Headset', 'Notebook Pack',
        'USB-C Dock', 'Mechanical Mouse', 'Webcam 4K'
    ])[(i % 10) + 1],
    (ARRAY['Electronics', 'Furniture', 'Stationery', 'Accessories'])[(i % 4) + 1],
    (ARRAY['North', 'South', 'East', 'West', 'Central'])[(i % 5) + 1],
    (i % 20 + 1),
    ((i % 500 + 10) * 1.50)::numeric(12,2),
    ((i % 500 + 10) * 0.90)::numeric(12,2),
    md5(i::text)
FROM generate_series(1, 100000) AS s(i);

COMMIT;

ANALYZE sales_records;
ANALYZE datasets;


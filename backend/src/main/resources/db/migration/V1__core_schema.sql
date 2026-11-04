CREATE TABLE users (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email            varchar(254) NOT NULL,
  email_normalized varchar(254) NOT NULL,
  display_name     varchar(80)  NOT NULL,
  password_hash    varchar(100) NOT NULL,
  role             varchar(20)  NOT NULL DEFAULT 'ANALYST'
                   CHECK (role IN ('ANALYST','MANAGER','ADMIN')),
  enabled          boolean      NOT NULL DEFAULT true,
  created_at       timestamptz  NOT NULL DEFAULT now(),
  last_login_at    timestamptz
);
CREATE UNIQUE INDEX ux_users_email ON users (email_normalized);

CREATE TABLE datasets (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id       uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name           varchar(100) NOT NULL,
  currency_code  char(3)      NOT NULL DEFAULT 'INR',
  status         varchar(10)  NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','ACTIVE')),
  row_count      integer      NOT NULL DEFAULT 0,
  min_order_date date,
  max_order_date date,
  data_version   integer      NOT NULL DEFAULT 0,
  created_at     timestamptz  NOT NULL DEFAULT now(),
  updated_at     timestamptz  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_datasets_owner_name ON datasets (owner_id, lower(name));
CREATE INDEX ix_datasets_owner_created ON datasets (owner_id, created_at DESC);

CREATE TABLE import_jobs (
  id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  dataset_id         uuid NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
  created_by         uuid NOT NULL REFERENCES users(id),
  original_filename  varchar(255) NOT NULL,
  file_format        varchar(5)   NOT NULL CHECK (file_format IN ('CSV','XLSX')),
  file_size_bytes    bigint       NOT NULL,
  file_sha256        char(64)     NOT NULL,
  date_format        varchar(20)  NOT NULL DEFAULT 'ISO',
  policy             varchar(30)  NOT NULL DEFAULT 'IMPORT_VALID_ONLY',
  status             varchar(12)  NOT NULL
                     CHECK (status IN ('PARSING','VALIDATED','COMPLETED','FAILED','CANCELLED','EXPIRED')),
  file_error_code    varchar(50),
  file_error_message varchar(500),
  total_rows         integer NOT NULL DEFAULT 0,
  valid_rows         integer NOT NULL DEFAULT 0,
  invalid_rows       integer NOT NULL DEFAULT 0,
  duplicate_rows     integer NOT NULL DEFAULT 0,
  blank_rows_skipped integer NOT NULL DEFAULT 0,
  warning_count      integer NOT NULL DEFAULT 0,
  rows_processed     integer NOT NULL DEFAULT 0,
  imported_rows      integer NOT NULL DEFAULT 0,
  issues_truncated   boolean NOT NULL DEFAULT false,
  created_at         timestamptz NOT NULL DEFAULT now(),
  validated_at       timestamptz,
  completed_at       timestamptz,
  expires_at         timestamptz
);
CREATE UNIQUE INDEX ux_import_one_active_per_dataset
  ON import_jobs (dataset_id) WHERE status IN ('PARSING','VALIDATED');
CREATE INDEX ix_import_dataset_created ON import_jobs (dataset_id, created_at DESC);

CREATE TABLE import_staging_rows (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  import_job_id     uuid NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
  source_row_number integer NOT NULL,
  row_status        varchar(20) NOT NULL
                    CHECK (row_status IN ('VALID','INVALID','DUPLICATE_CANDIDATE')),
  raw_values        jsonb,               -- filled for INVALID rows only
  order_id          varchar(64),
  order_date        date,
  product_name      varchar(255),
  category          varchar(100),
  region            varchar(100),
  quantity          integer,
  unit_price        numeric(12,2),
  unit_cost         numeric(12,2),
  row_hash          char(64)
);
CREATE INDEX ix_staging_job_status ON import_staging_rows (import_job_id, row_status, source_row_number);
CREATE INDEX ix_staging_job_hash   ON import_staging_rows (import_job_id, row_hash);

CREATE TABLE import_row_issues (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  import_job_id     uuid NOT NULL REFERENCES import_jobs(id) ON DELETE CASCADE,
  source_row_number integer NOT NULL,
  column_name       varchar(40),
  severity          varchar(7)   NOT NULL CHECK (severity IN ('ERROR','WARNING')),
  code              varchar(40)  NOT NULL,
  message           varchar(300) NOT NULL,
  raw_value         varchar(300)
);
CREATE INDEX ix_issues_job_row ON import_row_issues (import_job_id, source_row_number);

CREATE TABLE sales_records (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  dataset_id        uuid NOT NULL REFERENCES datasets(id) ON DELETE CASCADE,
  import_job_id     uuid REFERENCES import_jobs(id) ON DELETE SET NULL,
  source_row_number integer,
  order_id          varchar(64)   NOT NULL,
  order_date        date          NOT NULL,
  product_name      varchar(255)  NOT NULL,
  category          varchar(100)  NOT NULL,
  region            varchar(100)  NOT NULL,
  quantity          integer       NOT NULL CHECK (quantity BETWEEN 1 AND 1000000),
  unit_price        numeric(12,2) NOT NULL CHECK (unit_price BETWEEN 0 AND 99999999.99),
  unit_cost         numeric(12,2) NOT NULL CHECK (unit_cost  BETWEEN 0 AND 99999999.99),
  line_revenue      numeric(18,2) GENERATED ALWAYS AS (quantity * unit_price) STORED,
  line_gross_profit numeric(18,2) GENERATED ALWAYS AS (quantity * (unit_price - unit_cost)) STORED,
  row_hash          char(64)      NOT NULL
);
CREATE INDEX ix_sales_ds_date     ON sales_records (dataset_id, order_date);
CREATE INDEX ix_sales_ds_category ON sales_records (dataset_id, category);
CREATE INDEX ix_sales_ds_region   ON sales_records (dataset_id, region);
CREATE INDEX ix_sales_ds_product  ON sales_records (dataset_id, product_name);
CREATE INDEX ix_sales_ds_hash     ON sales_records (dataset_id, row_hash);


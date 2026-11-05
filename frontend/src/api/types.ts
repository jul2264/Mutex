export interface Metric {
  value: string | null;
  unavailableReason: string | null;
}

export interface PeriodDto {
  from: string | null;
  to: string | null;
  previousFrom: string | null;
  previousTo: string | null;
}

export interface PreviousTotalsDto {
  revenue: string;
  grossProfit: string;
  unitsSold: number;
}

export interface SummaryResponse {
  datasetId: string;
  currency: string;
  dataVersion: number;
  period: PeriodDto;
  revenue: string;
  grossProfit: string;
  unitsSold: number;
  orders: number;
  lineItems: number;
  grossMarginPct: Metric;
  revenueGrowthPct: Metric;
  previous: PreviousTotalsDto | null;
}

export interface UserDto {
  id: string;
  displayName: string;
  email: string;
  role: string;
}

export interface CsrfResponse {
  token: string;
  headerName: string;
  parameterName: string;
}

export interface ApiProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  code?: string;
  requestId?: string;
  errors?: Array<{ field: string; code: string; message: string }>;
}

export interface DatasetResponse {
  id: string;
  name: string;
  currencyCode: string;
  status: 'DRAFT' | 'ACTIVE';
  rowCount: number;
  minOrderDate: string | null;
  maxOrderDate: string | null;
  dataVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface ImportJobSummaryDto {
  id: string;
  datasetId: string;
  status: 'PARSING' | 'VALIDATED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';
  originalFilename: string;
  policy: string;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  importedRows: number;
  createdAt: string;
  completedAt: string | null;
}

export interface DatasetDetailResponse extends DatasetResponse {
  latestImport: ImportJobSummaryDto | null;
  importHistory: ImportJobSummaryDto[];
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface RowValuesDto {
  orderId: string | null;
  orderDate: string | null;
  productName: string | null;
  category: string | null;
  region: string | null;
  quantity: number | null;
  unitPrice: string | null;
  unitCost: string | null;
}

export interface RowIssueDto {
  column: string;
  severity: 'ERROR' | 'WARNING';
  code: string;
  message: string;
  rawValue: string | null;
}

export interface PreviewRowDto {
  rowNumber: number;
  status: 'VALID' | 'INVALID' | 'DUPLICATE_CANDIDATE';
  values: RowValuesDto;
  rawValues: Record<string, unknown> | null;
  issues: RowIssueDto[];
}

export interface PreviewCountsDto {
  total: number;
  valid: number;
  invalid: number;
  duplicateCandidate: number;
  warnings: number;
}

export interface PreviewResponse {
  source: 'STAGED' | 'COMMITTED';
  counts: PreviewCountsDto;
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  content: PreviewRowDto[];
}

export interface ConfirmImportRequest {
  importJobId: string;
  includeDuplicates: boolean;
}

export interface ConfirmImportResponse {
  datasetId: string;
  importJobId: string;
  importedRows: number;
  dataVersion: number;
  status: string;
}

export interface TrendPoint {
  month: string;
  revenue: string;
  grossProfit: string;
  unitsSold: number;
  grossMarginPct: Metric;
  revenueGrowthPct: Metric;
}

export interface TrendsResponse {
  datasetId: string;
  currency: string;
  dataVersion: number;
  points: TrendPoint[];
}

export interface BreakdownRow {
  name: string;
  revenue: string;
  grossProfit: string;
  unitsSold: number;
  revenueSharePct: string;
  grossMarginPct: Metric;
  marginDeltaPoints: string | null;
  revenueGrowthPct: Metric | null;
}

export interface CategoriesResponse {
  datasetId: string;
  currency: string;
  dataVersion: number;
  categories: BreakdownRow[];
}

export interface RegionsResponse {
  datasetId: string;
  currency: string;
  dataVersion: number;
  regions: BreakdownRow[];
}

export interface TopProductRow {
  product: string;
  revenue: string;
  grossProfit: string;
  unitsSold: number;
  revenueRank: number;
  profitRank: number;
}

export interface TopProductsResponse {
  datasetId: string;
  currency: string;
  dataVersion: number;
  products: TopProductRow[];
}

export interface FilterOptionsResponse {
  datasetId: string;
  categories: string[];
  regions: string[];
  minOrderDate: string | null;
  maxOrderDate: string | null;
  totalProducts: number;
  categoriesTruncated: boolean;
  regionsTruncated: boolean;
}

export interface AnalyticsFilterParams {
  from?: string;
  to?: string;
  category?: string[];
  region?: string[];
  product?: string;
  compare?: 'NONE' | 'PREVIOUS_PERIOD';
  sort?: string;
  direction?: 'asc' | 'desc';
  limit?: number;
  rankBy?: 'REVENUE' | 'PROFIT';
}

export interface UploadResponse {
  datasetId: string;
  importJobId: string;
  statusUrl: string;
}

export interface FileErrorDto {
  code: string;
  message: string;
}

export interface ImportJobStatusResponse {
  id: string;
  datasetId: string;
  status: 'PARSING' | 'VALIDATED' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | 'EXPIRED';
  policy: string;
  fileName: string;
  rowsProcessed: number;
  totalRows: number;
  validRows: number;
  invalidRows: number;
  duplicateRows: number;
  warningCount: number;
  blankRowsSkipped: number;
  issuesTruncated: boolean;
  canConfirm: boolean;
  confirmBlockedReason: string | null;
  fileError: FileErrorDto | null;
  expiresAt: string | null;
}

export interface RenameDatasetRequest {
  name: string;
}

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

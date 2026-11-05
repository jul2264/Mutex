import { describe, it, expect, vi, beforeEach } from 'vitest';
import {
  fetchSummary,
  fetchTrends,
  fetchCategories,
  fetchRegions,
  fetchTopProducts,
  fetchFilterOptions,
} from './analytics';

describe('analytics API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  const datasetId = '00000000-0000-0000-0000-000000000002';

  it('fetchSummary calls GET /api/datasets/:id/analytics/summary with filters', async () => {
    const mockSummary = {
      datasetId,
      currency: 'INR',
      dataVersion: 1,
      period: { from: '2026-01-01', to: '2026-02-28', previousFrom: null, previousTo: null },
      revenue: '9000.00',
      grossProfit: '3000.00',
      unitsSold: 15,
      orders: 2,
      lineItems: 2,
      grossMarginPct: { value: '33.33', unavailableReason: null },
      revenueGrowthPct: { value: null, unavailableReason: 'NOT_REQUESTED' },
      previous: null,
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockSummary,
    } as Response);

    const result = await fetchSummary(datasetId, {
      from: '2026-01-01',
      to: '2026-02-28',
      category: ['Furniture'],
      region: ['North', 'South'],
      product: 'Chair',
      compare: 'PREVIOUS_PERIOD',
    });

    expect(result).toEqual(mockSummary);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/summary?from=2026-01-01&to=2026-02-28&product=Chair&compare=PREVIOUS_PERIOD&category=Furniture&region=North&region=South`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('fetchTrends calls GET /api/datasets/:id/analytics/trends', async () => {
    const mockTrends = {
      datasetId,
      currency: 'INR',
      dataVersion: 1,
      points: [
        {
          month: '2026-01',
          revenue: '5000.00',
          grossProfit: '2000.00',
          unitsSold: 10,
          grossMarginPct: { value: '40.00', unavailableReason: null },
          revenueGrowthPct: { value: null, unavailableReason: 'NOT_REQUESTED' },
        },
      ],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockTrends,
    } as Response);

    const result = await fetchTrends(datasetId);
    expect(result).toEqual(mockTrends);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/trends`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('fetchCategories calls GET /api/datasets/:id/analytics/categories with sort params', async () => {
    const mockCategories = {
      datasetId,
      currency: 'INR',
      dataVersion: 1,
      categories: [
        {
          name: 'Furniture',
          revenue: '5100.00',
          grossProfit: '1270.00',
          unitsSold: 20,
          revenueSharePct: '30.18',
          grossMarginPct: { value: '24.90', unavailableReason: null },
          marginDeltaPoints: null,
          revenueGrowthPct: null,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockCategories,
    } as Response);

    const result = await fetchCategories(datasetId, { sort: 'revenue', direction: 'desc' });
    expect(result).toEqual(mockCategories);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/categories?sort=revenue&direction=desc`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('fetchRegions calls GET /api/datasets/:id/analytics/regions', async () => {
    const mockRegions = {
      datasetId,
      currency: 'INR',
      dataVersion: 1,
      regions: [
        {
          name: 'North',
          revenue: '7400.00',
          grossProfit: '2410.00',
          unitsSold: 33,
          revenueSharePct: '43.79',
          grossMarginPct: { value: '32.57', unavailableReason: null },
          marginDeltaPoints: '4.26',
          revenueGrowthPct: null,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockRegions,
    } as Response);

    const result = await fetchRegions(datasetId);
    expect(result).toEqual(mockRegions);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/regions`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('fetchTopProducts calls GET /api/datasets/:id/analytics/top-products with limit and rankBy', async () => {
    const mockTop = {
      datasetId,
      currency: 'INR',
      dataVersion: 1,
      products: [
        {
          product: 'Laptop',
          revenue: '8000.00',
          grossProfit: '2350.00',
          unitsSold: 8,
          revenueRank: 1,
          profitRank: 1,
        },
      ],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockTop,
    } as Response);

    const result = await fetchTopProducts(datasetId, { limit: 5, rankBy: 'PROFIT' });
    expect(result).toEqual(mockTop);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/top-products?limit=5&rankBy=PROFIT`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('fetchFilterOptions calls GET /api/datasets/:id/analytics/filter-options', async () => {
    const mockOptions = {
      datasetId,
      categories: ['Electronics', 'Furniture'],
      regions: ['East', 'North'],
      minOrderDate: '2026-01-01',
      maxOrderDate: '2026-03-31',
      totalProducts: 5,
      categoriesTruncated: false,
      regionsTruncated: false,
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockOptions,
    } as Response);

    const result = await fetchFilterOptions(datasetId);
    expect(result).toEqual(mockOptions);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      `/api/datasets/${datasetId}/analytics/filter-options`,
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });
});

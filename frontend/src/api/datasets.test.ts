import { describe, it, expect, vi, beforeEach } from 'vitest';
import { listDatasets, getDataset, renameDataset, deleteDataset } from './datasets';

describe('datasets API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('listDatasets calls GET /api/datasets with query params', async () => {
    const mockResponse = {
      content: [
        {
          id: 'ds-1',
          name: 'Sales 2026',
          currencyCode: 'INR',
          status: 'ACTIVE',
          rowCount: 100,
          minOrderDate: '2026-01-01',
          maxOrderDate: '2026-02-01',
          dataVersion: 1,
          createdAt: '2026-01-01T00:00:00Z',
          updatedAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    } as Response);

    const result = await listDatasets({ page: 0, size: 20, sort: 'name', direction: 'asc' });
    expect(result).toEqual(mockResponse);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets?page=0&size=20&sort=name&direction=asc',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('getDataset calls GET /api/datasets/:id', async () => {
    const mockDetail = {
      id: 'ds-1',
      name: 'Sales 2026',
      currencyCode: 'INR',
      status: 'ACTIVE',
      rowCount: 100,
      minOrderDate: '2026-01-01',
      maxOrderDate: '2026-02-01',
      dataVersion: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
      latestImport: null,
      importHistory: [],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockDetail,
    } as Response);

    const result = await getDataset('ds-1');
    expect(result).toEqual(mockDetail);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-1',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('renameDataset sends PATCH to /api/datasets/:id', async () => {
    const mockUpdated = {
      id: 'ds-1',
      name: 'New Name',
      currencyCode: 'INR',
      status: 'ACTIVE',
      rowCount: 100,
      minOrderDate: '2026-01-01',
      maxOrderDate: '2026-02-01',
      dataVersion: 1,
      createdAt: '2026-01-01T00:00:00Z',
      updatedAt: '2026-01-01T00:00:00Z',
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockUpdated,
    } as Response);

    const result = await renameDataset('ds-1', 'New Name');
    expect(result).toEqual(mockUpdated);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-1',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify({ name: 'New Name' }),
      })
    );
  });

  it('deleteDataset sends DELETE to /api/datasets/:id?confirmName=...', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => null,
    } as Response);

    await deleteDataset('ds-1', 'Sales 2026');
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-1?confirmName=Sales+2026',
      expect.objectContaining({ method: 'DELETE' })
    );
  });
});

import { describe, it, expect, vi, beforeEach } from 'vitest';
import { getPreview, confirmImport, cancelImport, getImportHistory, editRow, applyBulkFix, discardDuplicates } from './imports';

describe('imports API client', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  it('getPreview calls GET /api/datasets/:id/preview with params', async () => {
    const mockPreview = {
      source: 'STAGED',
      counts: { total: 10, valid: 8, invalid: 2, duplicateCandidate: 0, warnings: 1 },
      page: 0,
      size: 50,
      totalElements: 10,
      totalPages: 1,
      content: [],
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockPreview,
    } as Response);

    const result = await getPreview('ds-100', { source: 'STAGED', status: 'VALID', page: 0, size: 50 });
    expect(result).toEqual(mockPreview);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/preview?source=STAGED&status=VALID&page=0&size=50',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('confirmImport sends POST to /api/datasets/:id/imports/confirm', async () => {
    const mockResult = {
      datasetId: 'ds-100',
      importJobId: 'job-1',
      importedRows: 8,
      dataVersion: 1,
      status: 'COMPLETED',
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResult,
    } as Response);

    const result = await confirmImport('ds-100', { importJobId: 'job-1', includeDuplicates: false });
    expect(result).toEqual(mockResult);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/confirm',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify({ importJobId: 'job-1', includeDuplicates: false }),
      })
    );
  });

  it('cancelImport sends POST to /api/datasets/:id/imports/:jobId/cancel', async () => {
    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 204,
      json: async () => null,
    } as Response);

    await cancelImport('ds-100', 'job-1');
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/job-1/cancel',
      expect.objectContaining({ method: 'POST' })
    );
  });

  it('getImportHistory calls GET /api/datasets/:id/imports', async () => {
    const mockHistory = [
      {
        id: 'job-1',
        datasetId: 'ds-100',
        status: 'COMPLETED',
        originalFilename: 'sales.csv',
        policy: 'IMPORT_VALID_ONLY',
        totalRows: 10,
        validRows: 10,
        invalidRows: 0,
        duplicateRows: 0,
        importedRows: 10,
        createdAt: '2026-01-01T00:00:00Z',
        completedAt: '2026-01-01T00:01:00Z',
      },
    ];

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockHistory,
    } as Response);

    const result = await getImportHistory('ds-100');
    expect(result).toEqual(mockHistory);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('getJobStatus calls GET /api/datasets/:id/imports/:jobId', async () => {
    const mockStatus = {
      id: 'job-1',
      datasetId: 'ds-100',
      status: 'VALIDATED',
      policy: 'IMPORT_VALID_ONLY',
      fileName: 'sales.csv',
      rowsProcessed: 100,
      totalRows: 100,
      validRows: 98,
      invalidRows: 2,
      duplicateRows: 0,
      warningCount: 0,
      blankRowsSkipped: 0,
      issuesTruncated: false,
      canConfirm: true,
      confirmBlockedReason: null,
      fileError: null,
      expiresAt: '2026-01-01T01:00:00Z',
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockStatus,
    } as Response);

    const { getJobStatus } = await import('./imports');
    const result = await getJobStatus('ds-100', 'job-1');
    expect(result).toEqual(mockStatus);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/job-1',
      expect.objectContaining({ credentials: 'same-origin' })
    );
  });

  it('editRow sends PATCH to /api/datasets/:id/imports/:jobId/rows/:rowNumber', async () => {
    const mockResponse = {
      row: {
        rowNumber: 3,
        status: 'VALID',
        values: {
          orderId: 'ORD-1001',
          orderDate: '2026-02-10',
          productName: 'Product A',
          category: 'Electronics',
          region: 'North',
          quantity: 10,
          unitPrice: '500.00',
          unitCost: '300.00',
        },
        rawValues: null,
        issues: [],
      },
      counts: {
        total: 10,
        valid: 9,
        invalid: 1,
        duplicateCandidate: 0,
        warnings: 0,
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    } as Response);

    const editPayload = {
      orderId: 'ORD-1001',
      orderDate: '2026-02-10',
      productName: 'Product A',
      category: 'Electronics',
      region: 'North',
      quantity: '10',
      unitPrice: '500.00',
      unitCost: '300.00',
    };

    const result = await editRow('ds-100', 'job-1', 3, editPayload);
    expect(result).toEqual(mockResponse);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/job-1/rows/3',
      expect.objectContaining({
        method: 'PATCH',
        body: JSON.stringify(editPayload),
      })
    );
  });

  it('applyBulkFix sends POST to /api/datasets/:id/imports/:jobId/bulk-fix', async () => {
    const mockResponse = {
      repairedToValid: 4,
      remainingInvalid: 1,
      duplicateCandidateCount: 1,
      untouchedLargeQuantityCount: 1,
      counts: {
        total: 10,
        valid: 8,
        invalid: 1,
        duplicateCandidate: 1,
        warnings: 0,
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    } as Response);

    const bulkFixPayload = {
      negativePriceStrategy: 'ABS_VALUE' as const,
      decimalQuantityStrategy: 'ROUND_HALF_UP' as const,
      zeroQuantityStrategy: 'SET_ONE' as const,
      dateFixStrategy: 'LATEST_VALID' as const,
      customDate: null,
      fillMissingProduct: true,
      productPlaceholder: 'Unspecified Product',
      fillMissingCategory: true,
      categoryPlaceholder: 'Uncategorized',
      fillMissingRegion: true,
      regionPlaceholder: 'Unassigned',
    };

    const result = await applyBulkFix('ds-100', 'job-1', bulkFixPayload);
    expect(result).toEqual(mockResponse);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/job-1/bulk-fix',
      expect.objectContaining({
        method: 'POST',
        body: JSON.stringify(bulkFixPayload),
      })
    );
  });

  it('discardDuplicates sends POST to /api/datasets/:id/imports/:jobId/discard-duplicates', async () => {
    const mockResponse = {
      discardedCount: 2,
      counts: {
        total: 12,
        valid: 12,
        invalid: 0,
        duplicateCandidate: 0,
        warnings: 0,
      },
    };

    globalThis.fetch = vi.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => mockResponse,
    } as Response);

    const result = await discardDuplicates('ds-100', 'job-1');
    expect(result).toEqual(mockResponse);
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/datasets/ds-100/imports/job-1/discard-duplicates',
      expect.objectContaining({ method: 'POST' })
    );
  });
});


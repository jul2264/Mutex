import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { buildExportUrl, downloadExport } from './reports';
import { ApiError } from './apiFetch';

describe('reports API client', () => {
  describe('buildExportUrl', () => {
    it('constructs basic export URL without filters', () => {
      const url = buildExportUrl('dataset-123', 'csv', 'records');
      expect(url).toBe('/api/datasets/dataset-123/reports/export?format=csv&type=records');
    });

    it('constructs export URL with all filter parameters', () => {
      const url = buildExportUrl('dataset-123', 'xlsx', 'summary', {
        from: '2026-01-01',
        to: '2026-02-28',
        product: 'Widget A',
        compare: 'PREVIOUS_PERIOD',
        category: ['Electronics', 'Home'],
        region: ['North', 'West'],
      });

      expect(url).toContain('format=xlsx');
      expect(url).toContain('type=summary');
      expect(url).toContain('from=2026-01-01');
      expect(url).toContain('to=2026-02-28');
      expect(url).toContain('product=Widget+A');
      expect(url).toContain('compare=PREVIOUS_PERIOD');
      expect(url).toContain('category=Electronics');
      expect(url).toContain('category=Home');
      expect(url).toContain('region=North');
      expect(url).toContain('region=West');
    });
  });

  describe('downloadExport', () => {
    let originalFetch: typeof globalThis.fetch;

    beforeEach(() => {
      originalFetch = globalThis.fetch;

      const mockLink = {
        href: '',
        download: '',
        click: vi.fn(),
      };

      (globalThis as any).window = {
        URL: {
          createObjectURL: vi.fn().mockReturnValue('blob:http://localhost/test-blob'),
          revokeObjectURL: vi.fn(),
        },
      };

      (globalThis as any).document = {
        createElement: vi.fn().mockReturnValue(mockLink),
        body: {
          appendChild: vi.fn(),
          removeChild: vi.fn(),
        },
      };
    });

    afterEach(() => {
      globalThis.fetch = originalFetch;
      delete (globalThis as any).window;
      delete (globalThis as any).document;
      vi.restoreAllMocks();
    });

    it('triggers browser download with filename from Content-Disposition', async () => {
      const mockBlob = new Blob(['order_id,quantity\nA-1,10'], { type: 'text/csv' });
      const mockResponse = {
        ok: true,
        status: 200,
        headers: new Headers({
          'Content-Disposition': 'attachment; filename="sales_2026.csv"',
          'Content-Type': 'text/csv',
        }),
        blob: vi.fn().mockResolvedValue(mockBlob),
      };

      globalThis.fetch = vi.fn().mockResolvedValue(mockResponse as unknown as Response);

      await downloadExport('dataset-123', 'csv', 'records');

      expect(globalThis.fetch).toHaveBeenCalledWith(
        '/api/datasets/dataset-123/reports/export?format=csv&type=records',
        { method: 'GET', credentials: 'include' }
      );
      expect((globalThis as any).window.URL.createObjectURL).toHaveBeenCalledWith(mockBlob);
      expect((globalThis as any).window.URL.revokeObjectURL).toHaveBeenCalledWith('blob:http://localhost/test-blob');
    });

    it('throws ApiError when server returns 422 EXPORT_TOO_LARGE', async () => {
      const errorJson = {
        status: 422,
        code: 'EXPORT_TOO_LARGE',
        title: 'Export Too Large',
        detail: 'Export exceeds 100,000 rows',
      };

      const mockResponse = {
        ok: false,
        status: 422,
        headers: new Headers({ 'Content-Type': 'application/problem+json' }),
        json: vi.fn().mockResolvedValue(errorJson),
      };

      globalThis.fetch = vi.fn().mockResolvedValue(mockResponse as unknown as Response);

      await expect(
        downloadExport('dataset-123', 'csv', 'records')
      ).rejects.toThrow(ApiError);
    });
  });
});

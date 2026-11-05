import { describe, it, expect } from 'vitest';
import { parseFilterParams, serializeFilterParams, getDatePreset, computePresetDates } from './filters';

describe('filters lib', () => {
  it('parses empty search params to undefined defaults', () => {
    const params = new URLSearchParams();
    const result = parseFilterParams(params);
    expect(result.from).toBeUndefined();
    expect(result.to).toBeUndefined();
    expect(result.category).toBeUndefined();
    expect(result.region).toBeUndefined();
    expect(result.product).toBeUndefined();
    expect(result.compare).toBeUndefined();
  });

  it('parses full filter query with repeated arrays', () => {
    const params = new URLSearchParams(
      'from=2026-01-01&to=2026-03-31&category=Electronics&category=Furniture&region=North&product=Laptop&compare=PREVIOUS_PERIOD&sort=revenue&direction=desc&limit=25&rankBy=PROFIT'
    );
    const result = parseFilterParams(params);

    expect(result.from).toBe('2026-01-01');
    expect(result.to).toBe('2026-03-31');
    expect(result.category).toEqual(['Electronics', 'Furniture']);
    expect(result.region).toEqual(['North']);
    expect(result.product).toBe('Laptop');
    expect(result.compare).toBe('PREVIOUS_PERIOD');
    expect(result.sort).toBe('revenue');
    expect(result.direction).toBe('desc');
    expect(result.limit).toBe(25);
    expect(result.rankBy).toBe('PROFIT');
  });

  it('serializes filter object to URLSearchParams correctly', () => {
    const search = serializeFilterParams({
      from: '2026-01-01',
      to: '2026-02-28',
      category: ['Electronics', 'Stationery'],
      region: ['East'],
      product: 'Desk',
      compare: 'PREVIOUS_PERIOD',
      limit: 10,
      rankBy: 'REVENUE',
    });

    expect(search.get('from')).toBe('2026-01-01');
    expect(search.get('to')).toBe('2026-02-28');
    expect(search.getAll('category')).toEqual(['Electronics', 'Stationery']);
    expect(search.getAll('region')).toEqual(['East']);
    expect(search.get('product')).toBe('Desk');
    expect(search.get('compare')).toBe('PREVIOUS_PERIOD');
    expect(search.get('limit')).toBe('10');
    expect(search.get('rankBy')).toBe('REVENUE');
  });

  it('detects and computes presets', () => {
    expect(getDatePreset()).toBe('all');
    expect(getDatePreset('2026-01-01', '2026-01-10')).toBe('custom');

    const last30 = computePresetDates('last30');
    expect(last30.from).toBeDefined();
    expect(last30.to).toBeDefined();
    expect(getDatePreset(last30.from, last30.to)).toBe('last30');

    const lastMonth = computePresetDates('lastMonth');
    expect(lastMonth.from).toBeDefined();
    expect(lastMonth.to).toBeDefined();
    expect(getDatePreset(lastMonth.from, lastMonth.to)).toBe('lastMonth');
  });
});

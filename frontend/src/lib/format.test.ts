import { describe, it, expect } from 'vitest';
import { formatMoney, formatPercent, formatCount, formatDate } from './format';

describe('format utilities', () => {
  it('formats money in INR with currency symbol and 2 decimal places', () => {
    expect(formatMoney('9000.00', 'INR')).toBe('₹9,000.00');
    expect(formatMoney('3000.00', 'INR')).toBe('₹3,000.00');
  });

  it('formats money gracefully when null or empty', () => {
    expect(formatMoney(null)).toBe('—');
    expect(formatMoney(undefined)).toBe('—');
  });

  it('formats percentages to 2 decimal places with % symbol', () => {
    expect(formatPercent('33.33')).toBe('33.33%');
    expect(formatPercent('-20.00')).toBe('-20.00%');
  });

  it('formats percentages gracefully when null', () => {
    expect(formatPercent(null)).toBe('—');
  });

  it('formats counts as localized integers', () => {
    expect(formatCount(15)).toBe('15');
    expect(formatCount(2)).toBe('2');
    expect(formatCount(0)).toBe('0');
    expect(formatCount(null)).toBe('0');
  });

  it('formats dates from ISO YYYY-MM-DD to DD/MM/YYYY', () => {
    expect(formatDate('2026-01-15')).toBe('15/01/2026');
    expect(formatDate('2026-02-28')).toBe('28/02/2026');
    expect(formatDate(null)).toBe('—');
    expect(formatDate(undefined)).toBe('—');
  });

  it('formats negative money correctly', () => {
    expect(formatMoney('-200.00', 'INR')).toBe('₹-200.00');
  });
});


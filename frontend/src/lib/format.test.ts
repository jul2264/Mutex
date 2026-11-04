import { describe, it, expect } from 'vitest';
import { formatMoney, formatPercent, formatCount } from './format';

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
});

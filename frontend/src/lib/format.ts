export function formatMoney(amount: string | null | undefined, currency: string = 'INR'): string {
  if (!amount) return '—';
  const num = Number(amount);
  if (isNaN(num)) return amount;

  const currencySymbol = currency === 'INR' ? '₹' : currency;

  const formatted = new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);

  return `${currencySymbol}${formatted}`;
}

export function formatPercent(percent: string | null | undefined): string {
  if (!percent) return '—';
  const num = Number(percent);
  if (isNaN(num)) return percent;

  const formatted = new Intl.NumberFormat('en-IN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(num);

  return `${formatted}%`;
}

export function formatCount(count: number | null | undefined): string {
  if (count === null || count === undefined) return '0';
  return new Intl.NumberFormat('en-IN').format(count);
}

export function formatDate(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  try {
    const parts = dateStr.split('-');
    if (parts.length === 3) {
      const [year, month, day] = parts;
      return `${day}/${month}/${year}`;
    }
    return dateStr;
  } catch {
    return dateStr;
  }
}


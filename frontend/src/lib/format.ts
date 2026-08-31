// Small formatting + aggregation helpers shared across pages.

export function formatDateTime(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '—';
  return d.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function relativeTime(iso?: string): string {
  if (!iso) return '—';
  const d = new Date(iso).getTime();
  if (Number.isNaN(d)) return '—';
  const diff = Date.now() - d;
  const m = Math.floor(diff / 60_000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m}m ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h}h ago`;
  const days = Math.floor(h / 24);
  return `${days}d ago`;
}

export function formatCurrency(amount?: number, currency = 'USD'): string {
  if (amount === undefined || amount === null) return '—';
  try {
    return new Intl.NumberFormat(undefined, {
      style: 'currency',
      currency,
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${currency} ${amount}`;
  }
}

export function shortId(id?: string, len = 8): string {
  if (!id) return '—';
  return id.length > len ? `${id.slice(0, len)}…` : id;
}

/** Count rows by a key extractor, returning sorted desc entries. */
export function countBy<T>(rows: T[], key: (row: T) => string): { label: string; value: number }[] {
  const counts = new Map<string, number>();
  for (const row of rows) {
    const k = key(row) || '—';
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  return Array.from(counts.entries())
    .map(([label, value]) => ({ label, value }))
    .sort((a, b) => b.value - a.value);
}

export function lastNHourLabels(hours = 24): string[] {
  const out: string[] = [];
  const now = new Date();
  for (let i = hours - 1; i >= 0; i -= 1) {
    const d = new Date(now.getTime() - i * 3600_000);
    out.push(`${String(d.getHours()).padStart(2, '0')}:00`);
  }
  return out;
}

/** Shared categorical palette used by donut/stacked charts. */
export const donutColors = [
  '#f43f5e',
  '#f59e0b',
  '#22d3ee',
  '#34d399',
  '#a78bfa',
  '#60a5fa',
  '#fb7185',
];
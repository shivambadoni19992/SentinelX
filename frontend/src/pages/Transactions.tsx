import { useMemo, useState } from 'react';
import { mockPayments } from '../api/mock';
import { listPayments } from '../api/endpoints';
import type { Payment } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import {
  Toolbar,
  FilterSelect,
  SearchInput,
  ToolbarSpacer,
} from '../components/ui/Filter';
import { BarChart } from '../components/charts/BarChart';
import { formatCurrency, formatDateTime, relativeTime, shortId } from '../lib/format';

const STATUSES = ['PENDING', 'APPROVED', 'HELD', 'DECLINED'];

export function Transactions() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listPayments,
    mockPayments,
    [],
    { fallback: 'auto', demoLabel: 'payments API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data.filter((p) => {
      const matchesQ =
        !q ||
        (p.paymentId || '').toLowerCase().includes(q) ||
        (p.customerId || '').toLowerCase().includes(q) ||
        (p.merchantId || '').toLowerCase().includes(q);
      const matchesStatus = status === 'all' || (p.status || '').toUpperCase() === status;
      return matchesQ && matchesStatus;
    });
  }, [data, query, status]);

  const held = data.filter((p) => (p.status || '').toUpperCase() === 'HELD').length;
  const declined = data.filter((p) => (p.status || '').toUpperCase() === 'DECLINED').length;
  const approved = data.filter((p) => (p.status || '').toUpperCase() === 'APPROVED').length;
  const reviewed = held + declined;

  const statusTotals = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((p) => {
      const k = (p.status || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + (p.amount ?? 0);
    });
    return Object.entries(map)
      .map(([label, value]) => ({ label, value: Math.round(value) }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 6);
  }, [data]);

  const columns: Column<Payment>[] = [
    { key: 'payment', header: 'Payment', render: (p) => <span className="mono">{shortId(p.paymentId)}</span> },
    { key: 'customer', header: 'Customer', render: (p) => <span className="muted">{shortId(p.customerId, 10)}</span> },
    { key: 'merchant', header: 'Merchant', render: (p) => <span className="muted">{shortId(p.merchantId, 10)}</span> },
    { key: 'amount', header: 'Amount', render: (p) => <strong>{formatCurrency(p.amount, p.currency)}</strong> },
    { key: 'device', header: 'Device', render: (p) => <span className="mono muted">{p.deviceId ?? '—'}</span> },
    { key: 'ip', header: 'IP', render: (p) => <span className="mono muted">{p.ipAddress ?? '—'}</span> },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
    { key: 'time', header: 'Created', render: (p) => <span className="muted" title={formatDateTime(p.createdAt)}>{relativeTime(p.createdAt)}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="Transactions could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Approved" value={approved} tone="good" icon="✓" />
        <StatCard label="Held" value={held} tone="warn" icon="⏸" />
        <StatCard label="Declined" value={declined} tone="bad" icon="⛔" />
        <StatCard label="Needs Review" value={reviewed} tone="info" icon="⚑" />
      </div>

      <Card title="Volume by Status">
        <BarChart values={statusTotals.map((s) => s.value)} labels={statusTotals.map((s) => s.label)} color="#22d3ee" />
      </Card>

      <Card
        title={`Transactions (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search payment / customer / merchant…" />
            <FilterSelect
              label="Status"
              value={status}
              onChange={setStatus}
              options={[{ value: 'all', label: 'ALL' }, ...STATUSES.map((s) => ({ value: s, label: s }))]}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(p) => p.paymentId} loading={loading} itemName="transactions" />
      </Card>
    </div>
  );
}
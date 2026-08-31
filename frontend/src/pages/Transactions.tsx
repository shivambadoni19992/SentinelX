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

const STATUSES = ['SETTLED', 'HELD', 'FLAGGED', 'BLOCKED', 'REFUNDED', 'PENDING'];
const METHODS = ['CARD', 'BANK_TRANSFER', 'WALLET'];

export function Transactions() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listPayments,
    mockPayments,
    [],
    { fallback: 'auto', demoLabel: 'payments API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [minRisk, setMinRisk] = useState('all');

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data.filter((p) => {
      const matchesQ =
        !q ||
        (p.transactionId || '').toLowerCase().includes(q) ||
        (p.paymentMethod || '').toLowerCase().includes(q) ||
        (p.userId || '').toLowerCase().includes(q);
      const matchesStatus = status === 'all' || (p.status || '').toUpperCase() === status;
      const risk = p.riskScore ?? 0;
      const matchesRisk =
        minRisk === 'all' ||
        (minRisk === 'high' && risk >= 0.7) ||
        (minRisk === 'medium' && risk >= 0.4 && risk < 0.7) ||
        (minRisk === 'low' && risk < 0.4);
      return matchesQ && matchesStatus && matchesRisk;
    });
  }, [data, query, status, minRisk]);

  const held = data.filter((p) => (p.status || '').toUpperCase() === 'HELD').length;
  const blocked = data.filter((p) => (p.status || '').toUpperCase() === 'BLOCKED').length;
  const suspicious = data.filter((p) => (p.riskScore ?? 0) >= 0.6).length;
  const flagged = data.filter((p) => (p.status || '').toUpperCase() === 'FLAGGED').length;

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
    { key: 'txn', header: 'Transaction', render: (p) => <span className="mono">{shortId(p.transactionId)}</span> },
    { key: 'user', header: 'User ID', render: (p) => <span className="muted">{shortId(p.userId, 10)}</span> },
    { key: 'amount', header: 'Amount', render: (p) => <strong>{formatCurrency(p.amount, p.currency)}</strong> },
    { key: 'method', header: 'Method', render: (p) => <span>{p.paymentMethod}</span> },
    { key: 'risk', header: 'Risk', render: (p) => <span className={riskClass(p.riskScore)}>{(p.riskScore ?? 0).toFixed(2)}</span> },
    { key: 'status', header: 'Status', render: (p) => <StatusBadge status={p.status} /> },
    { key: 'time', header: 'Originated', render: (p) => <span className="muted" title={formatDateTime(p.originatedAt)}>{relativeTime(p.originatedAt)}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="Transactions could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Held Transactions" value={held} tone="warn" icon="⏸" />
        <StatCard label="Blocked" value={blocked} tone="bad" icon="⛔" />
        <StatCard label="Suspicious (risk ≥ 0.6)" value={suspicious} tone="bad" icon="⚠" />
        <StatCard label="Flagged" value={flagged} tone="info" icon="⚑" />
      </div>

      <Card title="Volume by Status">
        <BarChart values={statusTotals.map((s) => s.value)} labels={statusTotals.map((s) => s.label)} color="#22d3ee" />
      </Card>

      <Card
        title={`Transactions (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search transaction / user…" />
            <FilterSelect
              label="Status"
              value={status}
              onChange={setStatus}
              options={STATUSES.map((s) => ({ value: s, label: s }))}
            />
            <FilterSelect
              label="Risk"
              value={minRisk}
              onChange={setMinRisk}
              options={[
                { value: 'high', label: 'High (≥0.7)' },
                { value: 'medium', label: 'Medium (0.4–0.7)' },
                { value: 'low', label: 'Low (<0.4)' },
              ]}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(p) => p.id} loading={loading} itemName="transactions" />
      </Card>
    </div>
  );
}

function riskClass(score?: number): string {
  const s = score ?? 0;
  if (s >= 0.7) return 'risk-text-bad';
  if (s >= 0.4) return 'risk-text-warn';
  return 'risk-text-good';
}
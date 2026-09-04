import { useMemo, useState } from 'react';
import { mockAlerts, mockPayments, mockRiskDecisions } from '../api/mock';
import { listAlerts, listPayments, listRiskDecisions } from '../api/endpoints';
import type { RiskDecision } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { RiskBadge, StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Spinner } from '../components/ui/Spinner';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { DonutChart } from '../components/charts/DonutChart';
import { HBarChart } from '../components/charts/BarChart';
import { donutColors, relativeTime, shortId } from '../lib/format';

const LEVELS = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const ACTIONS = ['ALLOW', 'REVIEW', 'CHALLENGE', 'BLOCK'];

export function RiskAnalysis() {
  const decisions = useCollection(listRiskDecisions, mockRiskDecisions, [], {
    fallback: 'auto',
    demoLabel: 'risk API unreachable',
  });
  const payments = useCollection(listPayments, mockPayments, [], {
    fallback: 'auto',
    demoLabel: 'payments API unreachable',
  });

  const loading = decisions.loading || payments.loading;
  const anyDemo = decisions.source === 'demo' || payments.source === 'demo';

  const [query, setQuery] = useState('');
  const [level, setLevel] = useState('all');
  const [action, setAction] = useState('all');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  /** Human-readable reasons a decision scored the way it did. */
  function decisionReasons(d: RiskDecision): Array<{ label: string; points?: number; reasons: string[] }> {
    const factors = (d.factors ?? {}) as {
      signals?: Record<string, unknown>;
      reasons?: unknown;
    };
    const signals = factors.signals ?? {};
    const rawReasons = Array.isArray(factors.reasons) ? (factors.reasons as unknown[]) : [];
    return Object.entries(signals)
      .sort((a, b) => Number(b[1]) - Number(a[1]))
      .map(([signal, points]) => ({
        label: signal,
        points: typeof points === 'number' ? points : undefined,
        reasons: rawReasons.filter(
          (r) => typeof r === 'string' && r.toLowerCase().startsWith(signal.toLowerCase().slice(0, 4)),
        ) as string[],
      }));
  }

  const selected = decisions.data.find((d) => d.id === selectedId) ?? null;

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return decisions.data.filter((d) => {
      const matchesQ =
        !q ||
        (d.subjectId || '').toLowerCase().includes(q) ||
        (d.ruleVersion || '').toLowerCase().includes(q) ||
        (d.subjectType || '').toLowerCase().includes(q);
      const matchesLevel = level === 'all' || (d.riskLevel || '').toUpperCase() === level;
      const matchesAction = action === 'all' || (d.action || '').toUpperCase() === action;
      return matchesQ && matchesLevel && matchesAction;
    });
  }, [decisions.data, query, level, action]);

  const byLevel = useMemo(() => {
    const map: Record<string, number> = {};
    decisions.data.forEach((d) => {
      const k = (d.riskLevel || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return LEVELS.filter((l) => map[l]).map((l, i) => ({
      label: l,
      value: map[l],
      color: donutColors[i % donutColors.length],
    }));
  }, [decisions.data]);

  const topFactors = useMemo(() => {
    const score = new Map<string, number>();
    decisions.data.forEach((d) => {
      Object.entries(d.factors ?? {}).forEach(([k, v]) => {
        if (typeof v === 'number') score.set(k, (score.get(k) ?? 0) + v);
      });
    });
    return Array.from(score.entries())
      .map(([label, value]) => ({ label, value: Math.round(value * 100) / 100 }))
      .sort((a, b) => b.value - a.value)
      .slice(0, 6);
  }, [decisions.data]);

  const avgScore = useMemo(() => {
    if (!decisions.data.length) return 0;
    return decisions.data.reduce((s, d) => s + (d.riskScore ?? 0), 0) / decisions.data.length;
  }, [decisions.data]);

  const highRiskTxns = payments.data.filter((p) => ['HELD', 'DECLINED'].includes((p.status || '').toUpperCase())).length;

  const columns: Column<RiskDecision>[] = [
    {
      key: 'subject',
      header: 'Subject',
      render: (d) => (
        <div className="cell-title">
          <span className="cell-primary">{d.subjectType}</span>
          <span className="cell-secondary mono">{shortId(d.subjectId)}</span>
        </div>
      ),
    },
    { key: 'level', header: 'Risk Level', render: (d) => <RiskBadge level={d.riskLevel} /> },
    { key: 'score', header: 'Score', render: (d) => <strong>{(d.riskScore ?? 0).toFixed(2)}</strong> },
    { key: 'action', header: 'Decision', render: (d) => <StatusBadge status={d.action} /> },
    { key: 'rule', header: 'Rules', render: (d) => <span className="muted">{d.ruleVersion ?? '—'}</span> },
    { key: 'time', header: 'Decided', render: (d) => <span className="muted">{relativeTime(d.decisionAt)}</span> },
  ];

  if (loading) {
    return (
      <div className="page-loading">
        <Spinner label="Loading risk model…" />
      </div>
    );
  }
  if (decisions.source === 'error') {
    return (
      <ErrorState
        title="Risk data could not be loaded"
        message={decisions.error ?? 'Unknown error.'}
        onRetry={decisions.refetch}
      />
    );
  }

  return (
    <div className="page">
      {anyDemo ? <DemoBanner reason="Risk data uses synthetic values." /> : null}

      <div className="stat-grid">
        <StatCard label="Decisions Logged" value={decisions.data.length} tone="info" icon="▲" />
        <StatCard label="Average Risk Score" value={`${(avgScore * 100).toFixed(0)}%`} tone="warn" icon="‰" />
        <StatCard
          label="Blocked Decisions"
          value={decisions.data.filter((d) => (d.action || '').toUpperCase() === 'BLOCK').length}
          tone="bad"
          icon="⛔"
        />
        <StatCard label="High-Risk Transactions" value={highRiskTxns} tone="bad" icon="⇄" />
      </div>

      <div className="grid-2">
        <Card title="Decisions by Risk Level">
          <DonutChart data={byLevel} centerValue={decisions.data.length} centerLabel="decisions" />
        </Card>
        <Card title="Contributing Factors (weighted)">
          <HBarChart items={topFactors} color="#f59e0b" />
        </Card>
      </div>

      <Card
        title={`Risk Decisions (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search subjects…" />
            <FilterSelect
              label="Risk"
              value={level}
              onChange={setLevel}
              options={LEVELS.map((l) => ({ value: l, label: l }))}
            />
            <FilterSelect
              label="Decision"
              value={action}
              onChange={setAction}
              options={ACTIONS.map((a) => ({ value: a, label: a }))}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={decisions.refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable
          columns={columns}
          data={filtered}
          rowKey={(d) => d.id}
          loading={decisions.loading}
          itemName="risk decisions"
          onRowClick={(d) => setSelectedId((cur) => (cur === d.id ? null : d.id))}
        />
      </Card>

      {selected ? (
        <Card
          title={`Why this score — ${selected.subjectType} · ${selected.riskLevel} · ${(selected.riskScore ?? 0).toFixed(2)}`}
          actions={
            <button type="button" className="btn" onClick={() => setSelectedId(null)}>
              Close
            </button>
          }
        >
          <div className="why-panel">
            <p className="muted">
              Action <StatusBadge status={selected.action} /> · decided{' '}
              {relativeTime(selected.decisionAt)} · window{' '}
              {String(
                ((selected.factors as { windowMinutes?: number } | undefined)?.windowMinutes) ?? 15,
              )}{' '}
              min
            </p>
            {(() => {
              const signals = decisionReasons(selected);
              if (!signals.length) {
                return (
                  <p className="muted">
                    No per-signal breakdown recorded for this decision.
                  </p>
                );
              }
              return signals.map(({ label, points, reasons }) => (
                <div key={label} className="why-signal">
                  <strong>
                    {label.split('_').join(' ').toLowerCase()}
                    {points !== undefined ? ` · +${points} pts` : ''}
                  </strong>
                  {reasons.length ? (
                    <ul>
                      {reasons.map((r, i) => (
                        <li key={i}>{r}</li>
                      ))}
                    </ul>
                  ) : null}
                </div>
              ));
            })()}
          </div>
        </Card>
      ) : null}
    </div>
  );
}

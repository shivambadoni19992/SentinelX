import { useMemo, useState } from 'react';
import { mockSimulations } from '../api/mock';
import { listSimulations } from '../api/endpoints';
import type { SimulationRun } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { DonutChart } from '../components/charts/DonutChart';
import { HBarChart } from '../components/charts/BarChart';
import { donutColors, formatDateTime, relativeTime } from '../lib/format';

const STATUSES = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'];

export function SimulationHistory() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listSimulations,
    mockSimulations,
    [],
    { fallback: 'auto', demoLabel: 'simulations API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [scenario, setScenario] = useState('all');

  const scenarios = useMemo(
    () => Array.from(new Set(data.map((s) => s.type ?? s.scenario).filter(Boolean))).sort(),
    [data],
  );

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data
      .filter((s) => {
        const matchesQ =
          !q ||
          (s.name || '').toLowerCase().includes(q) ||
          (s.runBy || '').toLowerCase().includes(q) ||
          (s.scenario || '').toLowerCase().includes(q) ||
          (s.type || '').toLowerCase().includes(q);
        const matchesStatus = status === 'all' || (s.status || '').toUpperCase() === status;
        const matchesScenario = scenario === 'all' || s.scenario === scenario;
        return matchesQ && matchesStatus && matchesScenario;
      })
      .sort((a, b) => {
        const ta = new Date(a.startedAt ?? a.createdAt ?? 0).getTime();
        const tb = new Date(b.startedAt ?? b.createdAt ?? 0).getTime();
        return tb - ta;
      });
  }, [data, query, status, scenario]);

  const byStatus = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((s) => {
      const k = (s.status || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return STATUSES.filter((st) => map[st]).map((st, i) => ({
      label: st,
      value: map[st],
      color: donutColors[i % donutColors.length],
    }));
  }, [data]);

  const byScenario = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((s) => {
      const k = s.scenario || 'UNKNOWN';
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }, [data]);

  const columns: Column<SimulationRun>[] = [
    {
      key: 'name',
      header: 'Run',
      render: (s) => (
        <div className="cell-title">
          <span className="cell-primary">{s.name}</span>
          <span className="cell-secondary">{s.description}</span>
        </div>
      ),
    },
    { key: 'scenario', header: 'Type', render: (s) => <span>{s.type ?? s.scenario}</span> },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    { key: 'runby', header: 'Run By', render: (s) => <span className="muted">{s.runBy ?? '—'}</span> },
    {
      key: 'started',
      header: 'Started',
      render: (s) => <span className="muted" title={formatDateTime(s.startedAt)}>{relativeTime(s.startedAt)}</span>,
    },
    {
      key: 'completed',
      header: 'Completed',
      render: (s) => <span className="muted" title={formatDateTime(s.completedAt)}>{relativeTime(s.completedAt)}</span>,
    },
  ];

  if (source === 'error') {
    return <ErrorState title="Simulation history could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Total Runs" value={data.length} tone="info" icon="◷" />
        <StatCard label="Completed" value={data.filter((s) => (s.status || '').toUpperCase() === 'COMPLETED').length} tone="good" icon="✓" />
        <StatCard label="Failed" value={data.filter((s) => (s.status || '').toUpperCase() === 'FAILED').length} tone="bad" icon="✕" />
        <StatCard label="Scenarios Used" value={scenarios.length} tone="violet" icon="◈" />
      </div>

      <div className="grid-2">
        <Card title="Outcomes">
          <DonutChart data={byStatus} centerValue={data.length} centerLabel="runs" />
        </Card>
        <Card title="Runs by Scenario">
          <HBarChart items={byScenario} color="#60a5fa" />
        </Card>
      </div>

      <Card
        title={`History (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search runs…" />
            <FilterSelect label="Status" value={status} onChange={setStatus} options={STATUSES.map((s) => ({ value: s, label: s }))} />
            <FilterSelect label="Type" value={scenario} onChange={setScenario} options={scenarios.map((s) => ({ value: s ?? '', label: s ?? '' }))} />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(s) => s.simulationId ?? s.id ?? ''} loading={loading} itemName="simulation runs" />
      </Card>
    </div>
  );
}
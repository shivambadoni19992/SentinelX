import { useMemo, useState } from 'react';
import { mockEvents } from '../api/mock';
import { listDetections, listSecurityEvents } from '../api/endpoints';
import type { SecurityEvent } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { SeverityBadge, StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import {
  Toolbar,
  FilterSelect,
  SearchInput,
  ToolbarSpacer,
} from '../components/ui/Filter';
import { BarChart } from '../components/charts/BarChart';
import { donutColors, relativeTime } from '../lib/format';

// Prefer detections endpoint when available; fall back to the events stream.
function fetchApiEvents(): Promise<SecurityEvent[]> {
  return listDetections().catch(() => listSecurityEvents());
}

export function ApiSecurity() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    fetchApiEvents,
    mockEvents.filter((e) => (e.eventType || '').includes('API')),
    [],
    { fallback: 'auto', demoLabel: 'api events unreachable' },
  );

  const apiEvents = useMemo(
    () => data.filter((e) => (e.eventType || '').includes('API')),
    [data],
  );

  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState('all');

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return apiEvents.filter((e) => {
      const matchesQ =
        !q ||
        (e.action || '').toLowerCase().includes(q) ||
        (e.actor || '').toLowerCase().includes(q) ||
        (e.sourceIp || '').toLowerCase().includes(q);
      const matchesSeverity = severity === 'all' || (e.severity || '').toUpperCase() === severity;
      return matchesQ && matchesSeverity;
    });
  }, [apiEvents, query, severity]);

  const attackTypes = useMemo(() => {
    const map: Record<string, number> = {};
    apiEvents.forEach((e) => {
      const k = e.action || 'UNKNOWN';
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }, [apiEvents]);

  const columns: Column<SecurityEvent>[] = [
    { key: 'action', header: 'Action', render: (e) => <strong>{e.action}</strong> },
    { key: 'actor', header: 'Actor', render: (e) => <span className="mono">{e.actor ?? '—'}</span> },
    { key: 'severity', header: 'Severity', render: (e) => <SeverityBadge severity={e.severity} /> },
    { key: 'outcome', header: 'Outcome', render: (e) => <StatusBadge status={e.outcome} /> },
    { key: 'source', header: 'Source IP', render: (e) => <span className="mono muted">{e.sourceIp ?? '—'}</span> },
    { key: 'time', header: 'Detected', render: (e) => <span className="muted">{relativeTime(e.occurredAt)}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="API security data could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="API Attacks" value={apiEvents.length} tone="bad" icon="⌗" />
        <StatCard
          label="Critical"
          value={apiEvents.filter((e) => (e.severity || '').toUpperCase() === 'CRITICAL').length}
          tone="critical"
          icon="⚠"
        />
        <StatCard
          label="Blocked"
          value={apiEvents.filter((e) => (e.outcome || '').toUpperCase() === 'BLOCKED').length}
          tone="good"
          icon="⛔"
        />
        <StatCard
          label="Distinct Sources"
          value={new Set(apiEvents.map((e) => e.sourceIp)).size}
          tone="info"
          icon="⌖"
        />
      </div>

      <Card title="Attack Types">
        <BarChart
          values={attackTypes.map((a) => a.value)}
          labels={attackTypes.map((a) => a.label)}
          color={donutColors[0]}
        />
      </Card>

      <Card
        title={`API Events (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search API events…" />
            <FilterSelect
              label="Severity"
              value={severity}
              onChange={setSeverity}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(e) => e.id} loading={loading} itemName="api events" />
      </Card>
    </div>
  );
}
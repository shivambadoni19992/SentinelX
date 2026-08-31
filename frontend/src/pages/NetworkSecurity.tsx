import { useMemo, useState } from 'react';
import { mockEvents } from '../api/mock';
import { listSecurityEvents } from '../api/endpoints';
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
import { LineChart, ChartLegend } from '../components/charts/LineChart';
import { donutColors, relativeTime } from '../lib/format';
import { buildTimeseries } from '../api/mock';

function fetchNetworkEvents(): Promise<SecurityEvent[]> {
  return listSecurityEvents().then((ev) =>
    ev.filter((e) => (e.eventType || '').includes('NETWORK')),
  );
}

export function NetworkSecurity() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    fetchNetworkEvents,
    mockEvents.filter((e) => (e.eventType || '').includes('NETWORK')),
    [],
    { fallback: 'auto', demoLabel: 'network events unreachable' },
  );

  const networkEvents = useMemo(
    () => data.filter((e) => (e.eventType || '').includes('NETWORK')),
    [data],
  );

  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState('all');

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return networkEvents.filter((e) => {
      const matchesQ =
        !q ||
        (e.action || '').toLowerCase().includes(q) ||
        (e.sourceIp || '').toLowerCase().includes(q);
      const matchesSeverity = severity === 'all' || (e.severity || '').toUpperCase() === severity;
      return matchesQ && matchesSeverity;
    });
  }, [networkEvents, query, severity]);

  const series = useMemo(() => buildTimeseries(5, 24), []);
  // Traffic series (packets) uses a separate seed for a thicker line.
  const trafficSeries = useMemo(() => buildTimeseries(9, 24).map((v) => v * 40), []);

  const columns: Column<SecurityEvent>[] = [
    { key: 'action', header: 'Event', render: (e) => <strong>{e.action}</strong> },
    { key: 'severity', header: 'Severity', render: (e) => <SeverityBadge severity={e.severity} /> },
    { key: 'outcome', header: 'Status', render: (e) => <StatusBadge status={e.outcome} /> },
    { key: 'source', header: 'Source IP', render: (e) => <span className="mono muted">{e.sourceIp ?? '—'}</span> },
    { key: 'time', header: 'Detected', render: (e) => <span className="muted">{relativeTime(e.occurredAt)}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="Network data could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Network Threats" value={networkEvents.length} tone="bad" icon="⬡" />
        <StatCard
          label="Port Scans"
          value={networkEvents.filter((e) => (e.action || '').includes('SCAN')).length}
          tone="warn"
          icon="⌖"
        />
        <StatCard label="Traffic Spikes" value={networkEvents.filter((e) => (e.action || '').includes('SPIKE')).length} tone="info" icon="↗" />
        <StatCard label="Blocked Sources" value={networkEvents.filter((e) => (e.outcome || '').toUpperCase() === 'BLOCKED').length} tone="good" icon="⛔" />
      </div>

      <Card
        title="Inbound Activity (24h)"
        actions={
          <ChartLegend
            items={[
              { label: 'Events', color: donutColors[2] },
              { label: 'Traffic (x40)', color: donutColors[1] },
            ]}
          />
        }
      >
        <LineChart
          series={[
            { name: 'Events', values: series, color: donutColors[2] },
            { name: 'Traffic', values: trafficSeries, color: donutColors[1] },
          ]}
        />
      </Card>

      <Card
        title={`Network Events (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search network events…" />
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
        <DataTable columns={columns} data={filtered} rowKey={(e) => e.id} loading={loading} itemName="network events" />
      </Card>
    </div>
  );
}
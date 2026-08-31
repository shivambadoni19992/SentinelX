import { useMemo } from 'react';
import { fetchServices } from '../api/endpoints';
import type { ServiceHealth } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { ErrorState, EmptyState } from '../components/ui/StateViews';
import { DonutChart } from '../components/charts/DonutChart';
import { relativeTime } from '../lib/format';

// Phase-1 infrastructure, pinned to the compose topology.
const INFRA: { name: string; image: string; port: number }[] = [
  { name: 'PostgreSQL', image: 'postgres:16-alpine', port: 5434 },
  { name: 'Redis', image: 'redis:7-alpine', port: 6379 },
  { name: 'Apache Kafka', image: 'bitnami/kafka:3.7.2', port: 9092 },
  { name: 'OpenSearch', image: 'opensearch:2.17.1', port: 9200 },
  { name: 'Prometheus', image: 'prom/prometheus', port: 9090 },
  { name: 'Grafana', image: 'grafana/grafana', port: 3000 },
];

export function SystemHealth({ gateway }: { gateway: string }) {
  const { data, loading, source, error, refetch } = useCollection<ServiceHealth>(
    fetchServices,
    [],
    [],
    { fallback: 'live-only', demoLabel: 'system services' },
  );

  const up = data.filter((s) => (s.status || '').toUpperCase() === 'UP').length;
  const down = data.length - up;

  const donutData = useMemo(
    () => [
      { label: 'Healthy', value: up, color: '#34d399' },
      { label: 'Unreachable', value: down, color: '#f43f5e' },
    ],
    [up, down],
  );

  const columns: Column<ServiceHealth>[] = [
    {
      key: 'service',
      header: 'Service',
      render: (s) => (
        <div className="cell-title">
          <span className="cell-primary">{s.name}</span>
          <span className="cell-secondary mono">{s.id}</span>
        </div>
      ),
    },
    { key: 'url', header: 'Endpoint', render: (s) => <span className="mono muted">{s.url}</span> },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    { key: 'checked', header: 'Checked', render: (s) => <span className="muted">{relativeTime(s.checkedAt)}</span> },
  ];

  if (source === 'error') {
    return (
      <div className="page">
        <ErrorState
          title="Service health unavailable"
          message={error ?? 'The API gateway could not be reached.'}
          onRetry={refetch}
        />
        <Card title="Infrastructure (Phase 1)">
          <InfraGrid />
        </Card>
      </div>
    );
  }

  return (
    <div className="page">
      <div className="stat-grid">
        <StatCard label="Gateway" value={gateway === 'UP' ? 'Online' : gateway === 'DOWN' ? 'Offline' : '…'} tone={gateway === 'UP' ? 'good' : 'bad'} icon="✚" />
        <StatCard label="Healthy Services" value={up} tone="good" icon="✓" />
        <StatCard label="Unreachable" value={down} tone={down > 0 ? 'bad' : 'neutral'} icon="✕" />
        <StatCard label="Total Services" value={data.length} tone="info" icon="◈" />
      </div>

      <div className="grid-2">
        <Card title="Service Availability">
          {loading ? (
            <p className="muted">Probing services…</p>
          ) : (
            <DonutChart data={donutData} centerValue={up} centerLabel="healthy" />
          )}
        </Card>
        <Card title="Infrastructure (Phase 1)">
          <InfraGrid />
        </Card>
      </div>

      <Card title="Microservice Health">
        <DataTable
          columns={columns}
          data={data}
          rowKey={(s) => s.id}
          loading={loading}
          itemName="services"
          emptyMessage="No services reported yet — the gateway probe has not returned."
          action={
            <button type="button" className="btn" onClick={refetch}>
              Probe again
            </button>
          }
        />
      </Card>
    </div>
  );
}

function InfraGrid() {
  return (
    <div className="infra-grid">
      {INFRA.map((inf) => (
        <div key={inf.name} className="infra-card">
          <span className="dot ok" />
          <div>
            <div className="infra-name">{inf.name}</div>
            <div className="infra-meta">
              {inf.image} · host :{inf.port}
            </div>
          </div>
          <span className="infra-tag">docker</span>
        </div>
      ))}
      {INFRA.length === 0 ? <EmptyState title="No infrastructure" /> : null}
    </div>
  );
}
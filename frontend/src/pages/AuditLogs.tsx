import { useMemo, useState } from 'react';
import { mockAuditLogs } from '../api/mock';
import { listAuditLogs } from '../api/endpoints';
import type { AuditLog } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { formatDateTime, relativeTime, shortId } from '../lib/format';

export function AuditLogs() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listAuditLogs,
    mockAuditLogs,
    [],
    { fallback: 'auto', demoLabel: 'audit API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [result, setResult] = useState('all');
  const [resourceType, setResourceType] = useState('all');

  const resourceTypes = useMemo(
    () => Array.from(new Set(data.map((l) => l.resourceType).filter(Boolean))).sort(),
    [data],
  );
  const results = useMemo(
    () => Array.from(new Set(data.map((l) => l.result).filter(Boolean))).sort(),
    [data],
  );

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data.filter((l) => {
      const matchesQ =
        !q ||
        (l.action || '').toLowerCase().includes(q) ||
        (l.actor || '').toLowerCase().includes(q) ||
        (l.resourceType || '').toLowerCase().includes(q);
      const matchesResult = result === 'all' || (l.result || '').toUpperCase() === result;
      const matchesType = resourceType === 'all' || l.resourceType === resourceType;
      return matchesQ && matchesResult && matchesType;
    });
  }, [data, query, result, resourceType]);

  const denied = data.filter((l) => (l.result || '').toUpperCase() === 'DENIED').length;
  const compromised = data.filter((l) => (l.result || '').toUpperCase() === 'FORCED').length;

  const columns: Column<AuditLog>[] = [
    { key: 'time', header: 'Time', render: (l) => <span className="muted" title={formatDateTime(l.occurredAt)}>{relativeTime(l.occurredAt)}</span> },
    { key: 'action', header: 'Action', render: (l) => <strong>{l.action}</strong> },
    { key: 'actor', header: 'Actor', render: (l) => <span>{l.actor ?? '—'}</span> },
    { key: 'resource', header: 'Resource', render: (l) => <span className="muted">{l.resourceType}</span> },
    { key: 'result', header: 'Result', render: (l) => <StatusBadge status={l.result} /> },
    { key: 'details', header: 'Details', render: (l) => <span className="muted">{l.details ? JSON.stringify(l.details) : '—'}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="Audit logs could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Audit Events" value={data.length} tone="info" icon="≡" />
        <StatCard label="Denied Actions" value={denied} tone="bad" icon="⛔" />
        <StatCard label="Forced/Sessions" value={compromised} tone="warn" icon="⚠" />
        <StatCard label="Distinct Actors" value={new Set(data.map((l) => l.actor)).size} tone="violet" icon="◉" />
      </div>

      <Card
        title={`Audit Trail (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search audit log…" />
            <FilterSelect label="Result" value={result} onChange={setResult} options={results.map((r) => ({ value: r, label: r }))} />
            <FilterSelect label="Resource" value={resourceType} onChange={setResourceType} options={resourceTypes.map((r) => ({ value: r, label: r }))} />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(l) => l.id} loading={loading} itemName="audit entries" />
      </Card>
    </div>
  );
}
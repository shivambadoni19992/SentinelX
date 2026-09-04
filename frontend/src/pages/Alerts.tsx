import { useMemo, useState } from 'react';
import { mockAlerts } from '../api/mock';
import { listAlerts, applyAlertAction, updateAlertStatus } from '../api/endpoints';
import type { SecurityAlert } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { SeverityBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { donutColors, relativeTime, formatDateTime } from '../lib/format';
import { DonutChart } from '../components/charts/DonutChart';

const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const STATUSES = ['OPEN', 'INVESTIGATING', 'RESOLVED', 'FALSE_POSITIVE'];
const ACTIONS = ['ALLOW', 'MONITOR', 'REQUIRE_VERIFICATION', 'HOLD_TRANSACTION', 'BLOCK_ACCOUNT', 'RATE_LIMIT'];

export function Alerts() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listAlerts,
    mockAlerts,
    [],
    { fallback: 'auto', demoLabel: 'alerts API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [severity, setSeverity] = useState('all');
  const [status, setStatus] = useState('all');
  const [entity, setEntity] = useState('all');
  const [selectedAction, setSelectedAction] = useState<Record<string, string>>({});
  const [busyId, setBusyId] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);

  async function applyAction(alert: SecurityAlert) {
    const action = selectedAction[alert.id];
    if (!action) return;
    setBusyId(alert.id);
    setActionError(null);
    try {
      await applyAlertAction(alert.id, action, 'soc-analyst');
      refetch();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to apply action.');
    } finally {
      setBusyId(null);
    }
  }

  async function changeStatus(alert: SecurityAlert, next: string) {
    setBusyId(alert.id);
    setActionError(null);
    try {
      await updateAlertStatus(alert.id, next, 'soc-analyst');
      refetch();
    } catch (e) {
      setActionError(e instanceof Error ? e.message : 'Failed to update status.');
    } finally {
      setBusyId(null);
    }
  }

  const entities = useMemo(
    () => Array.from(new Set(data.map((a) => a.entityType).filter(Boolean))).sort(),
    [data],
  );

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data.filter((a) => {
      const matchesQ =
        !q ||
        (a.title || '').toLowerCase().includes(q) ||
        (a.description || '').toLowerCase().includes(q) ||
        (a.assignedTo || '').toLowerCase().includes(q);
      const matchesSev = severity === 'all' || (a.severity || '').toUpperCase() === severity;
      const matchesStatus = status === 'all' || (a.status || '').toUpperCase() === status;
      const matchesEntity = entity === 'all' || a.entityType === entity;
      return matchesQ && matchesSev && matchesStatus && matchesEntity;
    });
  }, [data, query, severity, status, entity]);

  const bySeverity = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((a) => {
      const k = (a.severity || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return SEVERITIES.filter((s) => map[s]).map((s, i) => ({
      label: s,
      value: map[s],
      color: donutColors[i % donutColors.length],
    }));
  }, [data]);

  const columns: Column<SecurityAlert>[] = [
    {
      key: 'title',
      header: 'Alert',
      render: (a) => (
        <div className="cell-title">
          <span className="cell-primary">{a.title}</span>
          <span className="cell-secondary">{a.description}</span>
        </div>
      ),
    },
    { key: 'severity', header: 'Severity', render: (a) => <SeverityBadge severity={a.severity} /> },
    { key: 'entity', header: 'Entity', render: (a) => <span className="muted">{a.entityType}</span> },
    { key: 'assigned', header: 'Assigned', render: (a) => <span className="muted">{a.assignedTo ?? '—'}</span> },
    { key: 'status', header: 'Status', render: (a) => (
        <select
          className="filter-select"
          value={a.status || 'OPEN'}
          disabled={busyId === a.id}
          onChange={(e) => changeStatus(a, e.target.value)}
        >
          {STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
        </select>
      ) },
    {
      key: 'action',
      header: 'Response Action',
      render: (a) => (
        <div className="alert-action-cell">
          <select
            className="filter-select"
            value={selectedAction[a.id] ?? ''}
            disabled={busyId === a.id}
            onChange={(e) => setSelectedAction((m) => ({ ...m, [a.id]: e.target.value }))}
          >
            <option value="" disabled>Choose action…</option>
            {ACTIONS.map((x) => <option key={x} value={x}>{x}</option>)}
          </select>
          <button
            type="button"
            className="btn"
            disabled={!selectedAction[a.id] || busyId === a.id}
            onClick={() => applyAction(a)}
          >
            {busyId === a.id ? '…' : 'Apply'}
          </button>
          {a.action ? <span className="muted">last: {a.action}</span> : null}
        </div>
      ),
    },
    { key: 'triggered', header: 'Triggered', render: (a) => <span className="muted" title={formatDateTime(a.triggeredAt)}>{relativeTime(a.triggeredAt)}</span> },
  ];

  if (source === 'error') {
    return <ErrorState title="Alerts could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}
      {actionError ? <div className="demo-banner" role="alert">{actionError}</div> : null}

      <div className="grid-2">
        <Card title="Open & Investigating">
          <DonutChart
            data={bySeverity}
            centerValue={data.filter((a) => a.status !== 'RESOLVED' && a.status !== 'FALSE_POSITIVE').length}
            centerLabel="active"
          />
        </Card>
        <Card title="Alerts by Entity Type">
          <div className="entity-tags">
            {entities.length ? (
              entities.map((e) => (
                <button
                  key={e}
                  type="button"
                  className={`entity-tag ${entity === e ? 'active' : ''}`}
                  onClick={() => setEntity(e === entity ? 'all' : e)}
                >
                  {e}
                  <span className="entity-count">{data.filter((a) => a.entityType === e).length}</span>
                </button>
              ))
            ) : (
              <span className="muted">No entity data.</span>
            )}
          </div>
        </Card>
      </div>

      <Card
        title={`Alerts (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search alerts…" />
            <FilterSelect
              label="Severity"
              value={severity}
              onChange={setSeverity}
              options={SEVERITIES.map((s) => ({ value: s, label: s }))}
            />
            <FilterSelect
              label="Status"
              value={status}
              onChange={setStatus}
              options={STATUSES.map((s) => ({ value: s, label: s }))}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(a) => a.id} loading={loading} itemName="alerts" />
      </Card>
    </div>
  );
}
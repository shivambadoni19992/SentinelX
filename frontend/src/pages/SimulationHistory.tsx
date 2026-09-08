import { useMemo, useState, useCallback } from 'react';
import {
  mockSimulations,
  mockSimulationEvents,
  mockSimulationDetections,
  mockSimulationRiskDecisions,
  mockSimulationAlerts,
  mockSimulationActions,
} from '../api/mock';
import { listSimulations } from '../api/endpoints';
import type {
  SimulationRun,
  SimulationEvent,
  SimulationDetection,
  SimulationRiskDecision,
  SimulationAlert,
  SimulationAction,
} from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge, SeverityBadge, RiskBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { DonutChart } from '../components/charts/DonutChart';
import { HBarChart } from '../components/charts/BarChart';
import { donutColors, formatDateTime, relativeTime } from '../lib/format';

const STATUSES = ['PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'];
const SEVERITIES = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
const DATE_RANGES = [
  { value: 'all', label: 'All Time' },
  { value: '1h', label: 'Last Hour' },
  { value: '24h', label: 'Last 24 Hours' },
  { value: '7d', label: 'Last 7 Days' },
  { value: '30d', label: 'Last 30 Days' },
];

function computeDuration(startedAt?: string, completedAt?: string): string {
  if (!startedAt) return '—';
  const start = new Date(startedAt).getTime();
  const end = completedAt ? new Date(completedAt).getTime() : Date.now();
  if (Number.isNaN(start) || Number.isNaN(end)) return '—';
  const seconds = Math.floor((end - start) / 1000);
  if (seconds < 60) return `${seconds}s`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ${seconds % 60}s`;
  const hours = Math.floor(minutes / 60);
  return `${hours}h ${minutes % 60}m`;
}

export function SimulationHistory() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listSimulations,
    mockSimulations,
    [],
    { fallback: 'auto', demoLabel: 'simulations API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [typeFilter, setTypeFilter] = useState('all');
  const [dateRange, setDateRange] = useState('all');
  const [severityFilter, setSeverityFilter] = useState('all');
  const [selectedRun, setSelectedRun] = useState<SimulationRun | null>(null);
  const [detailTab, setDetailTab] = useState<string>('configuration');

  const types = useMemo(
    () => Array.from(new Set(data.map((s) => s.type ?? s.scenario).filter(Boolean))).sort(),
    [data],
  );

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    const now = Date.now();
    return data
      .filter((s) => {
        const simId = (s.simulationId || '').toLowerCase();
        const matchesQ =
          !q ||
          (s.name || '').toLowerCase().includes(q) ||
          (s.runBy || '').toLowerCase().includes(q) ||
          (s.scenario || '').toLowerCase().includes(q) ||
          (s.type || '').toLowerCase().includes(q) ||
          simId.includes(q);
        const matchesStatus = status === 'all' || (s.status || '').toUpperCase() === status;
        const matchesType = typeFilter === 'all' || (s.type ?? s.scenario) === typeFilter;
        const matchesSeverity = severityFilter === 'all' || (s.highestRisk || '').toUpperCase() === severityFilter;
        let matchesDate = true;
        if (dateRange !== 'all' && s.startedAt) {
          const started = new Date(s.startedAt).getTime();
          const diff = now - started;
          const hours = diff / 3600_000;
          if (dateRange === '1h') matchesDate = hours <= 1;
          else if (dateRange === '24h') matchesDate = hours <= 24;
          else if (dateRange === '7d') matchesDate = hours <= 24 * 7;
          else if (dateRange === '30d') matchesDate = hours <= 24 * 30;
        }
        return matchesQ && matchesStatus && matchesType && matchesSeverity && matchesDate;
      })
      .sort((a, b) => {
        const ta = new Date(a.startedAt ?? a.createdAt ?? 0).getTime();
        const tb = new Date(b.startedAt ?? b.createdAt ?? 0).getTime();
        return tb - ta;
      });
  }, [data, query, status, typeFilter, dateRange, severityFilter]);

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

  const byType = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((s) => {
      const k = s.type ?? s.scenario ?? 'UNKNOWN';
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }, [data]);

  const handleRowClick = useCallback((run: SimulationRun) => {
    setSelectedRun(run);
    setDetailTab('configuration');
  }, []);

  const handleCloseDetail = useCallback(() => {
    setSelectedRun(null);
  }, []);

  const columns: Column<SimulationRun>[] = [
    {
      key: 'simulationId',
      header: 'Simulation ID',
      render: (s) => <span className="mono cell-id">{s.simulationId ?? s.id ?? '—'}</span>,
    },
    {
      key: 'type',
      header: 'Type',
      render: (s) => <span className="cell-type">{s.type ?? s.scenario ?? '—'}</span>,
    },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    { key: 'usersAffected', header: 'Users', render: (s) => <span>{(s.usersAffected ?? 0).toLocaleString()}</span> },
    { key: 'transactionsGenerated', header: 'Transactions', render: (s) => <span>{(s.transactionsGenerated ?? 0).toLocaleString()}</span> },
    { key: 'eventsGenerated', header: 'Events', render: (s) => <span>{(s.eventsGenerated ?? 0).toLocaleString()}</span> },
    { key: 'detections', header: 'Detections', render: (s) => <span>{(s.detections ?? 0).toLocaleString()}</span> },
    { key: 'alerts', header: 'Alerts', render: (s) => <span>{(s.alerts ?? 0).toLocaleString()}</span> },
    {
      key: 'highestRisk',
      header: 'Highest Risk',
      render: (s) => <RiskBadge level={s.highestRisk} />,
    },
    {
      key: 'startedAt',
      header: 'Started',
      render: (s) => <span className="muted" title={formatDateTime(s.startedAt)}>{relativeTime(s.startedAt)}</span>,
    },
    {
      key: 'duration',
      header: 'Duration',
      render: (s) => <span className="muted">{computeDuration(s.startedAt, s.completedAt)}</span>,
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
        <StatCard label="Scenarios Used" value={types.length} tone="violet" icon="◈" />
      </div>

      <div className="grid-2">
        <Card title="Outcomes">
          <DonutChart data={byStatus} centerValue={data.length} centerLabel="runs" />
        </Card>
        <Card title="Runs by Type">
          <HBarChart items={byType} color="#60a5fa" />
        </Card>
      </div>

      <Card
        title={`History (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search runs…" />
            <FilterSelect label="Status" value={status} onChange={setStatus} options={STATUSES.map((s) => ({ value: s, label: s }))} />
            <FilterSelect label="Type" value={typeFilter} onChange={setTypeFilter} options={types.map((s) => ({ value: s ?? '', label: s ?? '' }))} />
            <FilterSelect label="Date" value={dateRange} onChange={setDateRange} options={DATE_RANGES} />
            <FilterSelect label="Severity" value={severityFilter} onChange={setSeverityFilter} options={SEVERITIES.map((s) => ({ value: s, label: s }))} />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable
          columns={columns}
          data={filtered}
          rowKey={(s) => s.simulationId ?? s.id ?? ''}
          loading={loading}
          itemName="simulation runs"
          onRowClick={handleRowClick}
        />
      </Card>

      {selectedRun ? (
        <SimulationDetailPanel
          run={selectedRun}
          tab={detailTab}
          onTabChange={setDetailTab}
          onClose={handleCloseDetail}
        />
      ) : null}
    </div>
  );
}

interface DetailPanelProps {
  run: SimulationRun;
  tab: string;
  onTabChange: (tab: string) => void;
  onClose: () => void;
}

function SimulationDetailPanel({ run, tab, onTabChange, onClose }: DetailPanelProps) {
  const events = useMemo(() => mockSimulationEvents(run.simulationId ?? run.id ?? ''), [run]);
  const detections = useMemo(() => mockSimulationDetections(run.simulationId ?? run.id ?? ''), [run]);
  const riskDecisions = useMemo(() => mockSimulationRiskDecisions(run.simulationId ?? run.id ?? ''), [run]);
  const alerts = useMemo(() => mockSimulationAlerts(run.simulationId ?? run.id ?? ''), [run]);
  const actions = useMemo(() => mockSimulationActions(run.simulationId ?? run.id ?? ''), [run]);

  const tabs = [
    { id: 'configuration', label: 'Configuration' },
    { id: 'timeline', label: 'Timeline' },
    { id: 'events', label: `Events (${events.length})` },
    { id: 'detections', label: `Detections (${detections.length})` },
    { id: 'risk', label: `Risk Decisions (${riskDecisions.length})` },
    { id: 'alerts', label: `Alerts (${alerts.length})` },
    { id: 'actions', label: `Actions (${actions.length})` },
  ];

  const config = run.configuration ?? run.config ?? {};

  return (
    <div className="detail-overlay" onClick={onClose}>
      <div className="detail-panel" onClick={(e) => e.stopPropagation()}>
        <div className="detail-header">
          <div className="detail-title-group">
            <h2 className="detail-title">{run.name}</h2>
            <span className="detail-subtitle mono">{run.simulationId ?? run.id}</span>
            {run.campaignId ? <span className="detail-campaign mono">Campaign: {run.campaignId}</span> : null}
          </div>
          <div className="detail-header-actions">
            <StatusBadge status={run.status} />
            <RiskBadge level={run.highestRisk} />
            <button type="button" className="icon-btn" onClick={onClose} aria-label="Close">✕</button>
          </div>
        </div>
        <div className="detail-tabs">
          {tabs.map((t) => (
            <button key={t.id} type="button" className={`detail-tab ${tab === t.id ? 'active' : ''}`} onClick={() => onTabChange(t.id)}>
              {t.label}
            </button>
          ))}
        </div>
        <div className="detail-content">
          {tab === 'configuration' ? <ConfigurationTab run={run} config={config} /> : null}
          {tab === 'timeline' ? <TimelineTab run={run} events={events} detections={detections} alerts={alerts} actions={actions} /> : null}
          {tab === 'events' ? <EventsTab events={events} /> : null}
          {tab === 'detections' ? <DetectionsTab detections={detections} /> : null}
          {tab === 'risk' ? <RiskDecisionsTab decisions={riskDecisions} /> : null}
          {tab === 'alerts' ? <AlertsTab alerts={alerts} /> : null}
          {tab === 'actions' ? <ActionsTab actions={actions} /> : null}
        </div>
      </div>
    </div>
  );
}

function ConfigurationTab({ run, config }: { run: SimulationRun; config: Record<string, unknown> }) {
  const configEntries = Object.entries(config);
  return (
    <div className="config-section">
      <div className="config-grid">
        <div className="config-item"><span className="config-label">Type</span><span className="config-value">{run.type ?? run.scenario ?? '—'}</span></div>
        <div className="config-item"><span className="config-label">Status</span><StatusBadge status={run.status} /></div>
        <div className="config-item"><span className="config-label">Run By</span><span className="config-value">{run.runBy ?? '—'}</span></div>
        <div className="config-item"><span className="config-label">Started</span><span className="config-value">{formatDateTime(run.startedAt)}</span></div>
        <div className="config-item"><span className="config-label">Completed</span><span className="config-value">{formatDateTime(run.completedAt)}</span></div>
        <div className="config-item"><span className="config-label">Duration</span><span className="config-value">{computeDuration(run.startedAt, run.completedAt)}</span></div>
        <div className="config-item"><span className="config-label">Users Affected</span><span className="config-value">{(run.usersAffected ?? 0).toLocaleString()}</span></div>
        <div className="config-item"><span className="config-label">Transactions</span><span className="config-value">{(run.transactionsGenerated ?? 0).toLocaleString()}</span></div>
        <div className="config-item"><span className="config-label">Events Generated</span><span className="config-value">{(run.eventsGenerated ?? 0).toLocaleString()}</span></div>
        <div className="config-item"><span className="config-label">Peak EPS</span><span className="config-value">{(run.peakEps ?? 0).toLocaleString()}</span></div>
        <div className="config-item"><span className="config-label">Highest Risk</span><RiskBadge level={run.highestRisk} /></div>
      </div>
      {configEntries.length > 0 ? (
        <div className="config-raw">
          <h4 className="config-subtitle">Raw Configuration</h4>
          <div className="config-raw-grid">
            {configEntries.map(([key, value]) => (
              <div key={key} className="config-raw-item">
                <span className="config-raw-key">{key}</span>
                <span className="config-raw-value">{Array.isArray(value) ? value.join(', ') : String(value)}</span>
              </div>
            ))}
          </div>
        </div>
      ) : null}
      {run.errors && run.errors.length > 0 ? (
        <div className="config-errors">
          <h4 className="config-subtitle">Errors</h4>
          {run.errors.map((err, i) => (<div key={i} className="config-error-item">{err}</div>))}
        </div>
      ) : null}
    </div>
  );
}

function TimelineTab({ run, events, detections, alerts, actions }: { run: SimulationRun; events: SimulationEvent[]; detections: SimulationDetection[]; alerts: SimulationAlert[]; actions: SimulationAction[] }) {
  const timeline = useMemo(() => {
    const items: { time: string; type: string; label: string; severity?: string }[] = [];
    if (run.startedAt) items.push({ time: run.startedAt, type: 'start', label: 'Simulation started' });
    events.slice(0, 10).forEach((e) => items.push({ time: e.occurredAt, type: 'event', label: e.description ?? e.eventType, severity: e.severity }));
    detections.slice(0, 5).forEach((d) => items.push({ time: d.detectedAt, type: 'detection', label: d.description, severity: d.severity }));
    alerts.slice(0, 5).forEach((a) => items.push({ time: a.triggeredAt, type: 'alert', label: a.title, severity: a.severity }));
    actions.slice(0, 5).forEach((a) => items.push({ time: a.executedAt, type: 'action', label: a.actionType + ' -> ' + a.target }));
    if (run.completedAt) items.push({ time: run.completedAt, type: 'end', label: 'Simulation completed' });
    return items.sort((a, b) => new Date(a.time).getTime() - new Date(b.time).getTime());
  }, [run, events, detections, alerts, actions]);
  return (
    <div className="timeline-section">
      <div className="timeline">
        {timeline.map((item, i) => (
          <div key={i} className={"timeline-item timeline-" + item.type}>
            <div className="timeline-dot" />
            <div className="timeline-content">
              <span className="timeline-time">{relativeTime(item.time)}</span>
              <span className="timeline-label">{item.label}</span>
              {item.severity ? <SeverityBadge severity={item.severity} /> : null}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

function EventsTab({ events }: { events: SimulationEvent[] }) {
  const columns: Column<SimulationEvent>[] = [
    { key: 'occurredAt', header: 'Time', render: (e) => <span className="muted">{relativeTime(e.occurredAt)}</span> },
    { key: 'eventType', header: 'Type', render: (e) => <span className="mono">{e.eventType}</span> },
    { key: 'severity', header: 'Severity', render: (e) => <SeverityBadge severity={e.severity} /> },
    { key: 'action', header: 'Action', render: (e) => <span>{e.action}</span> },
    { key: 'outcome', header: 'Outcome', render: (e) => <StatusBadge status={e.outcome} /> },
    { key: 'sourceIp', header: 'Source IP', render: (e) => <span className="mono">{e.sourceIp ?? '—'}</span> },
    { key: 'userId', header: 'User', render: (e) => <span className="mono">{e.userId ?? '—'}</span> },
  ];
  return <DataTable columns={columns} data={events} rowKey={(e) => e.id} itemName="events" />;
}

function DetectionsTab({ detections }: { detections: SimulationDetection[] }) {
  const columns: Column<SimulationDetection>[] = [
    { key: 'detectedAt', header: 'Time', render: (d) => <span className="muted">{relativeTime(d.detectedAt)}</span> },
    { key: 'ruleName', header: 'Rule', render: (d) => <span>{d.ruleName}</span> },
    { key: 'severity', header: 'Severity', render: (d) => <SeverityBadge severity={d.severity} /> },
    { key: 'confidence', header: 'Confidence', render: (d) => <span>{(d.confidence * 100).toFixed(0)}%</span> },
    { key: 'description', header: 'Description', render: (d) => <span>{d.description}</span> },
  ];
  return <DataTable columns={columns} data={detections} rowKey={(d) => d.id} itemName="detections" />;
}

function RiskDecisionsTab({ decisions }: { decisions: SimulationRiskDecision[] }) {
  const columns: Column<SimulationRiskDecision>[] = [
    { key: 'decidedAt', header: 'Time', render: (d) => <span className="muted">{relativeTime(d.decidedAt)}</span> },
    { key: 'subjectId', header: 'Subject', render: (d) => <span className="mono">{d.subjectId}</span> },
    { key: 'subjectType', header: 'Type', render: (d) => <span>{d.subjectType}</span> },
    { key: 'riskLevel', header: 'Risk', render: (d) => <RiskBadge level={d.riskLevel} /> },
    { key: 'riskScore', header: 'Score', render: (d) => <span>{(d.riskScore * 100).toFixed(0)}%</span> },
    { key: 'action', header: 'Action', render: (d) => <span>{d.action}</span> },
  ];
  return <DataTable columns={columns} data={decisions} rowKey={(d) => d.id} itemName="risk decisions" />;
}

function AlertsTab({ alerts }: { alerts: SimulationAlert[] }) {
  const columns: Column<SimulationAlert>[] = [
    { key: 'triggeredAt', header: 'Time', render: (a) => <span className="muted">{relativeTime(a.triggeredAt)}</span> },
    { key: 'title', header: 'Title', render: (a) => <span>{a.title}</span> },
    { key: 'severity', header: 'Severity', render: (a) => <SeverityBadge severity={a.severity} /> },
    { key: 'status', header: 'Status', render: (a) => <StatusBadge status={a.status} /> },
    { key: 'description', header: 'Description', render: (a) => <span className="muted">{a.description}</span> },
  ];
  return <DataTable columns={columns} data={alerts} rowKey={(a) => a.id} itemName="alerts" />;
}

function ActionsTab({ actions }: { actions: SimulationAction[] }) {
  const columns: Column<SimulationAction>[] = [
    { key: 'executedAt', header: 'Time', render: (a) => <span className="muted">{relativeTime(a.executedAt)}</span> },
    { key: 'actionType', header: 'Action', render: (a) => <span>{a.actionType}</span> },
    { key: 'target', header: 'Target', render: (a) => <span className="mono">{a.target}</span> },
    { key: 'status', header: 'Status', render: (a) => <StatusBadge status={a.status} /> },
    { key: 'reason', header: 'Reason', render: (a) => <span className="muted">{a.reason}</span> },
  ];
  return <DataTable columns={columns} data={actions} rowKey={(a) => a.id} itemName="actions" />;
}

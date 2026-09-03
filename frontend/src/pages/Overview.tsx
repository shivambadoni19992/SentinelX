import { useMemo, useState } from 'react';
import {
  buildTimeseries,
  mockAlerts,
  mockEvents,
  mockOrders,
  mockPayments,
  mockRiskDecisions,
  mockUsers,
} from '../api/mock';
import {
  listAlerts,
  listOrders,
  listPayments,
  listRiskDecisions,
  listSecurityEvents,
  listUsers,
} from '../api/endpoints';
import type { Order, OverviewStats, SecurityAlert } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { LineChart, ChartLegend } from '../components/charts/LineChart';
import { DonutChart } from '../components/charts/DonutChart';
import { DataTable, type Column } from '../components/ui/DataTable';
import { SeverityBadge, StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { Spinner } from '../components/ui/Spinner';
import { Toolbar, FilterSelect, SearchInput, ToolbarSpacer } from '../components/ui/Filter';
import { lastNHourLabels, relativeTime, shortId, formatCurrency } from '../lib/format';

const COLORS = {
  cyan: '#22d3ee',
  rose: '#f43f5e',
  amber: '#f59e0b',
  violet: '#a78bfa',
  green: '#34d399',
  blue: '#60a5fa',
};

interface FilterState {
  query: string;
  severity: string;
  status: string;
}

function computeStats(
  alerts: SecurityAlert[],
  events: typeof mockEvents,
  payments: typeof mockPayments,
  risks: typeof mockRiskDecisions,
  users: typeof mockUsers,
  orders: Order[],
): OverviewStats {
  const criticalAlerts = alerts.filter(
    (a) =>
      (a.severity || '').toUpperCase() === 'CRITICAL' &&
      (a.status || '').toUpperCase() !== 'RESOLVED',
  ).length;
  const highRiskUsers = users.filter((u) => (u.accountStatus || '').toUpperCase() === 'MONITORED')
    .length;
  const suspicious = payments.filter(
    (p) => ['HELD', 'DECLINED'].includes((p.status || '').toUpperCase()),
  ).length;
  const held = payments.filter((p) => (p.status || '').toUpperCase() === 'HELD').length;
  const blockedAccounts = users.filter((u) => (u.accountStatus || '').toUpperCase() === 'BLOCKED')
    .length;
  const apiAttacks = events.filter((e) => (e.eventType || '').includes('API')).length;
  const networkThreats = events.filter((e) => (e.eventType || '').includes('NETWORK')).length;
  const openAlerts = alerts.filter(
    (a) =>
      (a.status || '').toUpperCase() === 'OPEN' ||
      (a.status || '').toUpperCase() === 'INVESTIGATING',
  ).length;

  return {
    securityEvents: events.length,
    criticalAlerts,
    highRiskUsers,
    suspiciousTransactions: suspicious,
    heldTransactions: held,
    blockedAccounts,
    apiAttacks,
    networkThreats,
    totalAlerts: alerts.length,
    totalTransactions: payments.length,
    openAlerts,
    totalOrders: orders.length,
    openOrders: orders.filter((o) =>
      ['PENDING', 'PROCESSING'].includes((o.status || '').toUpperCase()),
    ).length,
  };
}

export function Overview({ onNavigate }: { onNavigate: (r: string) => void }) {
  const alerts = useCollection(listAlerts, mockAlerts, [], {
    fallback: 'auto',
    demoLabel: 'Alerts API unreachable',
  });
  const events = useCollection(listSecurityEvents, mockEvents, [], {
    fallback: 'auto',
    demoLabel: 'Events API unreachable',
  });
  const payments = useCollection(listPayments, mockPayments, [], {
    fallback: 'auto',
    demoLabel: 'Payments API unreachable',
  });
  const risks = useCollection(listRiskDecisions, mockRiskDecisions, [], {
    fallback: 'auto',
    demoLabel: 'Risk API unreachable',
  });
  const users = useCollection(listUsers, mockUsers, [], {
    fallback: 'auto',
    demoLabel: 'Users API unreachable',
  });
  const orders = useCollection(listOrders, mockOrders, [], {
    fallback: 'auto',
    demoLabel: 'Orders API unreachable',
  });

  const [filters, setFilters] = useState<FilterState>({ query: '', severity: 'all', status: 'all' });

  const stats = useMemo(
    () => computeStats(alerts.data, events.data, payments.data, risks.data, users.data, orders.data),
    [alerts.data, events.data, payments.data, risks.data, users.data, orders.data],
  );

  const anyLoading = alerts.loading || events.loading || payments.loading || risks.loading || users.loading;
  const anyDemo = [alerts, events, payments, risks, users, orders].some((c) => c.source === 'demo');

  const eventLabels = lastNHourLabels(24);
  const chartData = useMemo(() => buildTimeseries(3, 24), []);
  const alertSeries = useMemo(() => buildTimeseries(7, 24), []);
  const apiSeries = useMemo(() => buildTimeseries(11, 24), []);

  const bySeverity = useMemo(() => {
    const map: Record<string, number> = {};
    alerts.data.forEach((a) => {
      const k = (a.severity || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return (['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'] as const).map((k) => ({
      label: k,
      value: map[k] ?? 0,
      color: k === 'CRITICAL' ? COLORS.rose : k === 'HIGH' ? COLORS.amber : k === 'MEDIUM' ? COLORS.cyan : COLORS.green,
    }));
  }, [alerts.data]);

  const byTxnStatus = useMemo(() => {
    const map: Record<string, number> = {};
    payments.data.forEach((p) => {
      const k = (p.status || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map).map(([label, value], i) => ({
      label,
      value,
      color: [COLORS.green, COLORS.rose, COLORS.amber, COLORS.blue, COLORS.violet, COLORS.cyan][i % 6],
    }));
  }, [payments.data]);

  const filteredAlerts = alerts.data
    .filter((a) => {
      const q = filters.query.toLowerCase();
      const matchesQ =
        !q ||
        (a.title || '').toLowerCase().includes(q) ||
        (a.description || '').toLowerCase().includes(q) ||
        (a.assignedTo || '').toLowerCase().includes(q);
      const matchesSeverity =
        filters.severity === 'all' || (a.severity || '').toUpperCase() === filters.severity;
      const matchesStatus =
        filters.status === 'all' || (a.status || '').toUpperCase() === filters.status;
      return matchesQ && matchesSeverity && matchesStatus;
    })
    .slice(0, 8);

  const alertColumns: Column<SecurityAlert>[] = [
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
    { key: 'status', header: 'Status', render: (a) => <StatusBadge status={a.status} /> },
    { key: 'time', header: 'Triggered', render: (a) => <span className="muted">{relativeTime(a.triggeredAt)}</span> },
  ];
if (anyLoading) {
    return (
      <div className="page-loading">
        <Spinner label="Assembling live SOC overview…" />
      </div>
    );
  }

  const statCards: {
    label: string;
    value: number;
    tone: 'good' | 'bad' | 'warn' | 'info' | 'violet' | 'critical';
    icon: string;
    spark?: number[];
  }[] = [
    { label: 'Security Events', value: stats.securityEvents, tone: 'info', icon: '⚡', spark: chartData },
    { label: 'Critical Alerts', value: stats.criticalAlerts, tone: 'critical', icon: '⚠', spark: alertSeries },
    { label: 'High Risk Users', value: stats.highRiskUsers, tone: 'violet', icon: '◉' },
    { label: 'Suspicious Transactions', value: stats.suspiciousTransactions, tone: 'bad', icon: '⇄' },
    { label: 'Held Transactions', value: stats.heldTransactions, tone: 'warn', icon: '⏸' },
    { label: 'Blocked Accounts', value: stats.blockedAccounts, tone: 'bad', icon: '⛔' },
    { label: 'API Attacks', value: stats.apiAttacks, tone: 'warn', icon: '⌗', spark: apiSeries },
    { label: 'Network Threats', value: stats.networkThreats, tone: 'bad', icon: '⬡' },
    { label: 'Retail Orders', value: stats.totalOrders, tone: 'info', icon: '▤' },
    { label: 'Open Orders', value: stats.openOrders, tone: 'good', icon: '⇅' },
  ];

  const byOrderStatus = useMemo(() => {
    const map: Record<string, number> = {};
    orders.data.forEach((o) => {
      const k = (o.status || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map).map(([label, value], i) => ({
      label,
      value,
      color: [COLORS.blue, COLORS.amber, COLORS.green, COLORS.violet, COLORS.rose, COLORS.cyan][i % 6],
    }));
  }, [orders.data]);

  const recentOrders = orders.data.slice(0, 6);
  const orderColumns: Column<Order>[] = [
    { key: 'order', header: 'Order', render: (o) => <span className="mono">{shortId(o.id)}</span> },
    { key: 'user', header: 'Customer', render: (o) => <span className="muted">{shortId(o.userId, 10)}</span> },
    { key: 'total', header: 'Total', render: (o) => <strong>{formatCurrency(o.totalAmount, o.currency)}</strong> },
    { key: 'status', header: 'Status', render: (o) => <StatusBadge status={o.status} /> },
    { key: 'time', header: 'Placed', render: (o) => <span className="muted">{relativeTime(o.placedAt)}</span> },
  ];

  return (
    <div className="page">
      {anyDemo ? (
        <DemoBanner reason="Some services fell back to synthetic data." />
      ) : null}
      {alerts.source === 'error' ? (
        <ErrorState message={alerts.error ?? 'Failed to load alerts.'} onRetry={alerts.refetch} />
      ) : null}

      <div className="stat-grid">
        {statCards.map((card) => (
          <StatCard
            key={card.label}
            label={card.label}
            value={card.value}
            tone={card.tone}
            icon={card.icon}
            spark={card.spark}
          />
        ))}
      </div>

      <div className="grid-2">
        <Card
          title="Event Activity (24h)"
          actions={<ChartLegend items={[{ label: 'Events', color: COLORS.cyan }]} />}
        >
          <LineChart series={[{ name: 'Events', values: chartData, color: COLORS.cyan }]} labels={eventLabels} />
        </Card>
        <Card
          title="Alert Trends (24h)"
          actions={
            <ChartLegend
              items={[
                { label: 'Alerts', color: COLORS.rose },
                { label: 'API attacks', color: COLORS.amber },
              ]}
            />
          }
        >
          <LineChart
            series={[
              { name: 'Alerts', values: alertSeries, color: COLORS.rose },
              { name: 'API', values: apiSeries, color: COLORS.amber },
            ]}
            labels={eventLabels}
          />
        </Card>
      </div>

      <div className="grid-2">
        <Card title="Alerts by Severity">
          <DonutChart data={bySeverity} centerValue={stats.totalAlerts} centerLabel="total alerts" />
        </Card>
        <Card title="Transactions by Status">
          <DonutChart data={byTxnStatus} centerValue={stats.totalTransactions} centerLabel="transactions" />
        </Card>
        <Card title="Orders by Status">
          <DonutChart data={byOrderStatus} centerValue={stats.totalOrders} centerLabel="orders" />
        </Card>
      </div>

      <Card title="Recent Orders">
        <DataTable
          columns={orderColumns}
          data={recentOrders}
          rowKey={(o) => o.id}
          loading={orders.loading}
          itemName="orders"
        />
      </Card>

      <Card
        title="Priority Alerts"
        actions={
          <Toolbar>
            <SearchInput
              value={filters.query}
              onChange={(v) => setFilters({ ...filters, query: v })}
              placeholder="Filter alerts…"
            />
            <FilterSelect
              label="Severity"
              value={filters.severity}
              onChange={(v) => setFilters({ ...filters, severity: v })}
              options={['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].map((s) => ({ value: s, label: s }))}
            />
            <FilterSelect
              label="Status"
              value={filters.status}
              onChange={(v) => setFilters({ ...filters, status: v })}
              options={['OPEN', 'INVESTIGATING', 'ACKNOWLEDGED', 'RESOLVED'].map((s) => ({
                value: s,
                label: s,
              }))}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={() => onNavigate('alerts')}>
              View all →
            </button>
          </Toolbar>
        }
      >
        <DataTable
          columns={alertColumns}
          data={filteredAlerts}
          rowKey={(a) => a.id}
          loading={alerts.loading}
          itemName="alerts"
        />
      </Card>
    </div>
  );
}

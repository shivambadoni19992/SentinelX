import { useState } from 'react';
import { Card } from '../components/ui/Card';

interface DashboardPanel {
  id: string;
  title: string;
  type: 'graph' | 'stat' | 'gauge' | 'table';
  metric: string;
  description: string;
}

interface GrafanaDashboard {
  id: string;
  title: string;
  description: string;
  icon: string;
  panels: DashboardPanel[];
}

const DASHBOARDS: GrafanaDashboard[] = [
  {
    id: 'platform',
    title: 'Platform',
    description: 'Overall platform health and performance metrics',
    icon: '◈',
    panels: [
      { id: 'http_requests', title: 'HTTP Requests/sec', type: 'graph', metric: 'sentinelx_http_requests_total', description: 'Total HTTP requests by method and status' },
      { id: 'http_latency', title: 'HTTP Latency (p95)', type: 'graph', metric: 'sentinelx_http_request_duration_seconds', description: 'Request latency distribution' },
      { id: 'http_errors', title: 'HTTP Errors', type: 'stat', metric: 'sentinelx_http_errors_total', description: 'Total HTTP error responses' },
      { id: 'api_requests', title: 'API Requests', type: 'graph', metric: 'sentinelx_api_requests_total', description: 'API requests by endpoint' },
      { id: 'api_errors', title: 'API Errors', type: 'stat', metric: 'sentinelx_api_errors_total', description: 'API error count' },
    ],
  },
  {
    id: 'kafka',
    title: 'Kafka',
    description: 'Kafka message production and consumption metrics',
    icon: '⚡',
    panels: [
      { id: 'kafka_messages', title: 'Messages/sec', type: 'graph', metric: 'sentinelx_kafka_messages_total', description: 'Kafka messages produced and consumed' },
      { id: 'kafka_errors', title: 'Kafka Errors', type: 'stat', metric: 'sentinelx_kafka_errors_total', description: 'Kafka processing errors' },
      { id: 'kafka_topics', title: 'Messages by Topic', type: 'table', metric: 'sentinelx_kafka_messages_total', description: 'Message distribution across topics' },
    ],
  },
  {
    id: 'security',
    title: 'Security',
    description: 'Security events and threat detection metrics',
    icon: '🛡',
    panels: [
      { id: 'security_events', title: 'Security Events', type: 'graph', metric: 'sentinelx_security_events_total', description: 'Security events by type and severity' },
      { id: 'detections', title: 'Detections', type: 'graph', metric: 'sentinelx_detections_total', description: 'Threat detections by rule' },
      { id: 'blocked_users', title: 'Blocked Users', type: 'stat', metric: 'sentinelx_blocked_users_total', description: 'Total blocked user accounts' },
    ],
  },
  {
    id: 'risk',
    title: 'Risk',
    description: 'Risk assessment and decision metrics',
    icon: '▲',
    panels: [
      { id: 'risk_decisions', title: 'Risk Decisions', type: 'graph', metric: 'sentinelx_risk_decisions_total', description: 'Risk decisions by level and action' },
      { id: 'risk_distribution', title: 'Risk Level Distribution', type: 'gauge', metric: 'sentinelx_risk_decisions_total', description: 'Current risk level distribution' },
    ],
  },
  {
    id: 'alerts',
    title: 'Alerts',
    description: 'Alert generation and status metrics',
    icon: '⚑',
    panels: [
      { id: 'alerts_total', title: 'Total Alerts', type: 'stat', metric: 'sentinelx_alerts_total', description: 'Total alerts generated' },
      { id: 'alerts_severity', title: 'Alerts by Severity', type: 'graph', metric: 'sentinelx_alerts_total', description: 'Alerts grouped by severity level' },
      { id: 'alerts_rate', title: 'Alert Rate', type: 'graph', metric: 'sentinelx_alerts_total', description: 'Alerts per minute' },
    ],
  },
  {
    id: 'payments',
    title: 'Payments',
    description: 'Payment processing and fraud metrics',
    icon: '⇄',
    panels: [
      { id: 'held_transactions', title: 'Held Transactions', type: 'stat', metric: 'sentinelx_held_transactions_total', description: 'Transactions currently on hold' },
      { id: 'held_rate', title: 'Hold Rate', type: 'gauge', metric: 'sentinelx_held_transactions_total', description: 'Percentage of transactions held' },
    ],
  },
  {
    id: 'api',
    title: 'API',
    description: 'API performance and usage metrics',
    icon: '⌗',
    panels: [
      { id: 'api_latency', title: 'API Latency', type: 'graph', metric: 'sentinelx_http_request_duration_seconds', description: 'API response times' },
      { id: 'api_throughput', title: 'API Throughput', type: 'graph', metric: 'sentinelx_api_requests_total', description: 'Requests per second' },
      { id: 'api_errors', title: 'API Error Rate', type: 'gauge', metric: 'sentinelx_api_errors_total', description: 'API error percentage' },
    ],
  },
  {
    id: 'simulation',
    title: 'Simulation Performance',
    description: 'Simulation execution and event metrics',
    icon: '▶',
    panels: [
      { id: 'sim_active', title: 'Active Simulations', type: 'stat', metric: 'sentinelx_simulation_active', description: 'Currently running simulations' },
      { id: 'sim_events', title: 'Simulation Events/sec', type: 'graph', metric: 'sentinelx_simulation_events_total', description: 'Events generated per simulation' },
      { id: 'sim_duration', title: 'Simulation Duration', type: 'graph', metric: 'sentinelx_simulation_duration_seconds', description: 'Simulation execution time' },
      { id: 'sim_errors', title: 'Simulation Errors', type: 'stat', metric: 'sentinelx_simulation_errors_total', description: 'Simulation execution errors' },
    ],
  },
];

export function GrafanaDashboards() {
  const [selectedDashboard, setSelectedDashboard] = useState<string>('platform');
  const dashboard = DASHBOARDS.find((d) => d.id === selectedDashboard) ?? DASHBOARDS[0];

  return (
    <div className="page">
      <div className="metrics-header">
        <h2 className="metrics-title">Grafana Dashboards</h2>
        <p className="metrics-subtitle">Prometheus metrics visualization for all platform components</p>
      </div>

      <div className="dashboard-tabs">
        {DASHBOARDS.map((d) => (
          <button
            key={d.id}
            type="button"
            className={`dashboard-tab ${selectedDashboard === d.id ? 'active' : ''}`}
            onClick={() => setSelectedDashboard(d.id)}
          >
            <span className="dashboard-tab-icon">{d.icon}</span>
            <span>{d.title}</span>
          </button>
        ))}
      </div>

      <Card title={dashboard.title}>
        <p className="field-hint">{dashboard.description}</p>
        <div className="dashboard-panels">
          {dashboard.panels.map((panel) => (
            <div key={panel.id} className={`dashboard-panel panel-${panel.type}`}>
              <div className="panel-header">
                <h4 className="panel-title">{panel.title}</h4>
                <span className="panel-type">{panel.type}</span>
              </div>
              <div className="panel-metric mono">{panel.metric}</div>
              <p className="panel-description">{panel.description}</p>
              <div className="panel-placeholder">
                <span className="placeholder-icon">{panel.type === 'graph' ? '📈' : panel.type === 'stat' ? '🔢' : panel.type === 'gauge' ? '🎚️' : '📋'}</span>
                <span>Grafana panel</span>
              </div>
            </div>
          ))}
        </div>
      </Card>

      <Card title="Prometheus Configuration">
        <div className="prometheus-config">
          <h4>Scrape Configuration</h4>
          <pre className="config-code">
{`scrape_configs:
  - job_name: 'sentinelx'
    scrape_interval: 15s
    metrics_path: /metrics
    static_configs:
      - targets: ['localhost:8080']`}
          </pre>
          <h4>Available Metrics</h4>
          <div className="metrics-list">
            {DASHBOARDS.flatMap((d) => d.panels).map((p) => (
              <span key={p.metric} className="metric-tag mono">{p.metric}</span>
            ))}
          </div>
        </div>
      </Card>
    </div>
  );
}

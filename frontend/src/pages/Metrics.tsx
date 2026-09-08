import { useEffect, useState, useMemo } from 'react';
import { metrics, metricsStore, METRIC_NAMES } from '../lib/metrics';
import { getOrCreateCorrelationId } from '../lib/correlation';
import { Card, StatCard } from '../components/ui/Card';
import { LineChart } from '../components/charts/LineChart';
import { DonutChart } from '../components/charts/DonutChart';
import { donutColors } from '../lib/format';

interface MetricDisplay {
  name: string;
  value: number;
  labels?: Record<string, string>;
}

export function Metrics() {
  const [, setTick] = useState(0);
  const correlationId = useMemo(() => getOrCreateCorrelationId(), []);

  // Subscribe to metric updates
  useEffect(() => {
    const unsubscribe = metricsStore.subscribe(() => setTick((t) => t + 1));
    return unsubscribe;
  }, []);

  // Simulate metric changes for demo
  useEffect(() => {
    const interval = setInterval(() => {
      // Simulate HTTP requests
      metrics.trackHttpRequest('GET', '/api/simulations', 200, Math.random() * 100 + 20);
      metrics.trackHttpRequest('POST', '/api/simulations', 201, Math.random() * 200 + 50);
      if (Math.random() > 0.9) {
        metrics.trackHttpRequest('GET', '/api/simulations', 500, Math.random() * 500 + 100);
      }

      // Simulate Kafka messages
      metrics.trackKafkaMessage('security-events', 'produced');
      metrics.trackKafkaMessage('security-events', 'consumed');
      metrics.trackKafkaMessage('risk-decisions', 'produced');

      // Simulate security events
      if (Math.random() > 0.7) {
        metrics.trackSecurityEvent('LOGIN_ATTEMPT', Math.random() > 0.5 ? 'HIGH' : 'MEDIUM');
      }
      if (Math.random() > 0.8) {
        metrics.trackDetection('Brute Force Detection', 'HIGH');
        metrics.trackRiskDecision('HIGH', 'BLOCK');
        metrics.trackBlockedUser();
      }
      if (Math.random() > 0.85) {
        metrics.trackAlert(Math.random() > 0.5 ? 'CRITICAL' : 'HIGH');
      }
      if (Math.random() > 0.9) {
        metrics.trackHeldTransaction();
      }

      // Simulate API requests
      metrics.trackApiRequest('/api/simulations', 'GET', 200);
      metrics.trackApiRequest('/api/payments', 'POST', 201);
    }, 2000);

    return () => clearInterval(interval);
  }, []);

  const httpRequests = metricsStore.getMetricValue(METRIC_NAMES.HTTP_REQUESTS_TOTAL);
  const httpErrors = metricsStore.getMetricValue(METRIC_NAMES.HTTP_ERRORS_TOTAL);
  const kafkaMessages = metricsStore.getMetricValue(METRIC_NAMES.KAFKA_MESSAGES_TOTAL);
  const securityEvents = metricsStore.getMetricValue(METRIC_NAMES.SECURITY_EVENTS_TOTAL);
  const detections = metricsStore.getMetricValue(METRIC_NAMES.DETECTIONS_TOTAL);
  const riskDecisions = metricsStore.getMetricValue(METRIC_NAMES.RISK_DECISIONS_TOTAL);
  const alerts = metricsStore.getMetricValue(METRIC_NAMES.ALERTS_TOTAL);
  const blockedUsers = metricsStore.getMetricValue(METRIC_NAMES.BLOCKED_USERS_TOTAL);
  const heldTransactions = metricsStore.getMetricValue(METRIC_NAMES.HELD_TRANSACTIONS_TOTAL);
  const apiRequests = metricsStore.getMetricValue(METRIC_NAMES.API_REQUESTS_TOTAL);
  const apiErrors = metricsStore.getMetricValue(METRIC_NAMES.API_ERRORS_TOTAL);

  const httpMetric = metricsStore.getMetric(METRIC_NAMES.HTTP_REQUEST_DURATION);
  const latencyValues = httpMetric?.values.slice(-20).map((v) => v.value) ?? [];

  const errorRate = httpRequests > 0 ? ((httpErrors / httpRequests) * 100).toFixed(1) : '0';

  const getMetricHistory = (name: string): number[] => {
    const metric = metricsStore.getMetric(name);
    if (!metric) return [];
    return metric.values.slice(-20).map((v) => v.value);
  };

  return (
    <div className="page">
      <div className="metrics-header">
        <h2 className="metrics-title">Prometheus Metrics</h2>
        <p className="metrics-subtitle">
          Real-time system metrics · Correlation ID: <span className="mono">{correlationId}</span>
        </p>
      </div>

      <div className="stat-grid">
        <StatCard label="HTTP Requests" value={httpRequests.toLocaleString()} tone="info" icon="◈" />
        <StatCard label="HTTP Errors" value={httpErrors.toLocaleString()} tone={httpErrors > 0 ? 'bad' : 'good'} icon="✕" />
        <StatCard label="Error Rate" value={`${errorRate}%`} tone={Number(errorRate) > 5 ? 'bad' : 'good'} icon="▲" />
        <StatCard label="Kafka Messages" value={kafkaMessages.toLocaleString()} tone="violet" icon="⚡" />
      </div>

      <div className="stat-grid">
        <StatCard label="Security Events" value={securityEvents.toLocaleString()} tone="warn" icon="🛡" />
        <StatCard label="Detections" value={detections.toLocaleString()} tone={detections > 0 ? 'critical' : 'neutral'} icon="◉" />
        <StatCard label="Risk Decisions" value={riskDecisions.toLocaleString()} tone="violet" icon="▲" />
        <StatCard label="Alerts" value={alerts.toLocaleString()} tone={alerts > 0 ? 'bad' : 'neutral'} icon="⚑" />
      </div>

      <div className="stat-grid">
        <StatCard label="Blocked Users" value={blockedUsers.toLocaleString()} tone={blockedUsers > 0 ? 'bad' : 'good'} icon="✕" />
        <StatCard label="Held Transactions" value={heldTransactions.toLocaleString()} tone={heldTransactions > 0 ? 'warn' : 'good'} icon="⇄" />
        <StatCard label="API Requests" value={apiRequests.toLocaleString()} tone="info" icon="⌗" />
        <StatCard label="API Errors" value={apiErrors.toLocaleString()} tone={apiErrors > 0 ? 'bad' : 'good'} icon="✕" />
      </div>

      <div className="grid-2">
        <Card title="Request Latency (ms)">
          {latencyValues.length > 0 ? (
            <LineChart series={[{ name: 'Latency', values: latencyValues, color: '#22d3ee' }]} height={200} />
          ) : (
            <p className="field-hint">No latency data yet</p>
          )}
        </Card>
        <Card title="Events by Type">
          <DonutChart
            data={[
              { label: 'Security', value: securityEvents, color: donutColors[0] },
              { label: 'Detections', value: detections, color: donutColors[1] },
              { label: 'Alerts', value: alerts, color: donutColors[2] },
              { label: 'Risk', value: riskDecisions, color: donutColors[3] },
            ].filter((d) => d.value > 0)}
            centerValue={securityEvents + detections + alerts + riskDecisions}
            centerLabel="total"
          />
        </Card>
      </div>

      <Card title="Metrics Export (Prometheus Format)">
        <pre className="prometheus-output">{metricsStore.toPrometheus()}</pre>
      </Card>
    </div>
  );
}

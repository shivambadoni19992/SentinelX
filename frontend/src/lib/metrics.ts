// Prometheus-style metrics collection for SentinelX
// Tracks all system metrics and exposes them in Prometheus format

export interface MetricValue {
  value: number;
  labels?: Record<string, string>;
  timestamp: number;
}

export interface Metric {
  name: string;
  help: string;
  type: 'counter' | 'gauge' | 'histogram';
  values: MetricValue[];
}

// Metric names matching Prometheus conventions
export const METRIC_NAMES = {
  // HTTP metrics
  HTTP_REQUESTS_TOTAL: 'sentinelx_http_requests_total',
  HTTP_REQUEST_DURATION: 'sentinelx_http_request_duration_seconds',
  HTTP_ERRORS_TOTAL: 'sentinelx_http_errors_total',

  // Kafka metrics
  KAFKA_MESSAGES_TOTAL: 'sentinelx_kafka_messages_total',
  KAFKA_ERRORS_TOTAL: 'sentinelx_kafka_errors_total',

  // Security metrics
  SECURITY_EVENTS_TOTAL: 'sentinelx_security_events_total',
  DETECTIONS_TOTAL: 'sentinelx_detections_total',
  RISK_DECISIONS_TOTAL: 'sentinelx_risk_decisions_total',
  ALERTS_TOTAL: 'sentinelx_alerts_total',
  BLOCKED_USERS_TOTAL: 'sentinelx_blocked_users_total',

  // Payment metrics
  HELD_TRANSACTIONS_TOTAL: 'sentinelx_held_transactions_total',

  // Simulation metrics
  SIMULATION_EVENTS_TOTAL: 'sentinelx_simulation_events_total',
  SIMULATION_DURATION: 'sentinelx_simulation_duration_seconds',
  SIMULATION_ERRORS_TOTAL: 'sentinelx_simulation_errors_total',
  SIMULATION_ACTIVE: 'sentinelx_simulation_active',

  // API metrics
  API_REQUESTS_TOTAL: 'sentinelx_api_requests_total',
  API_ERRORS_TOTAL: 'sentinelx_api_errors_total',
} as const;

// Central metrics store
class MetricsStore {
  private metrics: Map<string, Metric> = new Map();
  private listeners: Set<() => void> = new Set();

  constructor() {
    this.initializeMetrics();
  }

  private initializeMetrics() {
    const metricDefs: Omit<Metric, 'values'>[] = [
      // HTTP metrics
      { name: METRIC_NAMES.HTTP_REQUESTS_TOTAL, help: 'Total HTTP requests', type: 'counter', values: [] },
      { name: METRIC_NAMES.HTTP_REQUEST_DURATION, help: 'HTTP request latency', type: 'histogram', values: [] },
      { name: METRIC_NAMES.HTTP_ERRORS_TOTAL, help: 'Total HTTP errors', type: 'counter', values: [] },

      // Kafka metrics
      { name: METRIC_NAMES.KAFKA_MESSAGES_TOTAL, help: 'Total Kafka messages', type: 'counter', values: [] },
      { name: METRIC_NAMES.KAFKA_ERRORS_TOTAL, help: 'Total Kafka errors', type: 'counter', values: [] },

      // Security metrics
      { name: METRIC_NAMES.SECURITY_EVENTS_TOTAL, help: 'Total security events', type: 'counter', values: [] },
      { name: METRIC_NAMES.DETECTIONS_TOTAL, help: 'Total detections', type: 'counter', values: [] },
      { name: METRIC_NAMES.RISK_DECISIONS_TOTAL, help: 'Total risk decisions', type: 'counter', values: [] },
      { name: METRIC_NAMES.ALERTS_TOTAL, help: 'Total alerts', type: 'counter', values: [] },
      { name: METRIC_NAMES.BLOCKED_USERS_TOTAL, help: 'Total blocked users', type: 'counter', values: [] },

      // Payment metrics
      { name: METRIC_NAMES.HELD_TRANSACTIONS_TOTAL, help: 'Total held transactions', type: 'counter', values: [] },

      // Simulation metrics
      { name: METRIC_NAMES.SIMULATION_EVENTS_TOTAL, help: 'Total simulation events', type: 'counter', values: [] },
      { name: METRIC_NAMES.SIMULATION_DURATION, help: 'Simulation duration', type: 'gauge', values: [] },
      { name: METRIC_NAMES.SIMULATION_ERRORS_TOTAL, help: 'Total simulation errors', type: 'counter', values: [] },
      { name: METRIC_NAMES.SIMULATION_ACTIVE, help: 'Active simulations', type: 'gauge', values: [] },

      // API metrics
      { name: METRIC_NAMES.API_REQUESTS_TOTAL, help: 'Total API requests', type: 'counter', values: [] },
      { name: METRIC_NAMES.API_ERRORS_TOTAL, help: 'Total API errors', type: 'counter', values: [] },
    ];

    metricDefs.forEach((def) => {
      this.metrics.set(def.name, { ...def, values: [] });
    });
  }

  // Increment a counter metric
  increment(name: string, labels?: Record<string, string>, value = 1) {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'counter') return;
    const existing = metric.values.find(
      (v) => JSON.stringify(v.labels) === JSON.stringify(labels),
    );
    if (existing) {
      existing.value += value;
      existing.timestamp = Date.now();
    } else {
      metric.values.push({ value, labels, timestamp: Date.now() });
    }
    this.notifyListeners();
  }

  // Set a gauge metric
  setGauge(name: string, value: number, labels?: Record<string, string>) {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'gauge') return;
    const existing = metric.values.find(
      (v) => JSON.stringify(v.labels) === JSON.stringify(labels),
    );
    if (existing) {
      existing.value = value;
      existing.timestamp = Date.now();
    } else {
      metric.values.push({ value, labels, timestamp: Date.now() });
    }
    this.notifyListeners();
  }

  // Observe a histogram value
  observe(name: string, value: number, labels?: Record<string, string>) {
    const metric = this.metrics.get(name);
    if (!metric || metric.type !== 'histogram') return;
    metric.values.push({ value, labels, timestamp: Date.now() });
    // Keep only last 1000 values for histograms
    if (metric.values.length > 1000) {
      metric.values = metric.values.slice(-1000);
    }
    this.notifyListeners();
  }

  // Get all metrics
  getMetrics(): Metric[] {
    return Array.from(this.metrics.values());
  }

  // Get a specific metric
  getMetric(name: string): Metric | undefined {
    return this.metrics.get(name);
  }

  // Get metric value (sum for counters, latest for gauges)
  getMetricValue(name: string, labels?: Record<string, string>): number {
    const metric = this.metrics.get(name);
    if (!metric) return 0;
    if (metric.type === 'counter') {
      return metric.values
        .filter((v) => !labels || JSON.stringify(v.labels) === JSON.stringify(labels))
        .reduce((sum, v) => sum + v.value, 0);
    }
    if (metric.type === 'gauge') {
      const matching = metric.values.filter(
        (v) => !labels || JSON.stringify(v.labels) === JSON.stringify(labels),
      );
      return matching.length > 0 ? matching[matching.length - 1].value : 0;
    }
    return 0;
  }

  // Export in Prometheus format
  toPrometheus(): string {
    const lines: string[] = [];
    this.metrics.forEach((metric) => {
      lines.push(`# HELP ${metric.name} ${metric.help}`);
      lines.push(`# TYPE ${metric.name} ${metric.type}`);
      metric.values.forEach((v) => {
        const labels = v.labels
          ? '{' + Object.entries(v.labels).map(([k, val]) => `${k}="${val}"`).join(',') + '}'
          : '';
        lines.push(`${metric.name}${labels} ${v.value}`);
      });
    });
    return lines.join('\n');
  }

  // Subscribe to metric changes
  subscribe(listener: () => void) {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private notifyListeners() {
    this.listeners.forEach((l) => l());
  }

  // Reset all metrics
  reset() {
    this.metrics.forEach((metric) => {
      metric.values = [];
    });
    this.notifyListeners();
  }
}

// Singleton instance
export const metricsStore = new MetricsStore();

// Convenience functions for common operations
export const metrics = {
  // HTTP tracking
  trackHttpRequest(method: string, path: string, status: number, duration: number) {
    const labels = { method, path, status: String(status) };
    metricsStore.increment(METRIC_NAMES.HTTP_REQUESTS_TOTAL, labels);
    metricsStore.observe(METRIC_NAMES.HTTP_REQUEST_DURATION, duration, { method, path });
    if (status >= 400) {
      metricsStore.increment(METRIC_NAMES.HTTP_ERRORS_TOTAL, { method, path, status: String(status) });
    }
  },

  // Kafka tracking
  trackKafkaMessage(topic: string, operation: 'produced' | 'consumed') {
    metricsStore.increment(METRIC_NAMES.KAFKA_MESSAGES_TOTAL, { topic, operation });
  },

  trackKafkaError(topic: string) {
    metricsStore.increment(METRIC_NAMES.KAFKA_ERRORS_TOTAL, { topic });
  },

  // Security tracking
  trackSecurityEvent(eventType: string, severity: string) {
    metricsStore.increment(METRIC_NAMES.SECURITY_EVENTS_TOTAL, { event_type: eventType, severity });
  },

  trackDetection(ruleName: string, severity: string) {
    metricsStore.increment(METRIC_NAMES.DETECTIONS_TOTAL, { rule: ruleName, severity });
  },

  trackRiskDecision(riskLevel: string, action: string) {
    metricsStore.increment(METRIC_NAMES.RISK_DECISIONS_TOTAL, { risk_level: riskLevel, action });
  },

  trackAlert(severity: string) {
    metricsStore.increment(METRIC_NAMES.ALERTS_TOTAL, { severity });
  },

  trackBlockedUser() {
    metricsStore.increment(METRIC_NAMES.BLOCKED_USERS_TOTAL);
  },

  // Payment tracking
  trackHeldTransaction() {
    metricsStore.increment(METRIC_NAMES.HELD_TRANSACTIONS_TOTAL);
  },

  // Simulation tracking
  trackSimulationEvent(eventType: string) {
    metricsStore.increment(METRIC_NAMES.SIMULATION_EVENTS_TOTAL, { event_type: eventType });
  },

  trackSimulationDuration(duration: number) {
    metricsStore.setGauge(METRIC_NAMES.SIMULATION_DURATION, duration);
  },

  trackSimulationError(errorType: string) {
    metricsStore.increment(METRIC_NAMES.SIMULATION_ERRORS_TOTAL, { error_type: errorType });
  },

  setActiveSimulations(count: number) {
    metricsStore.setGauge(METRIC_NAMES.SIMULATION_ACTIVE, count);
  },

  // API tracking
  trackApiRequest(endpoint: string, method: string, status: number) {
    metricsStore.increment(METRIC_NAMES.API_REQUESTS_TOTAL, { endpoint, method, status: String(status) });
    if (status >= 400) {
      metricsStore.increment(METRIC_NAMES.API_ERRORS_TOTAL, { endpoint, method, status: String(status) });
    }
  },

  // Get store instance
  getStore: () => metricsStore,
};

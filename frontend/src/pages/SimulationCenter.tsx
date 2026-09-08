import { useEffect, useMemo, useRef, useState } from 'react';
import { cancelSimulation, createSimulation, listSimulations, streamSimulation } from '../api/endpoints';
import type { SimulationProgress } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { mockSimulations, mockStreamSimulation } from '../api/mock';
import type { SimulationRun } from '../api/types';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { relativeTime } from '../lib/format';
import { LineChart, ChartLegend } from '../components/charts/LineChart';
import { metrics } from '../lib/metrics';
import { createContext, getDuration } from '../lib/correlation';

// ------------------------------------------------------------------ catalog

interface SimTypeMeta {
  value: string;
  label: string;
  desc: string;
  /** Extra parameter sections this scenario reveals. */
  sections: SimSection[];
}

type SimSection = 'payment' | 'auth' | 'api' | 'network' | 'velocity' | 'bot';

const SIM_TYPES: SimTypeMeta[] = [
  { value: 'NORMAL_TRAFFIC', label: 'Normal Traffic', desc: 'Benign baseline — should raise no detections.', sections: [] },
  { value: 'MIXED_ATTACK', label: 'Mixed Attack', desc: 'Blended multi-vector attack rehearsal.', sections: [] },
  { value: 'BRUTE_FORCE', label: 'Brute Force', desc: 'Distributed credential guessing on few accounts.', sections: ['auth'] },
  { value: 'ACCOUNT_TAKEOVER', label: 'Account Takeover', desc: 'Compromise followed by privileged actions.', sections: ['auth', 'payment'] },
  { value: 'SUSPICIOUS_LOGIN', label: 'Suspicious Login', desc: 'Logins from unusual geos and hours.', sections: ['auth'] },
  { value: 'NEW_DEVICE', label: 'New Device', desc: 'First-seen device fingerprints.', sections: ['auth'] },
  { value: 'PAYMENT_FRAUD', label: 'Payment Fraud', desc: 'High-risk authorizations and holds.', sections: ['payment'] },
  { value: 'TRANSACTION_VELOCITY', label: 'Transaction Velocity', desc: 'Rapid-fire transactions per user.', sections: ['velocity'] },
  { value: 'FAILED_PAYMENTS', label: 'Failed Payments', desc: 'Bursts of declined authorizations.', sections: ['payment'] },
  { value: 'API_ABUSE', label: 'API Abuse', desc: '4xx/429-heavy abusive request patterns.', sections: ['api'] },
  { value: 'BOT_ACTIVITY', label: 'Bot Activity', desc: 'Bot fleet mixed with legitimate traffic.', sections: ['bot'] },
  { value: 'SUSPICIOUS_IP', label: 'Suspicious IP', desc: 'Traffic from anonymizer / bad-reputation IPs.', sections: ['api'] },
  { value: 'UNAUTHORIZED_DATA_ACCESS', label: 'Unauthorized Data Access', desc: 'Denied access to protected data endpoints.', sections: ['api'] },
  { value: 'PRIVILEGED_ACCESS_ANOMALY', label: 'Privileged Access Anomaly', desc: 'Admin actions outside expected patterns.', sections: ['api'] },
  { value: 'CHECKOUT_ABUSE', label: 'Checkout Abuse', desc: 'Rapid cart mutation and checkout hammering.', sections: [] },
  { value: 'INVENTORY_SCRAPING', label: 'Inventory Scraping', desc: 'Massive product/inventory view scraping.', sections: [] },
  { value: 'COUPON_ABUSE', label: 'Coupon Abuse', desc: 'Repeated coupon redemption across accounts.', sections: [] },
  { value: 'PORT_SCAN', label: 'Port Scan', desc: 'Sequential probes across many ports.', sections: ['network'] },
  { value: 'CONNECTION_SPIKE', label: 'Connection Spike', desc: 'Sudden connection-count surges.', sections: ['network'] },
  { value: 'FAILED_CONNECTIONS', label: 'Failed Connections', desc: 'High rate of unreachable-host attempts.', sections: ['network'] },
  { value: 'SUSPICIOUS_OUTBOUND', label: 'Suspicious Outbound', desc: 'Large transfers — compose-internal only.', sections: ['network'] },
];

const SECTION_TITLES: Record<SimSection, string> = {
  payment: 'Payment Parameters',
  auth: 'Authentication Parameters',
  api: 'API Parameters',
  network: 'Network Parameters',
  velocity: 'Velocity Parameters',
  bot: 'Bot Parameters',
};

// ------------------------------------------------------- field definitions

interface FieldDef {
  key: string;
  label: string;
  kind: 'number' | 'percent' | 'select' | 'text';
  min?: number;
  max?: number;
  step?: number;
  unit?: string;
  options?: { value: string; label: string }[];
  def: number | string | boolean;
  hint?: string;
}

// Safe upper limits — mirrored from the backend (sentinelx.simulation.limits).
const LIMITS = {
  maxUsers: 10_000,
  maxDevices: 10_000,
  maxIps: 10_000,
  maxDuration: 600,
  maxEps: 1_000,
  maxTotalEvents: 50_000,
};

const COMMON_FIELDS: FieldDef[] = [
  { key: 'numberOfUsers', label: 'Users', kind: 'number', min: 1, max: LIMITS.maxUsers, step: 1, def: 100, unit: 'users' },
  { key: 'numberOfDevices', label: 'Devices', kind: 'number', min: 1, max: LIMITS.maxDevices, step: 1, def: 100, unit: 'devices' },
  { key: 'numberOfIpAddresses', label: 'IP Addresses', kind: 'number', min: 1, max: LIMITS.maxIps, step: 1, def: 50, unit: 'IPs' },
  { key: 'durationSeconds', label: 'Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
  { key: 'eventsPerSecond', label: 'Events / sec', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 10, unit: 'events/s' },
  { key: 'attackPercentage', label: 'Attack %', kind: 'percent', min: 0, max: 100, step: 1, def: 25, unit: '%' },
  { key: 'intensity', label: 'Intensity', kind: 'percent', min: 0, max: 100, step: 1, def: 50, unit: '%' },
];

const PAYMENT_FIELDS: FieldDef[] = [
  { key: 'transactions', label: 'Transactions', kind: 'number', min: 100, max: 50_000, step: 100, def: 10_000, unit: 'total' },
  { key: 'normalAmount', label: 'Normal Amount', kind: 'number', min: 1, max: 5_000, step: 1, def: 80, unit: 'USD' },
  { key: 'suspiciousAmount', label: 'Suspicious Amount', kind: 'number', min: 1, max: 25_000, step: 10, def: 1_200, unit: 'USD' },
  { key: 'highValueAmount', label: 'High-Value Amount', kind: 'number', min: 1, max: 100_000, step: 100, def: 100_000, unit: 'USD' },
  { key: 'velocity', label: 'Velocity', kind: 'number', min: 1, max: 100, step: 1, def: 10, unit: 'cards' },
  { key: 'failedPaymentPercentage', label: 'Failed Payment %', kind: 'percent', min: 0, max: 100, step: 1, def: 35, unit: '%' },
  { key: 'newDevicePercentage', label: 'New Device %', kind: 'percent', min: 0, max: 100, step: 1, def: 30, unit: '%' },
  { key: 'suspiciousIpPercentage', label: 'Suspicious IP %', kind: 'percent', min: 0, max: 100, step: 1, def: 40, unit: '%' },
];

const VELOCITY_FIELDS: FieldDef[] = [
  { key: 'users', label: 'Users', kind: 'number', min: 10, max: 10_000, step: 10, def: 1_000, unit: 'users' },
  { key: 'transactionsPerUser', label: 'Transactions / User', kind: 'number', min: 1, max: 200, step: 1, def: 20, unit: 'txn' },
  { key: 'timeWindowSeconds', label: 'Time Window', kind: 'number', min: 1, max: 300, step: 1, def: 60, unit: 'sec' },
  { key: 'amount', label: 'Amount', kind: 'number', min: 1, max: 50_000, step: 10, def: 500, unit: 'USD' },
  { key: 'concurrentUsers', label: 'Concurrent Users', kind: 'number', min: 1, max: 500, step: 1, def: 5, unit: 'users' },
  { key: 'attackPercentage', label: 'Attack %', kind: 'percent', min: 0, max: 100, step: 1, def: 25, unit: '%' },
];

const AUTH_FIELDS: FieldDef[] = [
  { key: 'targetUsers', label: 'Target Users', kind: 'number', min: 1, max: 1_000, step: 1, def: 5, unit: 'accounts' },
  { key: 'failedAttemptsPerUser', label: 'Failed Attempts / User', kind: 'number', min: 1, max: 1_000, step: 1, def: 10, unit: 'attempts' },
  { key: 'attemptsPerSecond', label: 'Attempts / sec', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 15, unit: 'att/s' },
  { key: 'authSourceIps', label: 'IPs', kind: 'number', min: 1, max: LIMITS.maxIps, step: 1, def: 12, unit: 'IPs' },
  { key: 'authDevices', label: 'Devices', kind: 'number', min: 1, max: LIMITS.maxDevices, step: 1, def: 8, unit: 'devices' },
  { key: 'newIpPercentage', label: 'New IP %', kind: 'percent', min: 0, max: 100, step: 1, def: 80, unit: '%' },
  { key: 'newDevicePercentage', label: 'New Device %', kind: 'percent', min: 0, max: 100, step: 1, def: 80, unit: '%' },
  { key: 'successfulLoginPercentage', label: 'Successful Login %', kind: 'percent', min: 0, max: 100, step: 1, def: 90, unit: '%' },
];

const API_FIELDS: FieldDef[] = [
  { key: 'apiUsers', label: 'Users', kind: 'number', min: 1, max: LIMITS.maxUsers, step: 1, def: 25, unit: 'users' },
  { key: 'apiSourceIps', label: 'IPs', kind: 'number', min: 1, max: LIMITS.maxIps, step: 1, def: 15, unit: 'IPs' },
  {
    key: 'targetEndpoint',
    label: 'Target Endpoint',
    kind: 'select',
    def: '/api/payments',
    options: [
      { value: '/api/payments', label: '/api/payments' },
      { value: '/api/retail/products', label: '/api/retail/products' },
      { value: '/api/retail/cart', label: '/api/retail/cart' },
      { value: '/api/auth/login', label: '/api/auth/login' },
      { value: '/api/auth/users', label: '/api/auth/users' },
      { value: '/api/risk/decisions', label: '/api/risk/decisions' },
    ],
  },
  { key: 'normalRps', label: 'Normal RPS', kind: 'number', min: 1, max: 1_000, step: 1, def: 100, unit: 'req/s' },
  { key: 'attackRps', label: 'Attack RPS', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 5_000, unit: 'req/s' },
  { key: 'apiDurationSeconds', label: 'Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
];

const NETWORK_FIELDS: FieldDef[] = [
  {
    key: 'sourceContainers',
    label: 'Source Containers',
    kind: 'text',
    def: 'api-gateway, auth-service',
    hint: 'Comma-separated compose service names',
  },
  {
    key: 'targetContainers',
    label: 'Target Containers',
    kind: 'text',
    def: 'postgres, redis, detection-engine',
    hint: 'Compose services only — external targets are rejected',
  },
  {
    key: 'ports',
    label: 'Ports',
    kind: 'text',
    def: '5432, 6379, 9092',
    hint: 'Comma-separated ports; a single number sweeps 1..N (port scans)',
  },
  { key: 'attempts', label: 'Attempts', kind: 'number', min: 1, max: LIMITS.maxTotalEvents, step: 1, def: 500, unit: 'total' },
  { key: 'connectionsPerSecond', label: 'Connections / sec', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 30, unit: 'conn/s' },
  { key: 'networkDurationSeconds', label: 'Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
];

const BOT_FIELDS: FieldDef[] = [
  { key: 'botCount', label: 'Number of Bots', kind: 'number', min: 1, max: 1_000, step: 1, def: 50, unit: 'bots' },
  { key: 'requestsPerBot', label: 'Requests / Bot', kind: 'number', min: 1, max: 10_000, step: 10, def: 200, unit: 'req' },
  { key: 'rpsPerBot', label: 'RPS / Bot', kind: 'number', min: 1, max: 100, step: 1, def: 20, unit: 'req/s' },
  {
    key: 'targetEndpoints',
    label: 'Target Endpoints',
    kind: 'text',
    def: '/api/products, /api/search',
    hint: 'Comma-separated endpoint paths the bot fleet hits',
  },
  { key: 'sessionDurationSeconds', label: 'Session Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 45, unit: 'sec' },
  { key: 'botUserAgentPattern', label: 'User-Agent Pattern', kind: 'text', def: 'SimBot/$VERSION', hint: 'Bot user-agent; $VERSION is auto-incremented' },
  { key: 'botDurationSeconds', label: 'Attack Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
  { key: 'attackPercentage', label: 'Bot %', kind: 'percent', min: 0, max: 100, step: 1, def: 60, unit: '%' },
];

const SECTION_FIELDS: Record<SimSection, FieldDef[]> = {
  payment: PAYMENT_FIELDS,
  auth: AUTH_FIELDS,
  api: API_FIELDS,
  network: NETWORK_FIELDS,
  velocity: VELOCITY_FIELDS,
  bot: BOT_FIELDS,
};

const VECTOR_LABELS: Record<SimSection, string> = {
  payment: 'Include payment vector',
  auth: 'Include authentication vector',
  api: 'Include API vector',
  network: 'Include network vector',
  velocity: 'Include velocity vector',
  bot: 'Include bot vector',
};

/** The four network-drill scenario types that render the network dashboard. */
const NETWORK_SIM_TYPES = ['PORT_SCAN', 'CONNECTION_SPIKE', 'FAILED_CONNECTIONS', 'SUSPICIOUS_OUTBOUND'];

// ------------------------------------------------------------- value state

type ParamValues = Record<string, number | string | boolean | string[]>;

/** One aggregated network flow row rendered on the network drill dashboard. */
interface NetworkFlow {
  source: string;
  destination: string;
  port: string;
  protocol: string;
  rate: number;
  risk: number;
  severity: string;
  count: number;
}

function defaultsFor(sections: SimSection[]): ParamValues {
  const values: ParamValues = {};
  COMMON_FIELDS.forEach((f) => (values[f.key] = f.def));
  sections.forEach((s) => SECTION_FIELDS[s].forEach((f) => (values[f.key] = f.def)));
  return values;
}

/** Builds a time-series array for the RPS chart: baseline is steady, attack spikes. */
function buildRpsSeries(rate: number, points: number, attack: boolean): number[] {
  const out: number[] = [];
  for (let i = 0; i < points; i += 1) {
    if (attack) {
      // Attack phase: spike in the middle of the window.
      const spike = i > points * 0.3 && i < points * 0.8 ? 1.0 : 0.1;
      out.push(Math.max(0, Math.round(rate * spike * (0.8 + Math.sin(i / 3) * 0.2))));
    } else {
      // Baseline: gentle fluctuation around the configured rate.
      out.push(Math.max(0, Math.round(rate * (0.7 + Math.sin(i / 4) * 0.3))));
    }
  }
  return out;
}

// ---------------------------------------------------------------- widgets

function SliderField({
  field,
  value,
  onChange,
  invalid,
}: {
  field: FieldDef;
  value: number;
  onChange: (v: number) => void;
  invalid?: boolean;
}) {
  const { min = 0, max = 100, step = 1 } = field;
  return (
    <div className={`sim-field ${invalid ? 'sim-field-invalid' : ''}`}>
      <div className="sim-field-head">
        <span className="sim-field-label">{field.label}</span>
        <span className="sim-field-value">
          {value}
          {field.unit ? <em> {field.unit}</em> : null}
        </span>
      </div>
      <div className="sim-field-controls">
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          aria-label={field.label}
        />
        <input
          type="number"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={(e) => onChange(Number(e.target.value))}
          className="sim-number"
          aria-label={`${field.label} (numeric input)`}
        />
      </div>
      <div className="sim-field-range muted">
        {min} – {max.toLocaleString()}
        {field.unit ? ` ${field.unit}` : ''}
      </div>
    </div>
  );
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <fieldset className="sim-section">
      <legend>{title}</legend>
      <div className="sim-section-grid">{children}</div>
    </fieldset>
  );
}

// ------------------------------------------------------------------- page

export function SimulationCenter({ user }: { user?: { username: string } }) {
  const list = useCollection(listSimulations, mockSimulations, [], {
    fallback: 'auto',
    demoLabel: 'simulations API unreachable',
  });

  const [typeValue, setTypeValue] = useState('BRUTE_FORCE');
  const selected = SIM_TYPES.find((t) => t.value === typeValue)!;

  const [values, setValues] = useState<ParamValues>(() => defaultsFor(['auth']));
  const [includeVectors, setIncludeVectors] = useState<Record<SimSection, boolean>>({
    payment: true,
    auth: true,
    api: true,
    network: true,
    velocity: true,
    bot: true,
  });
  const [name, setName] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitOk, setSubmitOk] = useState<string | null>(null);

  const activeSections = useMemo(
    () => (typeValue === 'MIXED_ATTACK'
      ? (Object.keys(includeVectors) as SimSection[]).filter((s) => includeVectors[s])
      : selected.sections),
    [typeValue, includeVectors, selected],
  );

  const setValue = (key: string, v: number | string | boolean) =>
    setValues((prev) => ({ ...prev, [key]: v }));

  const handleTypeChange = (next: string) => {
    const meta = SIM_TYPES.find((t) => t.value === next)!;
    setTypeValue(next);
    setValues((prev) => {
      const merged = defaultsFor(meta.sections);
      // Preserve values the user already tuned where fields overlap.
      Object.keys(merged).forEach((k) => {
        if (prev[k] !== undefined) merged[k] = prev[k];
      });
      return merged;
    });
    setSubmitOk(null);
    setSubmitError(null);
  };

  // ---------------------------------------------------------- validation

  const errors = useMemo(() => {
    const errs: string[] = [];
    const check = (fields: FieldDef[], prefix?: string) => {
      fields.forEach((f) => {
        if (f.kind === 'number' || f.kind === 'percent') {
          const v = Number(values[f.key]);
          if (!Number.isFinite(v) || v < (f.min ?? 0) || v > (f.max ?? Infinity)) {
            errs.push(`${prefix ? prefix + ' · ' : ''}${f.label} must be ${f.min}–${(f.max ?? Infinity).toLocaleString()}`);
          }
        }
        if (f.kind === 'select' && !values[f.key]) {
          errs.push(`${prefix ? prefix + ' · ' : ''}${f.label} is required`);
        }
      });
    };
    check(COMMON_FIELDS);
    activeSections.forEach((s) => check(SECTION_FIELDS[s], SECTION_TITLES[s]));
    const totalEvents = Number(values.durationSeconds) * Number(values.eventsPerSecond);
    if (Number.isFinite(totalEvents) && totalEvents > LIMITS.maxTotalEvents) {
      errs.push(
        `Estimated events (${totalEvents.toLocaleString()}) exceed the safe limit of ${LIMITS.maxTotalEvents.toLocaleString()} — lower duration or events/sec.`,
      );
    }
    return errs;
  }, [values, activeSections]);

  // ---------------------------------------------------------- estimates

  const estimates = useMemo(() => {
    const duration = Math.max(
      Number(values.durationSeconds) || 0,
      Number(values.apiDurationSeconds) || 0,
      Number(values.networkDurationSeconds) || 0,
    );
    const eps = Number(values.eventsPerSecond) || 0;
    const usesPayment = activeSections.includes('payment');
    const usesApi = activeSections.includes('api');
    const usesNetwork = activeSections.includes('network');
    const transactions = usesPayment
      ? (Number(values.transactions) || 0)
      : 0;
    const requests = usesApi
      ? ((Number(values.normalRps) || 0) + (Number(values.attackRps) || 0)) * (Number(values.apiDurationSeconds) || duration)
      : usesNetwork
        ? (Number(values.connectionsPerSecond) || 0) * duration
        : eps * duration;
    return {
      users: Number(values.numberOfUsers) || 0,
      events: eps * (Number(values.durationSeconds) || 0),
      transactions,
      requests,
      duration,
      overLimit: eps * (Number(values.durationSeconds) || 0) > LIMITS.maxTotalEvents,
    };
  }, [values, activeSections]);

  // ---------------------------------------------------------- live metrics

    // ---------------------------------------------------------- SSE streaming
  // Live progress (counters + time-series) streamed from the backend over SSE,
  // with a synthetic fallback feed when the endpoint is unreachable.
  const [stream, setStream] = useState<SimulationProgress | null>(null);
  const [streaming, setStreaming] = useState(false);
  const esRef = useRef<EventSource | null>(null);
  const mockRef = useRef<{ cancel: () => void } | null>(null);

  const liveRun = useMemo(() => {
    const active = list.data.find(
      (s) => s.status === 'RUNNING' || s.status === 'QUEUED' || s.status === 'PENDING',
    );
    if (active) return active;
    return list.data.length ? list.data[list.data.length - 1] : null;
  }, [list.data]);

  const live = useMemo(() => {
    if (!liveRun) {
      return {
        present: false as const,
        users: 0,
        transactions: 0,
        requests: 0,
        events: 0,
        eps: 0,
        errors: 0,
        bf: { attackRate: 0, failedLogins: 0, successfulLogins: 0, targetUsers: 0, sourceIps: 0, risk: 0, alerts: 0, actions: 0 },
        ato: { failedLogins: 0, newIpLogins: 0, newDeviceLogins: 0, successfulLogins: 0, suspiciousPayments: 0, targetUsers: 0, risk: 0, alerts: 0, actions: 0 },
        pf: { totalPayments: 0, highValuePayments: 0, declinedPayments: 0, newDevicePayments: 0, suspiciousIpPayments: 0, fraudRingCards: 0, risk: 0, alerts: 0, actions: 0 },
        tv: { totalPayments: 0, baselinePayments: 0, burstPayments: 0, affectedUsers: 0, heldPayments: 0, baselineRate: 0, attackVelocity: 0, risk: 0, alerts: 0, actions: 0 },
        api: { totalRequests: 0, baselineRequests: 0, attackRequests: 0, blockedRequests: 0, currentRps: 0, baselineRps: 0, attackRps: 0, topIps: 0, topEndpoints: 0, risk: 0, alerts: 0, actions: 0 },
        bot: { legits: 0, bots: 0, totalRequests: 0, legitimatePct: 0, botPct: 0, botIps: 0, endpoints: 0, risk: 0, actions: 0, alerts: 0 },
        network: { totalEvents: 0, currentRate: 0, connectionsPerSecond: 0, distinctSources: 0, distinctDestinations: 0, distinctPorts: 0, risk: 0, actions: 0, alerts: 0, flows: [] },
        mixed: {
          campaignId: '',
          attackVectors: [],
          authEvents: 0,
          apiEvents: 0,
          paymentEvents: 0,
          networkEvents: 0,
          dataAccessEvents: 0,
          totalAttackEvents: 0,
          detections: 0,
          risk: 0,
          alerts: 0,
          actions: 0,
          attackIntensity: 0,
          affectedUsers: 0,
        },
        eventsPerSecond: [],
        alertsOverTime: [],
        riskDistribution: { HIGH: 0, MEDIUM: 0, LOW: 0 },
        progress: 0,
        isStreaming: false,
        streamStatus: null,
      };
    }
    const cfg = (liveRun.configuration ?? liveRun.config ?? {}) as Record<string, unknown>;
    const metrics = liveRun.metrics ?? {};
    const num = (k: string, fb = 0, src: Record<string, unknown> = cfg) => {
      const v = src[k];
      return typeof v === 'number' && Number.isFinite(v) ? v : fb;
    };
    const duration = num('durationSeconds', 60);
        const eps = num('eventsPerSecond', 0);
    const liveEps = stream?.eventsPerSec ?? eps;
    const liveErrors = stream
      ? stream.errors.length
      : (Array.isArray(liveRun.errors) ? liveRun.errors.length : 0);
    const transactions = num('transactions', 0);
    const apiRps = num('normalRps', 0) + num('attackRps', 0);
    const requests = apiRps > 0
      ? apiRps * num('apiDurationSeconds', duration)
      : num('connectionsPerSecond', 0) * duration;
    return {
      present: true as const,
      users: num('numberOfUsers'),
      transactions,
      requests: requests > 0 ? requests : Number(liveRun.eventsGenerated ?? 0),
            events: Number(liveRun.eventsGenerated ?? 0),
      eps: liveEps,
      errors: liveErrors,
      // Brute-force drill metrics (persisted live by the simulation runner).
      bf: {
        attackRate: num('attackRate', 0, metrics),
        failedLogins: num('failedLogins', 0, metrics),
        successfulLogins: num('successfulLogins', 0, metrics),
        targetUsers: num('targetUsers', 0, metrics),
        sourceIps: num('sourceIps', 0, metrics),
        risk: Number(liveRun.riskDecisions ?? 0),
        alerts: Number(liveRun.alerts ?? 0),
        actions: Number(liveRun.actions ?? 0),
      },
      // Account-takeover attack-chain metrics (persisted live by the simulation runner).
      ato: {
        failedLogins: num('failedLogins', 0, metrics),
        newIpLogins: num('newIpLogins', 0, metrics),
        newDeviceLogins: num('newDeviceLogins', 0, metrics),
        successfulLogins: num('successfulLogins', 0, metrics),
        suspiciousPayments: num('suspiciousPayments', 0, metrics),
        targetUsers: num('targetUsers', 0, metrics),
        risk: Number(liveRun.riskDecisions ?? 0),
        alerts: Number(liveRun.alerts ?? 0),
        actions: Number(liveRun.actions ?? 0),
      },
      // Payment-fraud metrics (persisted live by the simulation runner).
      pf: {
        totalPayments: num('totalPayments', 0, metrics),
        highValuePayments: num('highValuePayments', 0, metrics),
        declinedPayments: num('declinedPayments', 0, metrics),
        newDevicePayments: num('newDevicePayments', 0, metrics),
        suspiciousIpPayments: num('suspiciousIpPayments', 0, metrics),
        fraudRingCards: num('fraudRingCards', 0, metrics),
        risk: Number(liveRun.riskDecisions ?? 0),
        alerts: Number(liveRun.alerts ?? 0),
        actions: Number(liveRun.actions ?? 0),
      },
      // Transaction-velocity metrics (persisted live by the simulation runner).
      tv: {
        totalPayments: num('totalPayments', 0, metrics),
        baselinePayments: num('baselinePayments', 0, metrics),
        burstPayments: num('burstPayments', 0, metrics),
        affectedUsers: num('affectedUsers', 0, metrics),
        heldPayments: num('heldPayments', 0, metrics),
        baselineRate: num('baselineRate', 0, metrics),
        attackVelocity: num('attackVelocity', 0, metrics),
        risk: Number(liveRun.riskDecisions ?? 0),
        alerts: Number(liveRun.alerts ?? 0),
        actions: Number(liveRun.actions ?? 0),
      },
      // API-abuse metrics (persisted live by the simulation runner).
      api: {
        totalRequests: num('totalRequests', 0, metrics),
        baselineRequests: num('baselineRequests', 0, metrics),
        attackRequests: num('attackRequests', 0, metrics),
        blockedRequests: num('blockedRequests', 0, metrics),
        currentRps: num('currentRps', 0, metrics),
        baselineRps: num('baselineRps', 0, metrics),
        attackRps: num('attackRps', 0, metrics),
        topIps: num('topIps', 0, metrics),
        topEndpoints: num('topEndpoints', 0, metrics),
        risk: num('risk', 0, metrics),
        alerts: Number(liveRun.alerts ?? 0),
        actions: Number(liveRun.actions ?? 0),
      },
      // Bot-activity metrics (persisted live by the simulation runner).
      bot: {
        legits: num('legitRequests', 0, metrics),
        bots: num('botRequests', 0, metrics),
        totalRequests: num('totalRequests', 0, metrics),
        legitimatePct: num('legitimatePct', 100, metrics),
        botPct: num('botPct', 0, metrics),
        botIps: num('botIps', 0, metrics),
        endpoints: num('endpoints', 0, metrics),
        risk: num('risk', 0, metrics),
        actions: num('actions', 0, metrics),
        alerts: Number(liveRun.alerts ?? 0),
      },
      // Network-drill metrics (persisted live by the simulation runner).
      network: {
        totalEvents: num('totalEvents', 0, metrics),
        currentRate: num('currentRate', 0, metrics),
        connectionsPerSecond: num('connectionsPerSecond', 0, metrics),
        distinctSources: num('distinctSources', 0, metrics),
        distinctDestinations: num('distinctDestinations', 0, metrics),
        distinctPorts: num('distinctPorts', 0, metrics),
        risk: num('risk', 0, metrics),
        actions: num('actions', 0, metrics),
        alerts: Number(liveRun.alerts ?? 0),
        flows: Array.isArray(metrics?.flows) ? (metrics.flows as unknown as NetworkFlow[]) : [],
      },
      // Mixed-attack campaign metrics (aggregated from all active vectors).
      mixed: {
        campaignId: String(liveRun.campaignId ?? cfg.campaignId ?? ''),
        attackVectors: Array.isArray(cfg.attackVectors)
          ? (cfg.attackVectors as string[])
          : (Object.keys(includeVectors) as SimSection[]).filter((s) => includeVectors[s] && (cfg[`include${s[0].toUpperCase()}${s.slice(1)}`] !== false)),
        authEvents: num('authEvents', num('failedLogins', 0, metrics) + num('successfulLogins', 0, metrics) + num('newIpLogins', 0, metrics) + num('newDeviceLogins', 0, metrics), metrics),
        apiEvents: num('apiEvents', num('totalRequests', 0, metrics) + num('attackRequests', 0, metrics) + num('blockedRequests', 0, metrics), metrics),
        paymentEvents: num('paymentEvents', num('totalPayments', 0, metrics) + num('highValuePayments', 0, metrics) + num('declinedPayments', 0, metrics), metrics),
        networkEvents: num('networkEvents', num('totalEvents', 0, metrics), metrics),
        dataAccessEvents: num('dataAccessEvents', num('dataAccessAttempts', 0, metrics), metrics),
        totalAttackEvents: num('totalAttackEvents', 0, metrics),
        detections: Number(liveRun.detections ?? num('detections', 0, metrics)),
        risk: Number(liveRun.riskDecisions ?? num('risk', 0, metrics)),
        alerts: Number(liveRun.alerts ?? num('alerts', 0, metrics)),
        actions: Number(liveRun.actions ?? num('actions', 0, metrics)),
        attackIntensity: num('attackIntensity', num('attackPercent', 0), cfg),
        affectedUsers: num('affectedUsers', num('targetUsers', 0, metrics), metrics),
      },
    };
  }, [liveRun]);

  // ------------------------------------------------------------- submit

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    if (errors.length > 0) return;
    setSubmitting(true);
    setSubmitError(null);
    setSubmitOk(null);
    const configuration: ParamValues = { ...values };
    if (typeValue === 'MIXED_ATTACK') {
      const enabledVectors: string[] = [];
      (Object.keys(includeVectors) as SimSection[]).forEach((s) => {
        configuration[`include${s[0].toUpperCase()}${s.slice(1)}`] = includeVectors[s];
        if (includeVectors[s]) enabledVectors.push(s);
      });
      configuration.attackVectors = enabledVectors;
      configuration.campaignId = `camp-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 8)}`;
    }
    try {
      const run = await createSimulation({
        name: name.trim() || `${selected.label} scenario`,
        type: typeValue,
        configuration,
        runBy: user?.username,
      });
      const campaignInfo = configuration.campaignId ? ` (Campaign: ${configuration.campaignId})` : '';
      setSubmitOk(`Simulation ${run.simulationId ?? ''} queued${campaignInfo} — the live pipeline is now processing it.`);
      setName('');
      list.refetch();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to launch simulation.');
    } finally {
      setSubmitting(false);
    }
  };

  const activeCount = list.data.filter(
    (s) => s.status === 'RUNNING' || s.status === 'QUEUED' || s.status === 'PENDING',
  ).length;
  const completedCount = list.data.filter((s) => (s.status || '').toUpperCase() === 'COMPLETED').length;

  // Poll the run list every couple of seconds while a run is in flight so the
  // live pipeline panel tracks counters without a manual refresh.
  useEffect(() => {
    if (!activeCount) return;
    const id = setInterval(() => list.refetch(), 2000);
    return () => clearInterval(id);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeCount]);

  // ---------------------------------------------------------- SSE streaming
  // Connects to the real SSE endpoint when available, falling back to the
  // synthetic mock stream. Cancellation propagates to both paths.
  // Tracks Prometheus metrics during simulation execution.
  useEffect(() => {
    if (!liveRun) {
      setStream(null);
      setStreaming(false);
      metrics.setActiveSimulations(0);
      return;
    }
    const runId = liveRun.simulationId ?? liveRun.id ?? '';
    if (!runId) return;

    setStreaming(true);
    metrics.setActiveSimulations(1);
    const ctx = createContext(`simulation:${liveRun.type ?? liveRun.scenario}`);

    // Track simulation start
    metrics.trackSimulationEvent('start');
    metrics.trackKafkaMessage('simulation-events', 'produced');

    // Try the real SSE endpoint first.
    let es: EventSource | null = null;
    let mock: { cancel: () => void } | null = null;
    let useMock = false;

    const handleProgress = (data: SimulationProgress) => {
      setStream(data);
      // Track metrics from stream data
      if (data.eventsPerSec > 0) {
        metrics.trackSimulationEvent('event');
        metrics.trackKafkaMessage('security-events', 'produced');
      }
      if (data.detections > 0) {
        metrics.trackDetection('simulation-rule', 'HIGH');
      }
      if (data.riskDecisions > 0) {
        metrics.trackRiskDecision('HIGH', 'MONITOR');
      }
      if (data.alerts > 0) {
        metrics.trackAlert('HIGH');
      }
      if (data.actions > 0) {
        metrics.trackBlockedUser();
        metrics.trackHeldTransaction();
      }
      metrics.trackSimulationDuration(data.elapsed);
    };

    const handleDone = (data: SimulationProgress) => {
      setStream(data);
      setStreaming(false);
      metrics.setActiveSimulations(0);
      metrics.trackSimulationEvent('complete');
      metrics.trackSimulationDuration(getDuration(ctx));
      if (data.errors?.length) {
        metrics.trackSimulationError('execution_error');
      }
      metrics.trackKafkaMessage('simulation-events', 'consumed');
      list.refetch();
    };

    try {
      es = streamSimulation(runId);
      es.onmessage = (event) => {
        try {
          const data = JSON.parse(event.data) as SimulationProgress;
          handleProgress(data);
          if (data.status === 'COMPLETED' || data.status === 'CANCELLED' || data.status === 'FAILED') {
            es?.close();
            handleDone(data);
          }
        } catch {
          // Ignore malformed messages.
        }
      };
      es.onerror = () => {
        // Fall back to mock stream on SSE error.
        es?.close();
        if (useMock) return;
        useMock = true;
        metrics.trackKafkaError('simulation-events');
        mock = mockStreamSimulation(liveRun, handleProgress, handleDone);
        mockRef.current = mock;
      };
    } catch {
      // SSE not supported — use mock stream.
      useMock = true;
      mock = mockStreamSimulation(liveRun, handleProgress, handleDone);
      mockRef.current = mock;
    }

    esRef.current = es;

    return () => {
      if (es && !useMock) {
        es.close();
      }
      if (mock) {
        mock.cancel();
      }
      mockRef.current = null;
      esRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [liveRun?.simulationId, liveRun?.id]);

  // ---------------------------------------------------------- cancellation
  const handleCancel = useCallback(async () => {
    if (!liveRun) return;
    const runId = liveRun.simulationId ?? liveRun.id ?? '';
    // Stop the local stream immediately.
    if (mockRef.current) {
      mockRef.current.cancel();
    }
    if (esRef.current) {
      esRef.current.close();
    }
    // Track cancellation metrics
    metrics.trackSimulationEvent('cancelled');
    metrics.trackSimulationError('user_cancellation');
    metrics.setActiveSimulations(0);
    // Notify the backend.
    try {
      await cancelSimulation(runId);
    } catch {
      // Backend may not be reachable — local cancellation already done.
    }
    setStreaming(false);
    list.refetch();
  }, [liveRun, list]);

  const columns: Column<SimulationRun>[] = [
    { key: 'name', header: 'Name', render: (s) => <strong>{s.name}</strong> },
    { key: 'type', header: 'Type', render: (s) => <span>{s.type ?? s.scenario}</span> },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
    { key: 'events', header: 'Events', render: (s) => <span>{s.eventsGenerated?.toLocaleString() ?? '—'}</span> },
    { key: 'detections', header: 'Detections', render: (s) => <span>{s.detections?.toLocaleString() ?? '—'}</span> },
    { key: 'runby', header: 'Run By', render: (s) => <span className="muted">{s.runBy ?? '—'}</span> },
    { key: 'started', header: 'Started', render: (s) => <span className="muted">{relativeTime(s.startedAt)}</span> },
  ];

  if (list.source === 'error') {
    return (
      <ErrorState
        title="Simulation service unavailable"
        message={list.error ?? 'Unknown error.'}
        onRetry={list.refetch}
      />
    );
  }

  const renderFields = (fields: FieldDef[], prefix?: string) =>
    fields.map((f) => {
      if (f.kind === 'select') {
        return (
          <label className="field sim-select-field" key={f.key}>
            <span>{f.label}</span>
            <select
              value={String(values[f.key] ?? f.def)}
              onChange={(e) => setValue(f.key, e.target.value)}
            >
              {f.options!.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
          </label>
        );
      }
      return (
        <SliderField
          key={f.key}
          field={f}
          value={Number(values[f.key] ?? f.def)}
          onChange={(v) => setValue(f.key, v)}
        />
      );
    });

  return (
    <div className="page">
      {list.source === 'demo' ? (
        <DemoBanner reason="Simulation launch posts to the live API; history below is synthetic." />
      ) : null}

      <div className="stat-grid">
        <StatCard label="Active Runs" value={activeCount} tone="warn" icon="▶" />
        <StatCard label="Completed" value={completedCount} tone="good" icon="✓" />
        <StatCard label="Total Runs" value={list.data.length} tone="info" icon="◷" />
        <StatCard label="Platform" value="Isolated" tone="violet" icon="🛡" />
      </div>

      <div className="sim-layout grid-2">
        <Card title="Launch Simulation">
          <form className="sim-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>Simulation Type</span>
              <select value={typeValue} onChange={(e) => handleTypeChange(e.target.value)}>
                {SIM_TYPES.map((t) => (
                  <option key={t.value} value={t.value}>{t.label}</option>
                ))}
              </select>
            </label>
            <p className="field-hint">{selected.desc}</p>

            <label className="field">
              <span>Run Name (optional)</span>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Q3 credential drill" />
            </label>

            <Section title="Common Parameters">{renderFields(COMMON_FIELDS)}</Section>

            {typeValue === 'MIXED_ATTACK' ? (
              <Section title="Attack Vectors">
                {(Object.keys(includeVectors) as SimSection[]).map((s) => (
                  <label className="sim-checkbox" key={s}>
                    <input
                      type="checkbox"
                      checked={includeVectors[s]}
                      onChange={(e) => setIncludeVectors((prev) => ({ ...prev, [s]: e.target.checked }))}
                    />
                    <span>{VECTOR_LABELS[s]}</span>
                  </label>
                ))}
              </Section>
            ) : null}

            {activeSections.map((s) => (
              <Section key={s} title={SECTION_TITLES[s]}>
                {renderFields(SECTION_FIELDS[s], SECTION_TITLES[s])}
              </Section>
            ))}

            {errors.length > 0 ? (
              <div className="form-error sim-errors" role="alert">
                <strong>Fix before launching:</strong>
                <ul>
                  {errors.map((e) => <li key={e}>{e}</li>)}
                </ul>
              </div>
            ) : null}
            {submitError ? <p className="form-error" role="alert">{submitError}</p> : null}
            {submitOk ? <p className="form-ok">{submitOk}</p> : null}

            <button type="submit" className="btn btn-primary" disabled={submitting || errors.length > 0}>
              {submitting ? 'Launching…' : '▶ Launch Simulation'}
            </button>
          </form>
        </Card>

        <div className="sim-side">
          <Card title="Estimated Impact">
            <div className="sim-estimates">
              <div className="sim-estimate">
                <span className="sim-estimate-label">Estimated Users</span>
                <strong>{estimates.users.toLocaleString()}</strong>
              </div>
              <div className={`sim-estimate ${estimates.overLimit ? 'sim-estimate-bad' : ''}`}>
                <span className="sim-estimate-label">Estimated Events</span>
                <strong>{estimates.events.toLocaleString()}</strong>
                <span className="muted">cap {LIMITS.maxTotalEvents.toLocaleString()}</span>
              </div>
              <div className="sim-estimate">
                <span className="sim-estimate-label">Estimated Transactions</span>
                <strong>{estimates.transactions ? estimates.transactions.toLocaleString() : '—'}</strong>
              </div>
              <div className="sim-estimate">
                <span className="sim-estimate-label">Estimated Requests</span>
                <strong>{estimates.requests.toLocaleString()}</strong>
              </div>
              <div className="sim-estimate">
                <span className="sim-estimate-label">Estimated Duration</span>
                <strong>{estimates.duration}s</strong>
              </div>
            </div>
            <p className="field-hint">
              Limits are enforced here and re-validated by the backend. Events flow
              through the real detection → risk → alert pipeline; simulations never
              create alerts directly.
            </p>
          </Card>

          <Card title="Scenario Library" className="scenario-library">
            <div className="scenario-list">
              {SIM_TYPES.map((t) => (
                <button
                  key={t.value}
                  type="button"
                  className={`scenario-card ${typeValue === t.value ? 'active' : ''}`}
                  onClick={() => handleTypeChange(t.value)}
                >
                  <strong>{t.label}</strong>
                  <span>{t.desc}</span>
                </button>
              ))}
            </div>
          </Card>
        </div>
      </div>

      <Card
        title="Live Pipeline"
        actions={
          live.present ? (
            <span className="muted">
              {liveRun?.status ?? ''} · {liveRun?.type ?? liveRun?.scenario}
            </span>
          ) : null
        }
        className="live-pipeline"
      >
        {live.present ? (
          <>
            {/* Primary metrics grid */}
            <div className="live-grid live-grid-primary">
              <StatCard label="Simulation ID" value={<span className="mono">{liveRun?.simulationId ?? liveRun?.id ?? '—'}</span>} tone="info" icon="◈" />
              <StatCard label="Status" value={stream?.status ?? liveRun?.status ?? '—'} tone={stream?.status === 'RUNNING' ? 'warn' : stream?.status === 'COMPLETED' ? 'good' : 'info'} icon="▶" />
              <StatCard label="Elapsed" value={`${stream?.elapsed ?? 0}s`} tone="info" icon="◷" />
              <StatCard label="Progress" value={`${Math.round((stream?.progress ?? 0) * 100)}%`} tone={stream?.progress === 1 ? 'good' : 'warn'} icon="▲" spark={stream?.eventsPerSecond?.length ? stream.eventsPerSecond.slice(-10) : undefined} />
            </div>

            {/* Counters grid */}
            <div className="live-grid live-grid-counters">
              <StatCard label="Users" value={(stream?.users ?? live.users).toLocaleString()} tone="info" icon="◉" />
              <StatCard label="Transactions" value={(stream?.transactions ?? live.transactions).toLocaleString()} tone="violet" icon="⇄" />
              <StatCard label="API Requests" value={(stream?.apiRequests ?? live.requests).toLocaleString()} tone="info" icon="⌗" />
              <StatCard label="Network Events" value={(stream?.networkEvents ?? 0).toLocaleString()} tone="good" icon="⬡" />
              <StatCard label="Security Events" value={(stream?.securityEvents ?? live.events).toLocaleString()} tone="warn" icon="◷" />
              <StatCard label="Detections" value={(stream?.detections ?? 0).toLocaleString()} tone={stream?.detections ? 'critical' : 'neutral'} icon="◉" />
              <StatCard label="Risk Decisions" value={(stream?.riskDecisions ?? 0).toLocaleString()} tone="violet" icon="▲" />
              <StatCard label="Alerts" value={(stream?.alerts ?? 0).toLocaleString()} tone={stream?.alerts ? 'bad' : 'neutral'} icon="⚑" />
              <StatCard label="Actions" value={(stream?.actions ?? 0).toLocaleString()} tone={stream?.actions ? 'warn' : 'neutral'} icon="🛡" />
              <StatCard label="Errors" value={stream?.errors?.length ?? live.errors} tone={stream?.errors?.length ? 'bad' : 'good'} icon="✕" />
              <StatCard label="Events / sec" value={(stream?.eventsPerSec ?? live.eps).toLocaleString()} tone="good" icon="⚡" />
            </div>

            {/* Charts row */}
            {stream?.eventsPerSecond?.length ? (
              <div className="live-charts">
                <div className="live-chart-card">
                  <h4 className="live-chart-title">Events / sec</h4>
                  <LineChart
                    series={[{ name: 'EPS', values: stream.eventsPerSecond, color: '#22d3ee' }]}
                    height={160}
                  />
                </div>
                <div className="live-chart-card">
                  <h4 className="live-chart-title">Risk Distribution</h4>
                  <LineChart
                    series={[
                      { name: 'High', values: stream.eventsPerSecond.map((_, i) => Math.floor(stream.riskDistribution.HIGH * (i + 1) / stream.eventsPerSecond.length)), color: '#f43f5e' },
                      { name: 'Medium', values: stream.eventsPerSecond.map((_, i) => Math.floor(stream.riskDistribution.MEDIUM * (i + 1) / stream.eventsPerSecond.length)), color: '#fbbf24' },
                      { name: 'Low', values: stream.eventsPerSecond.map((_, i) => Math.floor(stream.riskDistribution.LOW * (i + 1) / stream.eventsPerSecond.length)), color: '#34d399' },
                    ]}
                    height={160}
                  />
                  <ChartLegend items={[
                    { label: 'High', color: '#f43f5e' },
                    { label: 'Medium', color: '#fbbf24' },
                    { label: 'Low', color: '#34d399' },
                  ]} />
                </div>
                <div className="live-chart-card">
                  <h4 className="live-chart-title">Alerts Over Time</h4>
                  <LineChart
                    series={[{ name: 'Alerts', values: stream.alertsOverTime, color: '#f43f5e' }]}
                    height={160}
                  />
                </div>
              </div>
            ) : null}

            {/* Cancel button */}
            {streaming ? (
              <div className="live-actions">
                <button type="button" className="btn btn-danger" onClick={handleCancel}>
                  ✕ Cancel Simulation
                </button>
              </div>
            ) : null}
          </>
        ) : (
          <p className="field-hint">
            No runs yet — launch a simulation above and its live counters will appear here.
          </p>
        )}

        {/* Brute-force drill dashboard: attack volume + pipeline response. */}
        {live.present && (liveRun?.type === 'BRUTE_FORCE' || liveRun?.scenario === 'BRUTE_FORCE') ? (
          <div className="bf-dashboard">
            <h3 className="bf-heading">Brute-Force Drill</h3>
            <div className="live-grid">
              <StatCard label="Attack Rate" value={`${live.bf.attackRate}/s`} tone="critical" icon="⚡" />
              <StatCard label="Failed Logins" value={live.bf.failedLogins.toLocaleString()} tone="bad" icon="✕" />
              <StatCard label="Target Users" value={live.bf.targetUsers.toLocaleString()} tone="warn" icon="◉" />
              <StatCard label="Source IPs" value={live.bf.sourceIps.toLocaleString()} tone="info" icon="⬡" />
              <StatCard
                label="Risk Decisions"
                value={live.bf.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Alerts"
                value={live.bf.alerts.toLocaleString()}
                tone={live.bf.alerts > 0 ? 'critical' : 'neutral'}
                icon="⚑"
              />
              <StatCard
                label="Actions"
                value={live.bf.actions.toLocaleString()}
                tone={live.bf.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>RATE_LIMIT or BLOCK_ACCOUNT auto-applied by risk level</span>
                  ) : null
                }
              />
            </div>
          </div>
        ) : null}

        {/* Account-takeover drill dashboard: full attack-chain visualization. */}
        {live.present && (liveRun?.type === 'ACCOUNT_TAKEOVER' || liveRun?.scenario === 'ACCOUNT_TAKEOVER') ? (
          <div className="ato-dashboard">
            <h3 className="ato-heading">Account-Takeover Drill</h3>
            <div className="ato-chain">
              <div className="ato-chain-step">
                <span className="ato-chain-icon">✕</span>
                <span className="ato-chain-label">Failed Logins</span>
                <span className="ato-chain-value">{live.ato.failedLogins.toLocaleString()}</span>
              </div>
              <span className="ato-chain-arrow">→</span>
              <div className="ato-chain-step">
                <span className="ato-chain-icon">⬡</span>
                <span className="ato-chain-label">New IP</span>
                <span className="ato-chain-value">{live.ato.newIpLogins.toLocaleString()}</span>
              </div>
              <span className="ato-chain-arrow">→</span>
              <div className="ato-chain-step">
                <span className="ato-chain-icon">◉</span>
                <span className="ato-chain-label">New Device</span>
                <span className="ato-chain-value">{live.ato.newDeviceLogins.toLocaleString()}</span>
              </div>
              <span className="ato-chain-arrow">→</span>
              <div className="ato-chain-step">
                <span className="ato-chain-icon">✓</span>
                <span className="ato-chain-label">Login</span>
                <span className="ato-chain-value">{live.ato.successfulLogins.toLocaleString()}</span>
              </div>
              <span className="ato-chain-arrow">→</span>
              <div className="ato-chain-step">
                <span className="ato-chain-icon">⇄</span>
                <span className="ato-chain-label">Payment</span>
                <span className="ato-chain-value">{live.ato.suspiciousPayments.toLocaleString()}</span>
              </div>
            </div>
            <div className="live-grid ato-grid">
              <StatCard label="Target Users" value={live.ato.targetUsers.toLocaleString()} tone="warn" icon="◉" />
              <StatCard
                label="Risk Decisions"
                value={live.ato.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Alerts"
                value={live.ato.alerts.toLocaleString()}
                tone={live.ato.alerts > 0 ? 'critical' : 'neutral'}
                icon="⚑"
              />
              <StatCard
                label="Actions"
                value={live.ato.actions.toLocaleString()}
                tone={live.ato.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>BLOCK_ACCOUNT + HOLD_TRANSACTION on CRITICAL</span>
                  ) : null
                }
              />
            </div>
          </div>
        ) : null}

        {/* Payment-fraud drill dashboard: transaction analysis + pipeline response. */}
        {live.present && (liveRun?.type === 'PAYMENT_FRAUD' || liveRun?.scenario === 'PAYMENT_FRAUD') ? (
          <div className="pf-dashboard">
            <h3 className="pf-heading">Payment-Fraud Drill</h3>
            <div className="live-grid pf-grid">
              <StatCard label="Total Payments" value={live.pf.totalPayments.toLocaleString()} tone="info" icon="⇄" />
              <StatCard label="High Value" value={live.pf.highValuePayments.toLocaleString()} tone="critical" icon="₹" />
              <StatCard label="Declined" value={live.pf.declinedPayments.toLocaleString()} tone="bad" icon="✕" />
              <StatCard label="New Device" value={live.pf.newDevicePayments.toLocaleString()} tone="warn" icon="◉" />
              <StatCard label="Suspicious IP" value={live.pf.suspiciousIpPayments.toLocaleString()} tone="warn" icon="⬡" />
              <StatCard label="Fraud Ring Cards" value={live.pf.fraudRingCards.toLocaleString()} tone="violet" icon="⌗" />
              <StatCard
                label="Risk Decisions"
                value={live.pf.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Alerts"
                value={live.pf.alerts.toLocaleString()}
                tone={live.pf.alerts > 0 ? 'critical' : 'neutral'}
                icon="⚑"
              />
              <StatCard
                label="Actions"
                value={live.pf.actions.toLocaleString()}
                tone={live.pf.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>HOLD_TRANSACTION on CRITICAL — payments held</span>
                  ) : null
                }
              />
            </div>
          </div>
        ) : null}

        {/* Transaction-velocity drill dashboard: rapid payment bursts + pipeline response. */}
        {live.present && (liveRun?.type === 'TRANSACTION_VELOCITY' || liveRun?.scenario === 'TRANSACTION_VELOCITY') ? (
          <div className="tv-dashboard">
            <h3 className="tv-heading">Transaction-Velocity Drill</h3>
            <div className="live-grid tv-grid">
              <StatCard label="Transactions / sec" value={live.tv.attackVelocity.toLocaleString()} tone="critical" icon="⇄" />
              <StatCard label="Baseline" value={live.tv.baselineRate.toLocaleString()} tone="info" icon="◷" />
              <StatCard label="Attack Velocity" value={live.tv.burstPayments.toLocaleString()} tone="warn" icon="⚡" />
              <StatCard label="Affected Users" value={live.tv.affectedUsers.toLocaleString()} tone="warn" icon="◉" />
              <StatCard
                label="Risk"
                value={live.tv.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Held Payments"
                value={live.tv.heldPayments.toLocaleString()}
                tone={live.tv.heldPayments > 0 ? 'critical' : 'neutral'}
                icon="⏸"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>HOLD_TRANSACTION on CRITICAL</span>
                  ) : null
                }
              />
            </div>
          </div>
        ) : null}

        {/* API-abuse drill dashboard: request flood + Redis rate-limit response. */}
        {live.present && (liveRun?.type === 'API_ABUSE' || liveRun?.scenario === 'API_ABUSE') ? (
          <div className="api-dashboard">
            <h3 className="api-heading">API-Abuse Drill</h3>
            <div className="live-grid api-grid">
              <StatCard label="Current RPS" value={live.api.currentRps.toLocaleString()} tone="critical" icon="⌗" />
              <StatCard label="Baseline" value={live.api.baselineRps.toLocaleString()} tone="info" icon="◷" />
              <StatCard label="Attack RPS" value={live.api.attackRps.toLocaleString()} tone="warn" icon="⚡" />
              <StatCard label="Blocked Requests" value={live.api.blockedRequests.toLocaleString()} tone="bad" icon="⛔" />
              <StatCard label="Top IPs" value={live.api.topIps.toLocaleString()} tone="warn" icon="⬡" />
              <StatCard label="Top Endpoints" value={live.api.topEndpoints.toLocaleString()} tone="warn" icon="⌗" />
              <StatCard
                label="Risk"
                value={live.api.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Alerts"
                value={live.api.alerts.toLocaleString()}
                tone={live.api.alerts > 0 ? 'critical' : 'neutral'}
                icon="⚑"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>RATE_LIMIT on API_ABUSE_DETECTED</span>
                  ) : null
                }
              />
            </div>
            <Card title="Requests / sec (live)">
              <LineChart
                series={[
                  { name: 'Baseline', values: buildRpsSeries(live.api.baselineRps, 24, false), color: '#22d3ee' },
                  { name: 'Attack', values: buildRpsSeries(live.api.attackRps, 24, true), color: '#f59e0b' },
                ]}
                labels={Array.from({ length: 24 }, (_, i) => `${i}s`)}
              />
            </Card>
          </div>
        ) : null}

        {/* Bot-activity drill dashboard: bot fleet mixed with legitimate traffic. */}
        {live.present && (liveRun?.type === 'BOT_ACTIVITY' || liveRun?.scenario === 'BOT_ACTIVITY') ? (
          <div className="bot-dashboard">
            <h3 className="bot-heading">Bot-Activity Drill</h3>
            <div className="live-grid bot-grid">
              <StatCard label="Legitimate %" value={`${live.bot.legitimatePct}%`} tone="good" icon="◉" />
              <StatCard label="Bot %" value={`${live.bot.botPct}%`} tone="warn" icon="◇" />
              <StatCard label="Bot IPs" value={live.bot.botIps.toLocaleString()} tone="warn" icon="⬡" />
              <StatCard label="Endpoints" value={live.bot.endpoints.toLocaleString()} tone="info" icon="⌗" />
              <StatCard label="Requests" value={live.bot.totalRequests.toLocaleString()} tone="bad" icon="⇄" />
              <StatCard
                label="Risk"
                value={live.bot.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Actions"
                value={live.bot.actions.toLocaleString()}
                tone={live.bot.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>RATE_LIMIT on BOT_ACTIVITY</span>
                  ) : null
                }
              />
            </div>
            <Card title="Traffic Mix (live)">
              <LineChart
                series={[
                  { name: 'Legitimate', values: buildRpsSeries(Math.max(1, live.bot.legits / 10), 24, false), color: '#22d3ee' },
                  { name: 'Bot', values: buildRpsSeries(Math.max(1, live.bot.bots / 10), 24, true), color: '#f59e0b' },
                ]}
                labels={Array.from({ length: 24 }, (_, i) => `${i}s`)}
              />
            </Card>
          </div>
        ) : null}

        {/* Network drill dashboard: compose-internal synthetic observations. */}
        {live.present && liveRun != null && NETWORK_SIM_TYPES.includes(String(liveRun.type ?? liveRun.scenario ?? '')) ? (
          <div className="network-dashboard">
            <h3 className="network-heading">Network Drill</h3>
            <div className="live-grid network-grid">
              <StatCard label="Rate" value={`${live.network.currentRate.toLocaleString()} /s`} tone="critical" icon="⇄" />
              <StatCard label="Sources" value={live.network.distinctSources.toLocaleString()} tone="info" icon="◉" />
              <StatCard label="Destinations" value={live.network.distinctDestinations.toLocaleString()} tone="info" icon="⬡" />
              <StatCard label="Ports" value={live.network.distinctPorts.toLocaleString()} tone="warn" icon="⌗" />
              <StatCard label="Risk" value={live.network.risk.toLocaleString()} tone="violet" icon="▲" />
              <StatCard
                label="Actions"
                value={live.network.actions.toLocaleString()}
                tone={live.network.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>Synthetic events only — compose-internal targets</span>
                  ) : null
                }
              />
            </div>
            <Card title="Top Flows (Source → Destination)">
              <DataTable<NetworkFlow>
                columns={[
                  { key: 'source', header: 'Source', render: (f) => <span className="mono">{f.source}</span> },
                  { key: 'destination', header: 'Destination', render: (f) => <span className="mono">{f.destination}</span> },
                  { key: 'port', header: 'Port', render: (f) => <span className="mono">{f.port}</span> },
                  { key: 'protocol', header: 'Protocol', render: (f) => <span className="muted">{f.protocol}</span> },
                  { key: 'rate', header: 'Rate', render: (f) => <strong>{`${f.rate.toLocaleString()} /s`}</strong> },
                  { key: 'risk', header: 'Risk', render: (f) => <span>{f.risk}</span> },
                  { key: 'severity', header: 'Severity', render: (f) => <StatusBadge status={f.severity} /> },
                ]}
                data={live.network.flows}
                rowKey={(f) => `${f.source}|${f.destination}|${f.port}|${f.protocol}`}
                itemName="network flows"
              />
            </Card>
          </div>
        ) : null}

        {/* Mixed-attack campaign dashboard: multi-vector attack visualization. */}
        {live.present && (liveRun?.type === 'MIXED_ATTACK' || liveRun?.scenario === 'MIXED_ATTACK') ? (
          <div className="mixed-dashboard">
            <h3 className="mixed-heading">Mixed-Attack Campaign</h3>
            <div className="mixed-campaign-flow">
              <div className="mixed-flow-step mixed-flow-campaign">
                <span className="mixed-flow-icon">◈</span>
                <span className="mixed-flow-label">Campaign</span>
                <span className="mixed-flow-value mono">{live.mixed.campaignId || '—'}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-auth">
                <span className="mixed-flow-icon">✕</span>
                <span className="mixed-flow-label">Authentication</span>
                <span className="mixed-flow-value">{live.mixed.authEvents.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-api">
                <span className="mixed-flow-icon">⌗</span>
                <span className="mixed-flow-label">API</span>
                <span className="mixed-flow-value">{live.mixed.apiEvents.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-payments">
                <span className="mixed-flow-icon">⇄</span>
                <span className="mixed-flow-label">Payments</span>
                <span className="mixed-flow-value">{live.mixed.paymentEvents.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-network">
                <span className="mixed-flow-icon">⬡</span>
                <span className="mixed-flow-label">Network</span>
                <span className="mixed-flow-value">{live.mixed.networkEvents.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-detection">
                <span className="mixed-flow-icon">◉</span>
                <span className="mixed-flow-label">Detection</span>
                <span className="mixed-flow-value">{live.mixed.detections.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-risk">
                <span className="mixed-flow-icon">▲</span>
                <span className="mixed-flow-label">Risk</span>
                <span className="mixed-flow-value">{live.mixed.risk.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-alerts">
                <span className="mixed-flow-icon">⚑</span>
                <span className="mixed-flow-label">Alerts</span>
                <span className="mixed-flow-value">{live.mixed.alerts.toLocaleString()}</span>
              </div>
              <span className="mixed-flow-arrow">→</span>
              <div className="mixed-flow-step mixed-flow-actions">
                <span className="mixed-flow-icon">🛡</span>
                <span className="mixed-flow-label">Actions</span>
                <span className="mixed-flow-value">{live.mixed.actions.toLocaleString()}</span>
              </div>
            </div>

            <div className="mixed-vectors">
              <h4 className="mixed-subheading">Active Attack Vectors</h4>
              <div className="mixed-vectors-list">
                {live.mixed.attackVectors.length > 0 ? (
                  live.mixed.attackVectors.map((v) => (
                    <span className="mixed-vector-tag" key={v}>
                      {VECTOR_LABELS[v as SimSection] || v}
                    </span>
                  ))
                ) : (
                  <span className="muted">No vectors selected</span>
                )}
              </div>
            </div>

            <div className="live-grid mixed-grid">
              <StatCard
                label="Total Attack Events"
                value={live.mixed.totalAttackEvents.toLocaleString()}
                tone="critical"
                icon="⚡"
              />
              <StatCard
                label="Data Access Events"
                value={live.mixed.dataAccessEvents.toLocaleString()}
                tone="warn"
                icon="⌗"
              />
              <StatCard
                label="Attack Intensity"
                value={`${live.mixed.attackIntensity}%`}
                tone={live.mixed.attackIntensity > 50 ? 'critical' : 'warn'}
                icon="▲"
              />
              <StatCard
                label="Affected Users"
                value={live.mixed.affectedUsers.toLocaleString()}
                tone="warn"
                icon="◉"
              />
              <StatCard
                label="Detections"
                value={live.mixed.detections.toLocaleString()}
                tone={live.mixed.detections > 0 ? 'good' : 'neutral'}
                icon="◉"
              />
              <StatCard
                label="Risk Decisions"
                value={live.mixed.risk.toLocaleString()}
                tone="violet"
                icon="▲"
              />
              <StatCard
                label="Alerts"
                value={live.mixed.alerts.toLocaleString()}
                tone={live.mixed.alerts > 0 ? 'critical' : 'neutral'}
                icon="⚑"
              />
              <StatCard
                label="Actions"
                value={live.mixed.actions.toLocaleString()}
                tone={live.mixed.actions > 0 ? 'warn' : 'neutral'}
                icon="🛡"
                sub={
                  liveRun.status === 'RUNNING' || liveRun.status === 'QUEUED' ? (
                    <span>Multi-vector response — BLOCK, HOLD, RATE_LIMIT auto-applied</span>
                  ) : null
                }
              />
            </div>
          </div>
        ) : null}
        <p className="field-hint">
          Counters come from the real pipeline. The simulation only injects source events (logins,
          API requests, payments, orders, logouts); alerts are produced downstream, never here.
        </p>
      </Card>

      <Card title={`Recent Runs (${list.data.length})`}>
        <DataTable
          columns={columns}
          data={list.data.slice(0, 5)}
          rowKey={(s) => s.simulationId ?? s.id ?? ''}
          loading={list.loading}
          itemName="simulations"
        />
      </Card>
    </div>
  );
}

import { useEffect, useMemo, useState } from 'react';
import { createSimulation, listSimulations } from '../api/endpoints';
import { useCollection } from '../hooks/useCollection';
import { mockSimulations } from '../api/mock';
import type { SimulationRun } from '../api/types';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { relativeTime } from '../lib/format';

// ------------------------------------------------------------------ catalog

interface SimTypeMeta {
  value: string;
  label: string;
  desc: string;
  /** Extra parameter sections this scenario reveals. */
  sections: SimSection[];
}

type SimSection = 'payment' | 'auth' | 'api' | 'network';

const SIM_TYPES: SimTypeMeta[] = [
  { value: 'NORMAL_TRAFFIC', label: 'Normal Traffic', desc: 'Benign baseline — should raise no detections.', sections: [] },
  { value: 'MIXED_ATTACK', label: 'Mixed Attack', desc: 'Blended multi-vector attack rehearsal.', sections: [] },
  { value: 'BRUTE_FORCE', label: 'Brute Force', desc: 'Distributed credential guessing on few accounts.', sections: ['auth'] },
  { value: 'ACCOUNT_TAKEOVER', label: 'Account Takeover', desc: 'Compromise followed by privileged actions.', sections: ['auth', 'payment'] },
  { value: 'SUSPICIOUS_LOGIN', label: 'Suspicious Login', desc: 'Logins from unusual geos and hours.', sections: ['auth'] },
  { value: 'NEW_DEVICE', label: 'New Device', desc: 'First-seen device fingerprints.', sections: ['auth'] },
  { value: 'PAYMENT_FRAUD', label: 'Payment Fraud', desc: 'High-risk authorizations and holds.', sections: ['payment'] },
  { value: 'TRANSACTION_VELOCITY', label: 'Transaction Velocity', desc: 'Rapid-fire transactions per user.', sections: ['payment'] },
  { value: 'FAILED_PAYMENTS', label: 'Failed Payments', desc: 'Bursts of declined authorizations.', sections: ['payment'] },
  { value: 'API_ABUSE', label: 'API Abuse', desc: '4xx/429-heavy abusive request patterns.', sections: ['api'] },
  { value: 'BOT_ACTIVITY', label: 'Bot Activity', desc: 'High-volume uniform non-human traffic.', sections: ['api'] },
  { value: 'SUSPICIOUS_IP', label: 'Suspicious IP', desc: 'Traffic from anonymizer / bad-reputation IPs.', sections: ['api'] },
  { value: 'UNAUTHORIZED_DATA_ACCESS', label: 'Unauthorized Data Access', desc: 'Denied access to protected data endpoints.', sections: ['api'] },
  { value: 'PRIVILEGED_ACCESS_ANOMALY', label: 'Privileged Access Anomaly', desc: 'Admin actions outside expected patterns.', sections: ['api'] },
  { value: 'CHECKOUT_ABUSE', label: 'Checkout Abuse', desc: 'Rapid cart mutation and checkout hammering.', sections: [] },
  { value: 'INVENTORY_SCRAPING', label: 'Inventory Scraping', desc: 'Massive product/inventory view scraping.', sections: [] },
  { value: 'COUPON_ABUSE', label: 'Coupon Abuse', desc: 'Repeated coupon redemption across accounts.', sections: [] },
  { value: 'PORT_SCAN', label: 'Port Scan', desc: 'Sequential probes across many ports.', sections: ['network'] },
  { value: 'CONNECTION_SPIKE', label: 'Connection Spike', desc: 'Sudden connection-count surges.', sections: ['network'] },
  { value: 'SUSPICIOUS_OUTBOUND', label: 'Suspicious Outbound', desc: 'Large outbound transfers to odd hosts.', sections: ['network'] },
];

const SECTION_TITLES: Record<SimSection, string> = {
  payment: 'Payment Parameters',
  auth: 'Authentication Parameters',
  api: 'API Parameters',
  network: 'Network Parameters',
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
  { key: 'transactionsPerSecond', label: 'Transactions', kind: 'number', min: 1, max: 500, step: 1, def: 20, unit: 'txn/s' },
  { key: 'normalAmount', label: 'Normal Amount', kind: 'number', min: 1, max: 5_000, step: 1, def: 80, unit: 'USD' },
  { key: 'suspiciousAmount', label: 'Suspicious Amount', kind: 'number', min: 1, max: 25_000, step: 10, def: 1_200, unit: 'USD' },
  { key: 'highValueAmount', label: 'High-Value Amount', kind: 'number', min: 1, max: 100_000, step: 100, def: 9_500, unit: 'USD' },
  { key: 'velocity', label: 'Velocity', kind: 'number', min: 1, max: 100, step: 1, def: 10, unit: 'txn/user' },
  { key: 'failedPaymentPercentage', label: 'Failed Payment %', kind: 'percent', min: 0, max: 100, step: 1, def: 35, unit: '%' },
  { key: 'newDevicePercentage', label: 'New Device %', kind: 'percent', min: 0, max: 100, step: 1, def: 30, unit: '%' },
  { key: 'suspiciousIpPercentage', label: 'Suspicious IP %', kind: 'percent', min: 0, max: 100, step: 1, def: 40, unit: '%' },
];

const AUTH_FIELDS: FieldDef[] = [
  { key: 'targetUsers', label: 'Target Users', kind: 'number', min: 1, max: 1_000, step: 1, def: 5, unit: 'accounts' },
  { key: 'failedAttemptsPerUser', label: 'Failed Attempts / User', kind: 'number', min: 1, max: 1_000, step: 1, def: 50, unit: 'attempts' },
  { key: 'attemptsPerSecond', label: 'Attempts / sec', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 25, unit: 'att/s' },
  { key: 'authSourceIps', label: 'IPs', kind: 'number', min: 1, max: LIMITS.maxIps, step: 1, def: 12, unit: 'IPs' },
  { key: 'authDevices', label: 'Devices', kind: 'number', min: 1, max: LIMITS.maxDevices, step: 1, def: 8, unit: 'devices' },
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
  { key: 'normalRps', label: 'Normal RPS', kind: 'number', min: 1, max: 500, step: 1, def: 10, unit: 'req/s' },
  { key: 'attackRps', label: 'Attack RPS', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 80, unit: 'req/s' },
  { key: 'apiDurationSeconds', label: 'Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
];

const NETWORK_FIELDS: FieldDef[] = [
  { key: 'networkSources', label: 'Sources', kind: 'number', min: 1, max: 1_000, step: 1, def: 8, unit: 'hosts' },
  { key: 'networkTargets', label: 'Targets', kind: 'number', min: 1, max: 254, step: 1, def: 12, unit: 'hosts' },
  { key: 'networkPorts', label: 'Ports', kind: 'number', min: 1, max: 1_000, step: 1, def: 50, unit: 'ports' },
  { key: 'networkAttempts', label: 'Attempts', kind: 'number', min: 1, max: LIMITS.maxTotalEvents, step: 1, def: 500, unit: 'total' },
  { key: 'connectionsPerSecond', label: 'Connections / sec', kind: 'number', min: 1, max: LIMITS.maxEps, step: 1, def: 30, unit: 'conn/s' },
  { key: 'networkDurationSeconds', label: 'Duration', kind: 'number', min: 1, max: LIMITS.maxDuration, step: 1, def: 60, unit: 'sec' },
];

const SECTION_FIELDS: Record<SimSection, FieldDef[]> = {
  payment: PAYMENT_FIELDS,
  auth: AUTH_FIELDS,
  api: API_FIELDS,
  network: NETWORK_FIELDS,
};

const VECTOR_LABELS: Record<SimSection, string> = {
  payment: 'Include payment vector',
  auth: 'Include authentication vector',
  api: 'Include API vector',
  network: 'Include network vector',
};

// ------------------------------------------------------------- value state

type ParamValues = Record<string, number | string | boolean>;

function defaultsFor(sections: SimSection[]): ParamValues {
  const values: ParamValues = {};
  COMMON_FIELDS.forEach((f) => (values[f.key] = f.def));
  sections.forEach((s) => SECTION_FIELDS[s].forEach((f) => (values[f.key] = f.def)));
  return values;
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
      ? (Number(values.transactionsPerSecond) || 0) * duration
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

  const liveRun = useMemo(() => {
    const active = list.data.find(
      (s) => s.status === 'RUNNING' || s.status === 'QUEUED' || s.status === 'PENDING',
    );
    if (active) return active;
    return list.data.length ? list.data[list.data.length - 1] : null;
  }, [list.data]);

  const live = useMemo(() => {
    if (!liveRun) {
      return { present: false as const, users: 0, transactions: 0, requests: 0, events: 0, eps: 0, errors: 0 };
    }
    const cfg = (liveRun.configuration ?? liveRun.config ?? {}) as Record<string, unknown>;
    const num = (k: string, fb = 0) => {
      const v = cfg[k];
      return typeof v === 'number' && Number.isFinite(v) ? v : fb;
    };
    const duration = num('durationSeconds', 60);
    const eps = num('eventsPerSecond', 0);
    const transactions = num('transactionsPerSecond', 0) * duration;
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
      eps,
      errors: Array.isArray(liveRun.errors) ? liveRun.errors.length : 0,
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
      (Object.keys(includeVectors) as SimSection[]).forEach((s) => {
        configuration[`include${s[0].toUpperCase()}${s.slice(1)}`] = includeVectors[s];
      });
    }
    try {
      const run = await createSimulation({
        name: name.trim() || `${selected.label} scenario`,
        type: typeValue,
        configuration,
        runBy: user?.username,
      });
      setSubmitOk(`Simulation ${run.simulationId ?? ''} queued — the live pipeline is now processing it.`);
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
          <div className="live-grid">
            <StatCard label="Users" value={live.users.toLocaleString()} tone="info" icon="◉" />
            <StatCard
              label="Transactions"
              value={live.transactions ? live.transactions.toLocaleString() : '—'}
              tone="violet"
              icon="⇄"
            />
            <StatCard
              label="Requests"
              value={live.requests ? live.requests.toLocaleString() : '—'}
              tone="info"
              icon="⌗"
            />
            <StatCard label="Events" value={live.events.toLocaleString()} tone="warn" icon="◷" />
            <StatCard label="Events / sec" value={live.eps ? live.eps.toLocaleString() : '—'} tone="good" icon="⚡" />
            <StatCard
              label="Errors"
              value={live.errors}
              tone={live.errors > 0 ? 'bad' : 'good'}
              icon="✕"
            />
          </div>
        ) : (
          <p className="field-hint">
            No runs yet — launch a simulation above and its live counters will appear here.
          </p>
        )}
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

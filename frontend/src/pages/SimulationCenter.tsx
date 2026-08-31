import { useState } from 'react';
import { createSimulation, listSimulations } from '../api/endpoints';
import { useCollection } from '../hooks/useCollection';
import { mockSimulations } from '../api/mock';
import type { SimulationRun } from '../api/types';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import { relativeTime } from '../lib/format';

const SCENARIOS: { value: string; label: string; desc: string }[] = [
  { value: 'BRUTE_FORCE', label: 'Brute-Force', desc: 'Simulate distributed credential attacks.' },
  { value: 'CARD_FRAUD', label: 'Card Fraud', desc: 'Card-not-present fraud with velocity spikes.' },
  { value: 'API_ABUSE', label: 'API Abuse', desc: 'Token replay and malformed payloads.' },
  { value: 'INSIDER_THREAT', label: 'Insider Threat', desc: 'Bulk data export by a compromised role.' },
  { value: 'DISTRIBUTED_DENIAL', label: 'Distributed DoS', desc: 'Inbound network flood simulation.' },
];

export function SimulationCenter({ user }: { user?: { username: string } }) {
  const list = useCollection(listSimulations, mockSimulations, [], {
    fallback: 'auto',
    demoLabel: 'simulations API unreachable',
  });

  const [scenario, setScenario] = useState(SCENARIOS[0].value);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [volume, setVolume] = useState(200);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitOk, setSubmitOk] = useState(false);

  const selected = SCENARIOS.find((s) => s.value === scenario);

  const activeCount = list.data.filter(
    (s) => s.status === 'RUNNING' || s.status === 'PENDING',
  ).length;
  const completedCount = list.data.filter(
    (s) => (s.status || '').toUpperCase() === 'COMPLETED',
  ).length;

  const handleSubmit = async (ev: React.FormEvent) => {
    ev.preventDefault();
    setSubmitting(true);
    setSubmitError(null);
    setSubmitOk(false);
    const finalName = name.trim() || `${selected?.label ?? scenario} scenario`;
    try {
      await createSimulation({
        name: finalName,
        description: description.trim() || selected?.desc,
        scenario,
        config: { volume, users: volume, txnVolume: volume, rate: Math.min(volume, 50) },
        runBy: user?.username,
        status: 'PENDING',
      });
      setSubmitOk(true);
      setName('');
      setDescription('');
      list.refetch();
    } catch (err) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to launch simulation.');
    } finally {
      setSubmitting(false);
    }
  };

  const columns: Column<SimulationRun>[] = [
    { key: 'name', header: 'Name', render: (s) => <strong>{s.name}</strong> },
    { key: 'scenario', header: 'Scenario', render: (s) => <span>{s.scenario}</span> },
    { key: 'status', header: 'Status', render: (s) => <StatusBadge status={s.status} /> },
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

      <div className="grid-2 sim-layout">
        <Card title="Launch Simulation">
          <form className="sim-form" onSubmit={handleSubmit}>
            <label className="field">
              <span>Scenario</span>
              <select value={scenario} onChange={(e) => setScenario(e.target.value)}>
                {SCENARIOS.map((s) => (
                  <option key={s.value} value={s.value}>
                    {s.label}
                  </option>
                ))}
              </select>
            </label>
            <p className="field-hint">{selected?.desc}</p>

            <label className="field">
              <span>Run Name (optional)</span>
              <input value={name} onChange={(e) => setName(e.target.value)} placeholder="e.g. Q3 credential drill" />
            </label>

            <label className="field">
              <span>Description (optional)</span>
              <input
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="What are we validating?"
              />
            </label>

            <label className="field">
              <span>Volume: {volume}</span>
              <input
                type="range"
                min={50}
                max={1000}
                step={50}
                value={volume}
                onChange={(e) => setVolume(Number(e.target.value))}
              />
            </label>

            {submitError ? (
              <p className="form-error" role="alert">
                {submitError}
              </p>
            ) : null}
            {submitOk ? <p className="form-ok">Simulation launched successfully.</p> : null}

            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? 'Launching…' : '▶ Launch in Sandbox'}
            </button>
          </form>
        </Card>

        <Card title="Scenario Library" className="scenario-library">
          <div className="scenario-list">
            {SCENARIOS.map((s) => (
              <button
                key={s.value}
                type="button"
                className={`scenario-card ${scenario === s.value ? 'active' : ''}`}
                onClick={() => setScenario(s.value)}
              >
                <strong>{s.label}</strong>
                <span>{s.desc}</span>
              </button>
            ))}
          </div>
        </Card>
      </div>

      <Card title={`Recent Runs (${list.data.length})`}>
        <DataTable
          columns={columns}
          data={list.data.slice(0, 5)}
          rowKey={(s) => s.id}
          loading={list.loading}
          itemName="simulations"
        />
      </Card>
    </div>
  );
}
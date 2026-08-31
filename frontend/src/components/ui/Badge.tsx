// Reusable badge primitives: severity, risk and status. Deterministic tone
// mapping keeps color-coding consistent across the whole console.

export type Tone = 'neutral' | 'good' | 'warn' | 'bad' | 'info' | 'critical' | 'violet';

const TONE_MAP: Record<Tone, string> = {
  neutral: 'badge-neutral',
  good: 'badge-good',
  warn: 'badge-warn',
  bad: 'badge-bad',
  info: 'badge-info',
  critical: 'badge-critical',
  violet: 'badge-violet',
};

export function Badge({
  tone = 'neutral',
  dot,
  children,
}: {
  tone?: Tone;
  dot?: boolean;
  children: React.ReactNode;
}) {
  return (
    <span className={`badge ${TONE_MAP[tone]}`}>
      {dot ? <span className="badge-dot" aria-hidden /> : null}
      {children}
    </span>
  );
}

/** Severity → tone. */
export function severityTone(severity?: string): Tone {
  switch ((severity ?? '').toUpperCase()) {
    case 'CRITICAL':
      return 'critical';
    case 'HIGH':
      return 'bad';
    case 'MEDIUM':
      return 'warn';
    case 'LOW':
      return 'info';
    default:
      return 'neutral';
  }
}

export function SeverityBadge({ severity }: { severity?: string }) {
  return (
    <Badge tone={severityTone(severity)} dot>
      {severity ?? '—'}
    </Badge>
  );
}

/** Risk level → tone. */
export function riskTone(level?: string): Tone {
  switch ((level ?? '').toUpperCase()) {
    case 'CRITICAL':
      return 'critical';
    case 'HIGH':
      return 'bad';
    case 'MEDIUM':
      return 'warn';
    case 'LOW':
      return 'good';
    default:
      return 'neutral';
  }
}

export function RiskBadge({ level }: { level?: string }) {
  return (
    <Badge tone={riskTone(level)} dot>
      {level ?? '—'}
    </Badge>
  );
}

/** Numeric risk score (0..1) → lightweight pill. */
export function RiskScoreBar({ score }: { score?: number }) {
  if (score === undefined || score === null) {
    return <span className="muted">—</span>;
  }
  const pct = Math.max(0, Math.min(1, score)) * 100;
  const tone = score >= 0.8 ? 'risk-bar-bad' : score >= 0.5 ? 'risk-bar-warn' : 'risk-bar-good';
  return (
    <span className={`risk-bar ${tone}`} title={`${(score * 100).toFixed(0)}%`}>
      <span className="risk-bar-fill" style={{ width: `${pct}%` }} />
    </span>
  );
}

/** Generic status (OPEN/RESOLVED/HELD/… ) → tone. */
export function statusTone(status?: string): Tone {
  const ups = (status ?? '').toUpperCase();
  if (['RESOLVED', 'COMPLETED', 'SETTLED', 'SUCCESS', 'ACTIVE', 'ALLOW', 'UP', 'ACKNOWLEDGED'].includes(ups)) {
    return 'good';
  }
  if (['OPEN', 'PENDING', 'RUNNING', 'INVESTIGATING', 'MONITORED', 'REVIEW', 'HELD', 'FLAGGED', 'CHALLENGE'].includes(ups)) {
    return 'warn';
  }
  if (['BLOCKED', 'FAILED', 'DENIED', 'DOWN', 'CRITICAL', 'FORCED', 'THROTTLED'].includes(ups)) {
    return 'bad';
  }
  if (['DISMISSED', 'CANCELLED', 'REFUNDED', 'UNKNOWN'].includes(ups)) {
    return 'neutral';
  }
  return 'info';
}

export function StatusBadge({ status }: { status?: string }) {
  return <Badge tone={statusTone(status)}>{status ?? '—'}</Badge>;
}
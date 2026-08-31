// Card panel and stat card used across every dashboard page.

export function Card({
  title,
  actions,
  children,
  className = '',
}: {
  title?: React.ReactNode;
  actions?: React.ReactNode;
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <section className={`panel ${className}`}>
      {title || actions ? (
        <div className="panel-header">
          {title ? <h2 className="panel-title">{title}</h2> : <span />}
          {actions ? <div className="panel-actions">{actions}</div> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}

export type StatTone = 'good' | 'bad' | 'warn' | 'info' | 'violet' | 'critical' | 'neutral';

export function StatCard({
  label,
  value,
  sub,
  tone = 'neutral',
  icon,
  spark,
}: {
  label: string;
  value: number | string;
  sub?: React.ReactNode;
  tone?: StatTone;
  icon?: React.ReactNode;
  spark?: number[];
}) {
  return (
    <div className={`stat-card stat-${tone}`}>
      <div className="stat-top">
        <span className="stat-icon">{icon}</span>
        <span className="stat-label">{label}</span>
      </div>
      <div className="stat-value">{value}</div>
      {sub ? <div className="stat-sub">{sub}</div> : null}
      {spark ? <Sparkline data={spark} tone={tone} /> : null}
    </div>
  );
}

function Sparkline({ data, tone }: { data: number[]; tone: StatTone }) {
  const w = 120;
  const h = 28;
  const max = Math.max(...data, 1);
  const pts = data
    .map((v, i) => {
      const x = (i / Math.max(data.length - 1, 1)) * w;
      const y = h - (v / max) * (h - 2) - 1;
      return `${x.toFixed(1)},${y.toFixed(1)}`;
    })
    .join(' ');
  return (
    <svg className="sparkline" viewBox={`0 0 ${w} ${h}`} width={w} height={h} aria-hidden>
      <polyline
        className={`sparkline-path spark-${tone}`}
        points={pts}
        fill="none"
        strokeWidth="1.6"
      />
    </svg>
  );
}
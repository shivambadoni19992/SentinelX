// Dependency-free SVG charts. Sized responsively via viewBox + 100% width.

interface Series {
  name: string;
  values: number[];
  color: string;
}

export function LineChart({
  series,
  height = 200,
  labels,
}: {
  series: Series[];
  height?: number;
  labels?: string[];
}) {
  const w = 600;
  const h = height;
  const pad = { l: 34, r: 10, t: 10, b: 22 };
  const innerW = w - pad.l - pad.r;
  const innerH = h - pad.t - pad.b;
  const max = Math.max(1, ...series.flatMap((s) => s.values));
  const min = 0;
  const xFor = (i: number, n: number) => pad.l + (i / Math.max(n - 1, 1)) * innerW;
  const yFor = (v: number) => pad.t + innerH - (v - min) / (max - min) * innerH;

  return (
    <div className="chart">
      <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={height} role="img" aria-label="Line chart">
        {[0.25, 0.5, 0.75, 1].map((f) => (
          <line
            key={f}
            x1={pad.l}
            x2={w - pad.r}
            y1={pad.t + innerH * (1 - f)}
            y2={pad.t + innerH * (1 - f)}
            className="chart-grid"
          />
        ))}
        <text x={2} y={yFor(max)} className="chart-axis-label">
          {max}
        </text>
        {labels?.map((l, i) => {
          const show = labels.length <= 8 || i % Math.ceil(labels.length / 8) === 0;
          return show ? (
            <text key={i} x={xFor(i, labels.length)} y={h - 6} className="chart-x-label" textAnchor="middle">
              {l}
            </text>
          ) : null;
        })}
        {series.map((s) => {
          const pts = s.values
            .map((v, i) => `${xFor(i, s.values.length).toFixed(1)},${yFor(v).toFixed(1)}`)
            .join(' ');
          return (
            <g key={s.name}>
              <polyline points={pts} fill="none" stroke={s.color} strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              {s.values.map((v, i) => (
                <circle key={i} cx={xFor(i, s.values.length)} cy={yFor(v)} r={i === s.values.length - 1 ? 3 : 1.6} fill={s.color} />
              ))}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function ChartLegend({ items }: { items: { label: string; color: string }[] }) {
  return (
    <div className="chart-legend">
      {items.map((it) => (
        <span key={it.label} className="chart-legend-item">
          <span className="legend-swatch" style={{ background: it.color }} />
          {it.label}
        </span>
      ))}
    </div>
  );
}
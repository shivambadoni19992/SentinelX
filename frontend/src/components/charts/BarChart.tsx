export function BarChart({
  values,
  labels,
  color = 'var(--accent)',
  height = 180,
}: {
  values: number[];
  labels?: string[];
  color?: string;
  height?: number;
}) {
  const w = 600;
  const h = height;
  const pad = { l: 34, r: 8, t: 8, b: 22 };
  const innerW = w - pad.l - pad.r;
  const innerH = h - pad.t - pad.b;
  const max = Math.max(1, ...values);
  const n = values.length;
  const barW = innerW / n;
  const barPad = Math.min(14, barW * 0.35);

  return (
    <div className="chart">
      <svg viewBox={`0 0 ${w} ${h}`} width="100%" height={height} role="img" aria-label="Bar chart">
        {[0.5, 1].map((f) => (
          <line
            key={f}
            x1={pad.l}
            x2={w - pad.r}
            y1={pad.t + innerH * (1 - f)}
            y2={pad.t + innerH * (1 - f)}
            className="chart-grid"
          />
        ))}
        <text x={2} y={pad.t + 4} className="chart-axis-label">
          {max}
        </text>
        {values.map((v, i) => {
          const barH = (v / max) * innerH;
          const x = pad.l + i * barW + barPad;
          const y = pad.t + innerH - barH;
          const show = !labels || labels.length <= 12;
          return (
            <g key={i}>
              <rect x={x} y={y} width={barW - barPad * 2} height={Math.max(barH, 1)} fill={color} rx="3">
                <title>{`${labels?.[i] ?? i}: ${v}`}</title>
              </rect>
              {show && labels ? (
                <text x={x + (barW - barPad * 2) / 2} y={h - 6} className="chart-x-label" textAnchor="middle">
                  {labels[i]}
                </text>
              ) : null}
            </g>
          );
        })}
      </svg>
    </div>
  );
}

export function HBarChart({
  items,
  color = 'var(--accent)',
}: {
  items: { label: string; value: number }[];
  color?: string;
}) {
  const max = Math.max(1, ...items.map((i) => i.value));
  return (
    <div className="hbar-list">
      {items.map((it) => (
        <div key={it.label} className="hbar-row">
          <span className="hbar-label">{it.label}</span>
          <div className="hbar-track">
            <div
              className="hbar-fill"
              style={{ width: `${(it.value / max) * 100}%`, background: color }}
            />
          </div>
          <span className="hbar-value">{it.value}</span>
        </div>
      ))}
    </div>
  );
}
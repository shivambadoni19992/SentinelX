export interface DonutDatum {
  label: string;
  value: number;
  color: string;
}

export function DonutChart({
  data,
  size = 180,
  thickness = 26,
  centerLabel,
  centerValue,
}: {
  data: DonutDatum[];
  size?: number;
  thickness?: number;
  centerLabel?: string;
  centerValue?: React.ReactNode;
}) {
  const total = Math.max(1, data.reduce((s, d) => s + d.value, 0));
  const radius = (size - thickness) / 2;
  const cx = size / 2;
  const cy = size / 2;
  const circ = 2 * Math.PI * radius;

  let acc = 0;
  const segments = data.map((d) => {
    const frac = d.value / total;
    const start = acc;
    acc += frac;
    return { ...d, frac, dash: frac * circ, offset: start * circ };
  });

  return (
    <div className="donut-wrap">
      <svg viewBox={`0 0 ${size} ${size}`} width={size} height={size} role="img" aria-label="Donut chart">
        <circle className="donut-track" cx={cx} cy={cy} r={radius} strokeWidth={thickness} fill="none" />
        {segments.map((seg) => (
          <circle
            key={seg.label}
            className="donut-seg"
            cx={cx}
            cy={cy}
            r={radius}
            stroke={seg.color}
            strokeWidth={thickness}
            fill="none"
            strokeDasharray={`${Math.max(0, seg.dash - 1.5)} ${circ}`}
            strokeDashoffset={-seg.offset}
            transform={`rotate(-90 ${cx} ${cy})`}
          />
        ))}
        {centerValue !== undefined ? (
          <text x={cx} y={cy - 2} textAnchor="middle" className="donut-center-value">
            {centerValue}
          </text>
        ) : null}
        {centerLabel ? (
          <text x={cx} y={cy + 12} textAnchor="middle" className="donut-center-label">
            {centerLabel}
          </text>
        ) : null}
      </svg>
      <div className="donut-legend">
        {data.map((d) => (
          <span key={d.label} className="donut-legend-item">
            <span className="legend-swatch" style={{ background: d.color }} />
            {d.label}
            <strong>{(d.value / total) * 100 > 0 ? `${Math.round((d.value / total) * 100)}%` : ''}</strong>
          </span>
        ))}
      </div>
    </div>
  );
}
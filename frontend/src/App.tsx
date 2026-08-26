import { useEffect, useState } from 'react';
import './App.css';

interface ServiceHealth {
  id: string;
  name: string;
  url: string;
  status: 'UP' | 'DOWN' | 'unknown';
  checkedAt: string;
  details?: Record<string, unknown>;
}

interface ServicesResponse {
  revealed: boolean;
  count: number;
  services: ServiceHealth[];
}

const INFRA = [
  { name: 'PostgreSQL', image: 'postgres:16-alpine', port: 5434 },
  { name: 'Redis', image: 'redis:7-alpine', port: 6379 },
  { name: 'Apache Kafka', image: 'bitnami/kafka:3.7.2', port: 9092 },
  { name: 'OpenSearch', image: 'opensearch:2.17.1', port: 9200 },
  { name: 'Prometheus', image: 'prom/prometheus', port: 9090 },
  { name: 'Grafana', image: 'grafana/grafana', port: 3000 },
];

function App() {
  const [services, setServices] = useState<ServiceHealth[]>([]);
  const [gateway, setGateway] = useState<'UP' | 'DOWN' | 'unknown'>('unknown');
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      try {
        const [svcRes, gwRes] = await Promise.all([
          fetch('/api/system/services'),
          fetch('/actuator/health'),
        ]);
        const svcJson = (await svcRes.json()) as ServicesResponse;
        const gwJson = (await gwRes.json()) as { status?: string };
        if (!cancelled) {
          setServices(svcJson?.services ?? []);
          setGateway(gwJson?.status === 'UP' ? 'UP' : 'unknown');
        }
      } catch {
        if (!cancelled) setGateway('unknown');
      } finally {
        if (!cancelled) setLoading(false);
      }
      setTimeout(load, 15000); // poll live
    };
    load();
    return () => {
      cancelled = true;
    };
  }, []);

  const up = services.filter((s) => s.status === 'UP').length;
  const down = services.length - up;

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <img src="/sentinelx.svg" alt="SentinelX" width="34" height="34" />
          <div>
            <h1>SentinelX</h1>
            <span>Enterprise Security &amp; Risk Monitoring Platform</span>
          </div>
        </div>
        <div className="gateway-pill" data-state={gateway}>
          Gateway: <strong>{gateway}</strong>
        </div>
      </header>

      <main className="content">
        <section className="kpis">
          <div className="kpi">
            <span className="label">Backend Services</span>
            <span className="value">{services.length}</span>
          </div>
          <div className="kpi">
            <span className="label">Healthy</span>
            <span className="value good">{up}</span>
          </div>
          <div className="kpi">
            <span className="label">Unreachable</span>
            <span className="value bad">{down}</span>
          </div>
        </section>

        <section className="panel">
          <h2>Microservice Health</h2>
          {loading ? (
            <p className="muted">Probing services…</p>
          ) : (
            <div className="grid">
              {services.map((s) => (
                <div key={s.id} className={`card ${(s.status === 'UP' ? 'up' : 'err')}`}>
                  <span className="dot" />
                  <div>
                    <div className="name">{s.name}</div>
                    <div className="meta">{s.id} · {s.url}</div>
                    <div className="meta">status: <strong>{s.status}</strong></div>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        <section className="panel">
          <h2>Infrastructure (Phase 1)</h2>
          <div className="grid">
            {INFRA.map((inf) => (
              <div key={inf.name} className="card">
                <span className="dot" />
                <div>
                  <div className="name">{inf.name}</div>
                  <div className="meta">{inf.image} · host :{inf.port}</div>
                </div>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="footer">SentinelX · Phase 1 scaffold · synthetic security environment</footer>
    </div>
  );
}

export default App;
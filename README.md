# SentinelX — Enterprise Security & Risk Monitoring Platform

SentinelX is a portfolio-grade, **fully synthetic** security-monitoring platform.
It simulates auth, payment, retail and network activity and runs a **real
end-to-end detection pipeline**: simulation → business events → Kafka →
security events → detection → risk scoring → alerts → database → SOC dashboard.

> **Isolation**: SentinelX never connects to real banks, payment systems,
> customer accounts or external attack targets. Everything runs inside a
> private Docker network and is synthetic by design.

## Status

| Phase | Scope | State |
|-------|-------|-------|
| **1** | Repository scaffolding · minimal Spring Boot/React apps · Docker topology (postgres, redis, kafka, opensearch, prometheus, grafana) · health endpoints · healthchecks · structured logging · `docker compose up --build` | ✅ complete |
| 2+ | Business services / real event pipeline / SOC dashboard / simulation center | planned |

## Tech Stack

- **Backend**: Java 21 · Spring Boot 3.x · Spring Cloud Gateway · Maven
- **Real-time/messaging**: Kafka (KRaft), Redis, PostgreSQL, OpenSearch
- **Frontend**: React · TypeScript · Vite
- **Observing**: Prometheus, Grafana, Spring Boot Actuator
- **Deployment**: Docker, Docker Compose

## Repository Layout

```
backend/
  api-gateway/            # Spring Cloud Gateway + system health aggregator (:8080)
  auth-service/           # minimal servlet service (:8081)
  payment-service/        # (:8082)
  retail-service/         # (:8083)
  security-event-service/ # (:8084)
  detection-engine/       # (:8085)
  risk-engine/            # (:8086)
  alert-service/          # (:8087)
  simulation-service/     # (:8088)
frontend/                 # React + TypeScript + Vite SOC console (:8090)
infrastructure/
  prometheus/             # scrape config
  grafana/                # datasource + dashboard provisioning
docker-compose.yml        # full topology
.env.example              # config template
docs/                     # phase reports, architecture
scripts/                  # scaffolding + verify helpers
```

## Quick Start

```bash
cp .env.example .env          # optional; defaults are sane
docker compose config         # validate compose
docker compose up --build     # build images + start topology
```

Then open:

| Component | URL |
|-----------|-----|
| SentinelX SOC console | http://localhost:8090 |
| API Gateway | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| OpenSearch | http://localhost:9200 |

## Backend dev (out-of-container)

```bash
cd backend
mvn clean package -DskipTests
java -jar auth-service/target/*.jar
```

## Frontend dev

```bash
cd frontend
npm install
npm run dev           # http://localhost:5173
```

## Observability

Every service exposes `/actuator/health` and `/actuator/prometheus`, uses a
structured console log format, and ships a Docker healthcheck. Prometheus
scrapes all services; Grafana has a provisioned **SentinelX – System Health**
dashboard.

## Environment

Copy `.env.example` to `.env` and adjust ports/credentials. Host port bindings
are chosen to avoid collisions (e.g. Postgres on **5434**, frontend on **8090**).

## Next phases
1. Kafka/PG persistence + real simulation pipeline (simulation → security events → detection → risk → alerts).
2. SOC dashboard pages; Simulation Center; SSE live updates.
3. Detection/risk rule engines (explainable rules, risk levels, actions).
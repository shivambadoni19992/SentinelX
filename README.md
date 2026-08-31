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

## API Gateway (Spring Cloud Gateway)

The gateway is the single entry point for the SOC console and every client —
**the frontend communicates only through the gateway** (the Vite dev server
proxies `/api` to `api-gateway:8080`).

Routes (all rate-limited in Redis):

| Route | Upstream |
|-------|----------|
| `/api/auth/**` | `auth-service:8081` |
| `/api/payments/**` | `payment-service:8082` |
| `/api/orders/**` | `retail-service:8083` |
| `/api/security/**` | `security-event-service:8084` |
| `/api/detections/**` | `detection-engine:8085` |
| `/api/risk/**` | `risk-engine:8086` |
| `/api/alerts/**` | `alert-service:8087` |
| `/api/simulations/**` | `simulation-service:8088` |

Gateway capabilities:

- **JWT validation** — every protected route requires a `Bearer` token issued
  by the auth-service (same `sentinelx.jwt.*` secret/issuer). `login`, the
  actuator, and `/api/system/**` are public. Authenticated user id/role are
  forwarded upstream as `X-Auth-User-Id` / `X-Auth-Role`.
- **Redis rate limiting** — token-bucket `RequestRateLimiter` per route, keyed
  by user id for authenticated calls and by client IP otherwise. Tune via
  `RATE_LIMIT_REPLENISH` / `RATE_LIMIT_BURST`.
- **Correlation ID** — a `X-Correlation-Id` is generated or propagated,
  forwarded upstream, echoed on responses, and included in request logs.
- **Security headers** — `X-Content-Type-Options`, `X-Frame-Options`,
  `X-XSS-Protection`, `Strict-Transport-Security`, `Referrer-Policy`,
  `Permissions-Policy` on every response.
- **Request logging** — structured `method path status duration_ms
  correlationId userId` lines.

Actuator exposes `health,info,metrics,prometheus,gateway`
(`/actuator/gateway/routes` lists the live route table).

### End-to-end verification (Docker Compose)

```bash
docker compose up -d --build
./scripts/e2e-gateway.sh        # React -> gateway -> Java services
```

`scripts/e2e-gateway.sh` logs in through the gateway, calls `/api/auth/me`,
asserts correlation-id + security headers and the 401 negative cases, then
repeats login and `/me` **through the frontend proxy** (`:8090`) to prove the
full React → Gateway → Java path.

## Authentication (auth-service)

Stateless JWT authentication powered by Spring Security.

- `POST /api/auth/login` — exchange `{ username, password }` for a signed JWT
  (username or email accepted). Passwords are verified with **BCrypt**; only
  hashes are ever stored, and no password material is returned.
- `GET /api/auth/me` — return the current user for a `Bearer <token>`.
- Role-based authorization via `@PreAuthorize` (e.g. `/api/auth/admin/**`
  requires `ADMIN`).
- Account statuses enforced: `ACTIVE` (full access), `MONITORED` (allowed,
  flagged for review), `BLOCKED` (authentication refused).
- Every successful login records a row in the `whoami.sessions` audit table
  (token stored as a SHA-256 hash, never the raw JWT).

Endpoints are reached through the gateway at `/api/auth/**`; the React SOC
console ships a login page that stores the token in `localStorage`.

### Development accounts (seeded automatically, non-prod only)

On startup (profiles other than `prod`) the auth-service seeds the accounts
below. Passwords are BCrypt-hashed at runtime and never written to source.

| Username | Role | Status |
|----------|------------|----------|
| admin    | ADMIN      | ACTIVE |
| analyst  | SOC_ANALYST| ACTIVE |
| engineer | SECURITY_ENGINEER | ACTIVE |
| support  | SUPPORT    | ACTIVE |
| auditor  | AUDITOR    | ACTIVE |
| monitored| SOC_ANALYST| MONITORED |
| blocked  | SOC_ANALYST| BLOCKED |

All development accounts share the dev password `SentinelX!Dev1`. Override the
production secret with `SENTINELX_JWT_SECRET` (must be ≥ 32 bytes); the dev-only
default is unsuitable for any non-development deployment.

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
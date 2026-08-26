# SentinelX — Phase 1 Report

## CURRENT PHASE
**Phase 1** — Repository scaffolding, minimal runnable microservices, Docker
topology, observability, and connectivity verification. No business logic
implemented by design.

## IMPLEMENTED
- Monorepo layout `backend/`, `frontend/`, `infrastructure/`, `docs/`, `scripts/`.
- 9 minimal Spring Boot 3 / Java 21 microservices (api-gateway + 8 workers).
- Spring Cloud Gateway with routing + an infrastructure proxy to the backend.
- React + TypeScript + Vite POS dashboard shell.
- Docker Compose topology: postgres, redis, kafka (KRaft), opensearch,
  prometheus, grafana, frontend, and all Java services.
- Prometheus scrape config + Grafana provisioning (datasource + dashboard).
- Structured per-service logging (delimited pipeline, no extra deps).

## FILES CREATED
- `pom.xml` (backend aggregator) + 9 child service POMs
- `backend/Dockerfile` (shared, parameterized by `SERVICE` build arg)
- `backend/<service>/src/.../{Application}.java`, `application.yml`, `logback-spring.xml` (×9)
- `backend/api-gateway/src/.../SystemHealthController.java` (actuator aggregator)
- `frontend/` package.json, vite.config.ts, tsconfig.json, index.html,
  public/sentinelx.svg, src/main.tsx, src/App.tsx, src/App.css, src/index.css, Dockerfile, .dockerignore
- `infrastructure/prometheus/prometheus.yml`
- `infrastructure/grafana/provisioning/datasources/prometheus.yml`,
  `infrastructure/grafana/provisioning/dashboards/dashboards.yml`,
  `infrastructure/grafana/dashboards/health.json`
- `docker-compose.yml`, `.env.example`, `.gitignore`, `README.md`
- `docs/architecture.md`, `docs/phase-1-report.md`
- `scripts/generate-service.sh`, `scripts/start.sh`, `scripts/verify.sh`

## FILES MODIFIED
- `.gitignore` (build artifacts, env, node, data)

## DATABASE CHANGES
- None yet (Postgres included in the topology; tables deferred to Phase 2 when
  persistence is added).

## KAFKA CHANGES
- None yet (Kafka broker running in KRaft mode and wired into the network;
  topics are deferred to the Phase 2 event pipeline).

## API ENDPOINTS
- `GET /actuator/health` (all services + gateway)
- `GET /actuator/info` (all services)
- `GET /actuator/prometheus` (all services)
- `GET /api/system/services` (gateway; live health of all 9 microservices)

## UI CHANGES
- Branded "SentinelX" shell; Overview page that live-probes the gateway +
  backend health through `/api/system/services` (polls every 15s).
- Infrastructure cards for postgres/redis/kafka/opensearch/prometheus/grafana.

## SIMULATION CHANGES
- None in this phase (Simulation Center is a Phase 3 milestone).

## TESTS
- TypeScript build validated (`vite build`) — see build logs.
- Backend validated via `mvn clean package -DskipTests` — all 9 modules built.
- Actual `docker compose build`/`up` verification steps are reported in
  "DOCKER STATUS".

## DOCKER STATUS
- `docker compose config` -> **valid** (COMPOSE-CONFIG-OK).
- `docker compose build` -> in progress / see `verify.sh` and run log.
- `docker compose up –-build` -> to be executed; see run log for startup
  results of each container and healthcheck outcomes.

## DEMO STEPS
1. `cp .env.example .env` (optional)
2. `docker compose up --build -d`
3. Open http://localhost:8090 — Overview shows live service health.
4. `curl http://localhost:8080/actuator/health`, `/api/system/services`
5. Grafana http://localhost:3000 (admin/admin) → "SentinelX – System Health".

## KNOWN ISSUES
- Health probes hit each backend from the gateway; one slow/unresponsive
  container will show `DOWN` but not block startup (depends_on uses
  `service_started` for non-critical infra).
- Host ports (Postgres 5434, frontend 8090) differ from default GET 5432/3000)
  to avoid collisions on the host machine.

## NEXT PHASE
- Phase 2: Kafka topics + PostgreSQL (Flyway) persistence; simulation →
  business-event → security-event producer/consumer skeleton; SSE for live
  updates; first SOC dashboard data endpoints.
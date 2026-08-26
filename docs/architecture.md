# SentinelX — Architecture Overview

## Topology

```
                             ┌─────────────────────────────┐
   Browser ──:8090──► React/Vite (SOC Console)            │
                             │  /api + /actuator (proxy)    │
                             └───────────────┬─────────────┘
                                             │
                                   ┌─────────▼─────────┐
                                   │ api-gateway :8080  │  Spring Cloud Gateway
                                   └──┬───┬───┬───┬─────┘
        ┌──────────────┬──────────────┤   │   │   └──────────────┐
        ▼              ▼              ▼   │   ▼                  ▼
  auth-service   payment-service  retail  │  security-event  ... (workers 8081–8088)
     :8081           :8082         :8083  │     :8084  detection :8085 risk :8086
                                          │     alert :8087 simulation :8088
                                          ▼
                              Observability (actuator/prometheus)
                                          │
                       ┌──────────────────▼───────────────────┐
                       │ prometheus :9090 ──► grafana :3000    │
                       └──────────────────────────────────────┘

   Infrastructure: postgres:5434(mapped) · redis:6379 · kafka:9092 ·
                   opensearch:9200 · prometheus:9090 · grafana:3000
```

## Networking

All containers join a single `sentinelx-net` bridge network and resolve each
other by service name (e.g. `api-gateway`, `auth-service`). Services expose
their Spring Boot Actuator on the same port as the app for health/healthchecks.

## Key design decisions

- **Single shared `backend/Dockerfile`** parameterized by a `SERVICE` build
  arg → one consistent build path for all microservices.
- **`depends_on` with health conditions** so Java services wait for
  Postgres/Redis/Kafka before starting; the rest start opportunistically.
- **Gateway aggregates Actuator health** (`/api/system/services`) so the UI
  and operators see the whole fleet through one endpoint.
- **KRaft Kafka** (no ZooKeeper) keeps the topology small.
- **Per-service structured logging** and healthchecks on every container.

## Planned evolution (later phases)

1. Kafka topics + Postgres/Flyway persistence + real event pipeline.
2. SOC dashboard pages and Simulation Center with SSE live updates.
3. Explainable detection rules + risk scoring engine.
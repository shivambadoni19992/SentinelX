# SentinelX Docker Deployment

## Quick Start

The only required command to start the entire platform:

```bash
docker compose up --build
```

## Architecture

### Infrastructure Services

| Service | Port | Description |
|---------|------|-------------|
| PostgreSQL | 5432 | Primary database |
| Redis | 6379 | Cache and session store |
| Zookeeper | 2181 | Kafka coordination |
| Kafka | 9092/29092 | Message broker |
| OpenSearch | 9200 | Log and event storage |
| OpenSearch Dashboards | 5601 | Log visualization |
| Prometheus | 9090 | Metrics collection |
| Grafana | 3001 | Metrics visualization |

### Application Services

| Service | Port | Description |
|---------|------|-------------|
| Gateway | 8080 | API gateway and load balancer |
| Auth | 8081 | Authentication and authorization |
| Payment | 8082 | Payment processing |
| Retail | 8083 | Retail commerce |
| Security Event | 8084 | Security event collection |
| Detection | 8085 | Threat detection engine |
| Risk | 8086 | Risk assessment |
| Alert | 8087 | Alert management |
| Simulation | 8088 | Security simulation |
| Frontend | 3000 | Web UI (Nginx) |

## Access Points

- **Frontend**: http://localhost:3000
- **API Gateway**: http://localhost:8080
- **Grafana**: http://localhost:3001 (admin/admin)
- **Prometheus**: http://localhost:9090
- **OpenSearch Dashboards**: http://localhost:5601

## Data Flow

```
Frontend → Gateway → Services → Kafka → Detection → Risk → Alerts
                                              ↓
                                         Database
                                              ↓
                                        OpenSearch
                                              ↓
                                      Grafana Dashboard
```

## Health Checks

All services have health checks configured. Docker Compose will wait for
infrastructure services to be healthy before starting dependent services.

Check service health:
```bash
docker compose ps
docker compose logs <service-name>
```

## Volumes

Data is persisted in named volumes:
- `sentinelx-postgres-data` - PostgreSQL data
- `sentinelx-redis-data` - Redis data
- `sentinelx-opensearch-data` - OpenSearch indices
- `sentinelx-prometheus-data` - Prometheus metrics
- `sentinelx-grafana-data` - Grafana dashboards

## Networking

All services are connected via the `sentinelx-network` bridge network.
Service discovery uses Docker DNS (service names as hostnames).

## Metrics

Prometheus scrapes metrics from all services every 15 seconds.
Metrics are available at `/metrics` endpoint on each service.

### Tracked Metrics

- `sentinelx_http_requests_total` - HTTP request count
- `sentinelx_http_request_duration_seconds` - Request latency
- `sentinelx_http_errors_total` - HTTP error count
- `sentinelx_kafka_messages_total` - Kafka message count
- `sentinelx_security_events_total` - Security events
- `sentinelx_detections_total` - Threat detections
- `sentinelx_risk_decisions_total` - Risk decisions
- `sentinelx_alerts_total` - Generated alerts
- `sentinelx_blocked_users_total` - Blocked users
- `sentinelx_held_transactions_total` - Held transactions
- `sentinelx_simulation_events_total` - Simulation events
- `sentinelx_simulation_duration_seconds` - Simulation duration
- `sentinelx_service_up` - Service health status

## Stopping

```bash
docker compose down
```

To remove all data:
```bash
docker compose down -v
```

## Troubleshooting

1. **Services not starting**: Check logs with `docker compose logs <service>`
2. **Port conflicts**: Ensure ports 3000, 3001, 5432, 6379, 8080-8088, 9090, 9092, 9200, 5601 are free
3. **Health check failures**: Increase resources or check service logs
4. **Kafka connection issues**: Ensure Zookeeper is healthy first

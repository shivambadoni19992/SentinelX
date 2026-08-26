#!/usr/bin/env bash
# SentinelX — post-start health checks.
set -uo pipefail

echo "==> Gateway health:"
curl -sS -o /dev/null -w "  http gateway        -> %{http_code}\n" http://localhost:8080/actuator/health || echo "  gateway DOWN"

echo "==> Service aggregation (each backend actuator):"
curl -sS http://localhost:8080/api/system/services | python3 -m json.tool || echo "  /api/system/services failed"

echo "==> Key infra endpoints:"
for spec in \
  "prometheus 9090 /-/healthy" \
  "grafana 3000 /api/health" \
  "opensearch 9200 /_cluster/health"; do
  name="${spec%% *}"; rest="${spec#* }"; port="${rest%% *}"; path="${rest#* }"
  code=$(curl -sS -o /dev/null -w "%{http_code}" "http://localhost:${port}${path}" 2>/dev/null || echo 000)
  printf "  %-12s :%-5s -> %s\n" "$name" "$port" "$code"
done

echo "==> Container summary:"
docker compose ps
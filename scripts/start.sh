#!/usr/bin/env bash
# SentinelX — bring the whole platform up.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
  echo "No .env found; using docker-compose defaults (see .env.example)."
fi

echo "==> Building and starting SentinelX..."
docker compose up --build -d

echo "==> Waiting for healthchecks..."
sleep 20

docker compose ps
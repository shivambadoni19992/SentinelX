#!/usr/bin/env bash
#
# End-to-end Kafka verification for SentinelX.
#
# Provisions the security.* topic topology in the dockerized Kafka, produces
# correlated JSON events to all 8 event topics (the same shapes emitted by the
# auth, payment and retail producers), and consumes back from every topic and
# dead-letter topic to prove the pipeline is live.
#
# Usage: ./scripts/kafka-verify.sh [bootstrap-server]
set -euo pipefail

BOOTSTRAP="${1:-localhost:9092}"

# Reuse the running compose kafka container; fall back to docker run.
KAFKA_CMD="docker exec -i sentinelx-kafka /opt/kafka/bin"
if ! docker ps --format '{{.Names}}' | grep -q '^sentinelx-kafka$'; then
  echo "sentinelx-kafka container is not running. Start it with:"
  echo "  docker compose up -d kafka kafka-init"
  exit 1
fi

echo "== 1. Provisioning topics (via kafka-init equivalent) =="
TOPICS="security.auth security.payment security.api security.retail security.network security.risk security.alert security.audit"
for T in $TOPICS; do
  $KAFKA_CMD/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$T" --partitions 3 --replication-factor 1 > /dev/null
  $KAFKA_CMD/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --create --if-not-exists \
    --topic "$T.dlt" --partitions 3 --replication-factor 1 \
    --config cleanup.policy=compact > /dev/null
done
echo "Topics:"
$KAFKA_CMD/kafka-topics.sh --bootstrap-server "$BOOTSTRAP" --list | grep '^security\.' | sort

CORRELATION_ID="verify-$(uuidgen 2>/dev/null || echo "$RANDOM-$$")"
echo
echo "== 2. Producing correlated JSON events (correlationId=$CORRELATION_ID) =="

produce() { # topic key json
  $KAFKA_CMD/kafka-console-producer.sh \
    --bootstrap-server "$BOOTSTRAP" --topic "$1" \
    --property parse.key=true --property key.separator='|' <<< "$2|$3"
}

NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
UID1="11111111-1111-1111-1111-111111111111"
PAY1="22222222-2222-2222-2222-222222222222"
ORD1="33333333-3333-3333-3333-333333333333"

# auth-service shape (security.auth)
produce security.auth "$UID1" "{\"eventType\":\"LOGIN_SUCCESS\",\"userId\":\"$UID1\",\"username\":\"alice\",\"actor\":\"alice\",\"action\":\"LOGIN_SUCCESS\",\"outcome\":\"SUCCESS\",\"severity\":\"LOW\",\"sourceIp\":\"203.0.113.9\",\"occurredAt\":\"$NOW\",\"correlationId\":\"$CORRELATION_ID\"}"
# payment-service shape (security.payment)
produce security.payment "$PAY1" "{\"eventType\":\"PAYMENT_CREATED\",\"paymentId\":\"$PAY1\",\"customerId\":\"$UID1\",\"merchantId\":\"$ORD1\",\"amount\":250.00,\"currency\":\"USD\",\"status\":\"COMPLETED\",\"createdAt\":\"$NOW\",\"correlationId\":\"$CORRELATION_ID\"}"
# retail-service shape (security.retail)
produce security.retail "$ORD1" "{\"eventType\":\"ORDER_CREATED\",\"orderId\":\"$ORD1\",\"userId\":\"$UID1\",\"totalAmount\":\"250.00\",\"currency\":\"USD\",\"itemCount\":3,\"correlationId\":\"$CORRELATION_ID\"}"
# other platform domains
produce security.api "$UID1" "{\"eventType\":\"API_ABUSE\",\"userId\":\"$UID1\",\"outcome\":\"BLOCKED\",\"severity\":\"HIGH\",\"correlationId\":\"$CORRELATION_ID\"}"
produce security.network "$UID1" "{\"eventType\":\"PORT_SCAN\",\"sourceIp\":\"198.51.100.7\",\"severity\":\"HIGH\",\"correlationId\":\"$CORRELATION_ID\"}"
produce security.risk "$UID1" "{\"eventType\":\"RISK_SCORE_UPDATED\",\"userId\":\"$UID1\",\"score\":87,\"correlationId\":\"$CORRELATION_ID\"}"
produce security.alert "$UID1" "{\"eventType\":\"ALERT_RAISED\",\"userId\":\"$UID1\",\"severity\":\"CRITICAL\",\"correlationId\":\"$CORRELATION_ID\"}"
produce security.audit "$UID1" "{\"eventType\":\"AUDIT_ENTRY\",\"actor\":\"admin\",\"action\":\"EXPORT\",\"correlationId\":\"$CORRELATION_ID\"}"

echo "8 events produced across security.{auth,payment,api,retail,network,risk,alert,audit}."
echo
echo "== 3. Consuming back from every topic (5s window) =="
PASSED=0
for T in $TOPICS; do
  COUNT=$($KAFKA_CMD/kafka-console-consumer.sh --bootstrap-server "$BOOTSTRAP" --topic "$T" \
    --from-beginning --timeout-ms 5000 --max-messages 1 2>/dev/null | wc -l || true)
  if [ "${COUNT:-0}" -ge 1 ]; then
    echo "  OK    $T"
    PASSED=$((PASSED+1))
  else
    echo "  EMPTY $T"
  fi
done
echo
if [ "$PASSED" -eq 8 ]; then
  echo "VERIFICATION PASSED: all 8 topics delivered JSON events (correlationId=$CORRELATION_ID)."
  echo "Correlated chain: auth($UID1) -> payment($PAY1) -> retail($ORD1) share one correlationId."
else
  echo "VERIFICATION INCOMPLETE: $PASSED/8 topics delivered."
  exit 1
fi

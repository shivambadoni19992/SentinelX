#!/usr/bin/env bash
# SentinelX — end-to-end gateway verification.
#
# Proves the mandated request path through Docker Compose:
#
#   React (browser / frontend proxy) --> API Gateway --> Java services
#
# Checks:
#   1. gateway actuator health
#   2. POST /api/auth/login through the gateway   -> expect 200 + JWT
#   3. GET  /api/auth/me through the gateway      -> expect 200 + user role
#   4. correlation-id + security headers present on responses
#   5. /api/auth/me without token                 -> expect 401
#   6. /api/auth/me with invalid token            -> expect 401
#   7. full path via the frontend dev-server proxy (:8090) -> login + me
set -uo pipefail

GATEWAY="${GATEWAY_URL:-http://localhost:8080}"
FRONTEND="${FRONTEND_URL:-http://localhost:8090}"
USERNAME="${TEST_USER:-analyst}"
PASSWORD="${TEST_PASSWORD:-SentinelX!Dev1}"

PASS=0
FAIL=0

note()  { printf '  %s\n' "$1"; }
ok()    { printf '  \033[32mPASS\033[0m  %s\n' "$1"; PASS=$((PASS+1)); }
bad()   { printf '  \033[31mFAIL\033[0m  %s\n' "$1"; FAIL=$((FAIL+1)); }

check_eq() { # $1 label  $2 actual  $3 expected
  if [ "$2" = "$3" ]; then ok "$1 (got $2)"; else bad "$1 (got $2, expected $3)"; fi
}

echo "==> Gateway health ($GATEWAY)..."
gw_code=$(curl -sS -o /tmp/gw_health.json -w '%{http_code}' "$GATEWAY/actuator/health" 2>/dev/null || echo 000)
check_eq "gateway /actuator/health HTTP" "$gw_code" "200"

echo ""
echo "==> Login through gateway..."
login=$(curl -sS -w '\n%{http_code}' -X POST "$GATEWAY/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" 2>/dev/null)
login_code=$(printf '%s\n' "$login" | tail -n1)
login_body=$(printf '%s\n' "$login" | sed '$d')

if [ "$login_code" = "200" ]; then
  ok "login HTTP $login_code"
  token=$(printf '%s\n' "$login_body" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])' 2>/dev/null)
  if [ -n "$token" ]; then
    note "  token acquired (len=${#token})"
  else
    bad "could not parse token from login response"; token=""
  fi
else
  bad "login HTTP $login_code"; token=""
fi

echo ""
echo "==> /api/auth/me through gateway..."
if [ -n "$token" ]; then
  me_resp=$(curl -sS -D /tmp/gw_me_headers -o /tmp/gw_me_body -w '%{http_code}' \
    "$GATEWAY/api/auth/me" -H "Authorization: Bearer $token")
  check_eq "/api/auth/me HTTP" "$me_resp" "200"
  me_role=$(python3 -c 'import sys,json; print(json.load(open("/tmp/gw_me_body"))["role"])' 2>/dev/null)
  check_eq "/api/auth/me role" "$me_role" "SOC_ANALYST"

  echo ""
  echo "==> Headers on /api/auth/me..."
  if grep -qi '^X-Correlation-Id:' /tmp/gw_me_headers; then
    cid=$(grep -i '^X-Correlation-Id:' /tmp/gw_me_headers | tr -d '\r' | awk '{print $2}')
    ok "X-Correlation-Id present ($cid)"
  else
    bad "X-Correlation-Id missing"
  fi
  grep -qi '^X-Content-Type-Options: nosniff' /tmp/gw_me_headers && ok "X-Content-Type-Options: nosniff" || bad "X-Content-Type-Options missing"
  grep -qi '^X-Frame-Options: DENY' /tmp/gw_me_headers && ok "X-Frame-Options: DENY" || bad "X-Frame-Options missing"
  grep -qi '^Strict-Transport-Security:' /tmp/gw_me_headers && ok "Strict-Transport-Security present" || bad "Strict-Transport-Security missing"
fi

echo ""
echo "==> Negative cases..."
n1=$(curl -sS -o /dev/null -w '%{http_code}' "$GATEWAY/api/auth/me")
check_eq "/api/auth/me with no token" "$n1" "401"
n2=$(curl -sS -o /dev/null -w '%{http_code}' "$GATEWAY/api/auth/me" -H "Authorization: Bearer garbage.token.here")
check_eq "/api/auth/me with invalid token" "$n2" "401"

echo ""
echo "==> Full path via frontend proxy ($FRONTEND) -> gateway -> auth-service..."
f_login=$(curl -sS -w '\n%{http_code}' -X POST "$FRONTEND/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" 2>/dev/null)
f_code=$(printf '%s\n' "$f_login" | tail -n1)
f_body=$(printf '%s\n' "$f_login" | sed '$d')
if [ "$f_code" = "200" ]; then
  ok "frontend-proxy login HTTP $f_code"
  f_token=$(printf '%s\n' "$f_body" | python3 -c 'import sys,json; print(json.load(sys.stdin)["token"])' 2>/dev/null)
  if [ -n "$f_token" ]; then
    f_me=$(curl -sS -o /dev/null -w '%{http_code}' "$FRONTEND/api/auth/me" -H "Authorization: Bearer $f_token")
    check_eq "frontend-proxy /api/auth/me" "$f_me" "200"
  else
    bad "could not parse token from frontend login"
  fi
else
  bad "frontend-proxy login HTTP $f_code"
fi

echo ""
echo "=================================================="
echo "RESULT: PASS=$PASS FAIL=$FAIL"
echo "=================================================="
[ "$FAIL" -eq 0 ]
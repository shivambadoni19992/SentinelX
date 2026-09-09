# SentinelX End-to-End Tests

## Test Suite Overview

This test suite validates the complete SentinelX platform functionality including
attack detection, security controls, simulation management, and Kafka integration.

## Test Categories

### Attack Scenario Tests (e2e/attack-tests.ts)

Tests the full attack → detection → risk → alert → action flow:

| Test | Expected Outcome |
|------|------------------|
| Normal Traffic | No false positives |
| Brute Force | Detection → Risk → Alert |
| Account Takeover | CRITICAL alert → Account BLOCKED |
| Payment Fraud | Detection → Risk → Alert → Payment HELD |
| Transaction Velocity | Detection → Alert |
| API Abuse | Rate limiting → Alert |
| Bot Activity | Detection → Alert |
| Network Scan | Detection → Risk → Alert |
| Data Access | Detection → Alert |
| Mixed Attack | Multiple detections → Campaign ID |

### Security Tests (security/security-tests.ts)

| Test | Description |
|------|-------------|
| JWT | Token generation, validation, rejection of invalid tokens |
| RBAC | Role-based access control enforcement |
| Data Masking | PII protection (device ID, IP address) |
| Audit Logging | Action logging and traceability |

### Simulation Tests (simulation/simulation-tests.ts)

| Test | Description |
|------|-------------|
| Event Limit | Enforce maximum event count |
| Duration Limit | Enforce maximum simulation duration |
| Cancellation | Stop running simulation |
| Concurrent Limit | Handle multiple simultaneous simulations |
| SSE Stream | Real-time progress streaming |

### Kafka Integration Tests (integration/kafka-tests.ts)

| Test | Description |
|------|-------------|
| Message Production | Produce Kafka messages |
| Message Consumption | Consume and process messages |
| End-to-End Flow | Full event processing pipeline |

## Running Tests

### Prerequisites

1. Start the platform:
```bash
docker compose up --build
```

2. Wait for all services to be healthy:
```bash
docker compose ps
```

### Run All Tests

```bash
cd tests
npm install
npm test
```

### Run Specific Test Categories

```bash
# Security tests only
npm run test:security

# Simulation tests only
npm run test:simulation

# Attack scenario tests only
npm run test:attacks

# Kafka integration tests only
npm run test:kafka
```

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| API_BASE_URL | http://localhost:8080 | Gateway URL |
| AUTH_URL | http://localhost:8081 | Auth service URL |
| PAYMENT_URL | http://localhost:8082 | Payment service URL |
| SIMULATION_URL | http://localhost:8088 | Simulation service URL |

## Test Output

```
╔════════════════════════════════════════════════════════════╗
║        SentinelX End-to-End Test Suite                    ║
╚════════════════════════════════════════════════════════════╝

── Security Tests ──
── Simulation Tests ──
── Kafka Integration Tests ──
── Attack Scenario Tests ──

=== Test Results ===
Total: 25, Passed: 23, Failed: 2

✓ PASS - JWT - Token generation and validation (150ms)
✓ PASS - RBAC - Role-based access control (200ms)
✓ PASS - Brute Force → Detection → Risk → Alert (45000ms)
✗ FAIL - Payment Fraud → Detection → Risk → Alert → Payment HELD (32000ms)
  Error: No payments were held
```

## Verification Matrix

### Attack → Outcome Verification

| Attack | Detection | Risk | Alert | Action |
|--------|-----------|------|-------|--------|
| Brute Force | ✓ | ✓ | ✓ | - |
| Account Takeover | ✓ | ✓ | CRITICAL | BLOCKED |
| Payment Fraud | ✓ | ✓ | ✓ | HELD |
| Transaction Velocity | ✓ | - | ✓ | - |
| API Abuse | ✓ | - | ✓ | Rate Limited |
| Bot Activity | ✓ | - | ✓ | - |
| Network Scan | ✓ | ✓ | ✓ | - |
| Data Access | ✓ | - | ✓ | - |
| Mixed Attack | ✓ (multiple) | ✓ | ✓ | Campaign ID |

### Security Controls

| Control | Test | Expected |
|---------|------|----------|
| JWT | Token validation | Valid token accepted, invalid rejected |
| RBAC | Role enforcement | Admin access granted, customer denied |
| Masking | PII protection | Device ID and IP masked |
| Audit | Action logging | All actions logged with correlation ID |

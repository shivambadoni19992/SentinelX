// Demo (synthetic) dataset. Produced only when a live SentinelX endpoint is
// unavailable or unrouted, so the SOC console always renders. Every demo value
// maps 1:1 to the real API contracts in ./types.

import type {
  AuditLog,
  Cart,
  Order,
  Payment,
  Product,
  RiskDecision,
    SecurityAlert,
  SecurityEvent,
  SimulationProgress,
  SimulationRun,
  User,
} from './types';

const now = Date.now();
const MIN = 60_000;
let counter = 0;
export function uid(prefix = 'id'): string {
  counter += 1;
  return `${prefix}-${Date.now().toString(36)}-${counter}`;
}
function minutesAgo(mins: number): string {
  return new Date(now - mins * MIN).toISOString();
}

const USERS: User[] = [
  { id: uid('u'), username: 'admin', email: 'admin@sentinelx.io', role: 'ADMIN', accountStatus: 'ACTIVE', createdAt: minutesAgo(60 * 24 * 120) },
  { id: uid('u'), username: 'analyst', email: 'analyst@sentinelx.io', role: 'SOC_ANALYST', accountStatus: 'ACTIVE', createdAt: minutesAgo(60 * 24 * 90) },
  { id: uid('u'), username: 'm.reyes', email: 'm.reyes@partner.io', role: 'CUSTOMER', accountStatus: 'BLOCKED', createdAt: minutesAgo(60 * 24 * 45) },
  { id: uid('u'), username: 'k.okafor', email: 'k.okafor@acme.com', role: 'CUSTOMER', accountStatus: 'MONITORED', createdAt: minutesAgo(60 * 24 * 30) },
  { id: uid('u'), username: 'j.petrov', email: 'j.petrov@nova.biz', role: 'CUSTOMER', accountStatus: 'ACTIVE', createdAt: minutesAgo(60 * 24 * 22) },
  { id: uid('u'), username: 's.lambert', email: 's.lambert@acme.com', role: 'CUSTOMER', accountStatus: 'MONITORED', createdAt: minutesAgo(60 * 24 * 12) },
  { id: uid('u'), username: 't.chang', email: 't.chang@warehouse.io', role: 'CUSTOMER', accountStatus: 'BLOCKED', createdAt: minutesAgo(60 * 24 * 8) },
  { id: uid('u'), username: 'auditor', email: 'auditor@sentinelx.io', role: 'AUDITOR', accountStatus: 'ACTIVE', createdAt: minutesAgo(60 * 24 * 60) },
];

export const mockUsers: User[] = USERS;

export const mockAlerts: SecurityAlert[] = [
  { id: uid('a'), title: 'Brute-force login spike', description: '20+ failed credential attempts from a single source.', severity: 'CRITICAL', entityType: 'USER', entityId: USERS[4].id, status: 'OPEN', assignedTo: 'analyst', triggeredAt: minutesAgo(4) },
  { id: uid('a'), title: 'Unusual geolocation login', description: 'Login from a new region minutes after a prior session.', severity: 'HIGH', entityType: 'USER', entityId: USERS[5].id, status: 'INVESTIGATING', assignedTo: 'analyst', triggeredAt: minutesAgo(27) },
  { id: uid('a'), title: 'Card-not-present anomaly', description: 'High-value transaction from a flagged device.', severity: 'CRITICAL', entityType: 'PAYMENT', entityId: uid('p'), status: 'OPEN', triggeredAt: minutesAgo(51) },
  { id: uid('a'), title: 'API key rotation expired', description: 'Service account key past its rotation window.', severity: 'MEDIUM', entityType: 'API', status: 'RESOLVED', assignedTo: 'engineer', triggeredAt: minutesAgo(180) },
  { id: uid('a'), title: 'Port scan detected', description: 'Inbound scanner hitting internal services.', severity: 'HIGH', entityType: 'NETWORK', status: 'ACKNOWLEDGED', triggeredAt: minutesAgo(300) },
  { id: uid('a'), title: 'Failed MFA challenge', description: 'Repeated OTP failures on a monitored account.', severity: 'LOW', entityType: 'USER', entityId: USERS[3].id, status: 'OPEN', assignedTo: 'analyst', triggeredAt: minutesAgo(620) },
  { id: uid('a'), title: 'Data export by non-admin', description: 'Bulk export API invoked without admin role.', severity: 'HIGH', entityType: 'API', status: 'INVESTIGATING', triggeredAt: minutesAgo(1300) },
  { id: uid('a'), title: 'Suspicious withdrawal cascade', description: 'Multiple rapid withdrawals nearing limits.', severity: 'MEDIUM', entityType: 'TRANSACTION', entityId: uid('p'), status: 'OPEN', triggeredAt: minutesAgo(260) },
];

// Synthetic dataset matching the payment-processing API contract. deviceId /
// ipAddress mirror the backend's masked egress form (devi**** / 203.0.113.xxx).
const fakeUuid = () => uid('pmt').replace(/[^a-zA-Z0-9-]/g, '') + '-0000-4000-8000-000000000000';
const maskedDevice = () => `devi${Math.random().toString(36).slice(2, 6).replace(/[^a-z0-9]/g, '')}****`;
const maskedIp = () => `203.0.113.${Math.floor(Math.random() * 250) + 2}`.replace(/(.+\.)\d+/, '$1xxx');

export const mockPayments: Payment[] = [
  { paymentId: fakeUuid(), customerId: USERS[3].id, merchantId: USERS[4].id, amount: 8490, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'HELD', createdAt: minutesAgo(9) },
  { paymentId: fakeUuid(), customerId: USERS[2].id, merchantId: USERS[4].id, amount: 1220, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'DECLINED', createdAt: minutesAgo(41) },
  { paymentId: fakeUuid(), customerId: USERS[4].id, merchantId: USERS[5].id, amount: 43.5, currency: 'EUR', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'APPROVED', createdAt: minutesAgo(70) },
  { paymentId: fakeUuid(), customerId: USERS[5].id, merchantId: USERS[6].id, amount: 3100, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'HELD', createdAt: minutesAgo(95) },
  { paymentId: fakeUuid(), customerId: USERS[3].id, merchantId: USERS[5].id, amount: 220, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'APPROVED', createdAt: minutesAgo(130) },
  { paymentId: fakeUuid(), customerId: USERS[6].id, merchantId: USERS[4].id, amount: 5000, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'DECLINED', createdAt: minutesAgo(200) },
  { paymentId: fakeUuid(), customerId: USERS[3].id, merchantId: USERS[6].id, amount: 460, currency: 'GBP', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'APPROVED', createdAt: minutesAgo(260) },
  { paymentId: fakeUuid(), customerId: USERS[2].id, merchantId: USERS[5].id, amount: 96, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'DECLINED', createdAt: minutesAgo(330) },
  { paymentId: fakeUuid(), customerId: USERS[4].id, merchantId: USERS[4].id, amount: 12400, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'PENDING', createdAt: minutesAgo(410) },
  { paymentId: fakeUuid(), customerId: USERS[5].id, merchantId: USERS[6].id, amount: 33, currency: 'USD', deviceId: maskedDevice(), ipAddress: maskedIp(), status: 'APPROVED', createdAt: minutesAgo(520) },
];

export const mockRiskDecisions: RiskDecision[] = [
  { id: uid('r'), subjectId: USERS[3].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'HIGH', riskScore: 0.82, factors: { velocity: 0.9, geo_disparity: 0.7, device_trust: 0.4 }, action: 'CHALLENGE', decisionAt: minutesAgo(36) },
  { id: uid('r'), subjectId: USERS[2].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'CRITICAL', riskScore: 0.97, factors: { blocklist: 1.0, velocity: 0.8 }, action: 'BLOCK', decisionAt: minutesAgo(60) },
  { id: uid('r'), subjectId: USERS[5].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'MEDIUM', riskScore: 0.55, factors: { amount_ratio: 0.6, new_device: 0.5 }, action: 'REVIEW', decisionAt: minutesAgo(95) },
  { id: uid('r'), subjectId: USERS[4].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'LOW', riskScore: 0.1, factors: { velocity: 0.1 }, action: 'ALLOW', decisionAt: minutesAgo(130) },
  { id: uid('r'), subjectId: USERS[6].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'HIGH', riskScore: 0.88, factors: { chargebacks: 0.9, velocity: 0.75 }, action: 'BLOCK', decisionAt: minutesAgo(210) },
  { id: uid('r'), subjectId: USERS[4].id, subjectType: 'USER', ruleVersion: 'risk-rules-2.4.1', riskLevel: 'MEDIUM', riskScore: 0.6, factors: { geo_disparity: 0.65 }, action: 'REVIEW', decisionAt: minutesAgo(280) },
];

export const mockEvents: SecurityEvent[] = [
  { id: uid('e'), eventType: 'LOGIN', userId: USERS[4].id, actor: USERS[4].username, action: 'AUTHENTICATE', outcome: 'FAILED', severity: 'HIGH', sourceIp: '185.220.101.42', occurredAt: minutesAgo(5) },
  { id: uid('e'), eventType: 'API_ATTACK', actor: 'oauth-client-7', action: 'REPLAY_DETECTED', outcome: 'BLOCKED', severity: 'CRITICAL', sourceIp: '45.155.205.11', occurredAt: minutesAgo(17) },
  { id: uid('e'), eventType: 'NETWORK', action: 'PORT_SCAN', outcome: 'DETECTED', severity: 'HIGH', sourceIp: '193.169.255.77', occurredAt: minutesAgo(44) },
  { id: uid('e'), eventType: 'PAYMENT', userId: USERS[3].id, action: 'PAYMENT_ATTEMPT', outcome: 'HELD', severity: 'HIGH', sourceIp: '91.219.236.9', occurredAt: minutesAgo(70) },
  { id: uid('e'), eventType: 'LOGIN', userId: USERS[3].id, actor: USERS[3].username, action: 'AUTHENTICATE', outcome: 'CHALLENGED', severity: 'MEDIUM', sourceIp: '185.220.101.42', occurredAt: minutesAgo(120) },
  { id: uid('e'), eventType: 'API_ATTACK', actor: 'anonymous', action: 'SQLI_ATTEMPT', outcome: 'BLOCKED', severity: 'CRITICAL', sourceIp: '62.210.138.227', occurredAt: minutesAgo(150) },
  { id: uid('e'), eventType: 'NETWORK', action: 'TRAFFIC_SPIKE', outcome: 'MONITORED', severity: 'MEDIUM', sourceIp: '172.16.4.12', occurredAt: minutesAgo(220) },
  { id: uid('e'), eventType: 'CONFIG', actor: 'engineer', action: 'RULE_DEPLOY', outcome: 'SUCCESS', severity: 'LOW', sourceIp: '10.0.0.5', occurredAt: minutesAgo(320) },
];

export const mockAuditLogs: AuditLog[] = [
  { id: uid('al'), userId: USERS[0].id, action: 'USER_UPDATE', actor: 'admin', resourceType: 'User', result: 'SUCCESS', details: { field: 'role' }, occurredAt: minutesAgo(12) },
  { id: uid('al'), userId: USERS[1].id, action: 'ALERT_UPDATE', actor: 'analyst', resourceType: 'SecurityAlert', result: 'SUCCESS', details: { status: 'INVESTIGATING' }, occurredAt: minutesAgo(31) },
  { id: uid('al'), action: 'ACCESS_DENIED', actor: 'm.reyes', resourceType: 'Endpoint', result: 'DENIED', details: { path: '/api/payments' }, occurredAt: minutesAgo(58) },
  { id: uid('al'), userId: USERS[0].id, action: 'SIMULATION_START', actor: 'admin', resourceType: 'SimulationRun', result: 'SUCCESS', occurredAt: minutesAgo(83) },
  { id: uid('al'), userId: USERS[3].id, action: 'SESSION_TERMINATE', actor: 'SOC_ANALYST', resourceType: 'Session', result: 'FORCED', occurredAt: minutesAgo(140) },
  { id: uid('al'), action: 'RATE_LIMIT', actor: 'oauth-client-7', resourceType: 'Api', result: 'THROTTLED', details: { limit: 80 }, occurredAt: minutesAgo(190) },
];

export const mockSimulations: SimulationRun[] = [
  { id: 'sim-001', simulationId: 'sim-001', campaignId: 'camp-mx7k2f-abc123', name: 'Multi-vector campaign', description: 'Blended attack across auth, API, payments, and network.', type: 'MIXED_ATTACK', status: 'RUNNING', configuration: { numberOfUsers: 500, durationSeconds: 120, eventsPerSecond: 50, attackPercent: 35, attackIntensity: 75, attackVectors: ['auth', 'api', 'payment', 'network', 'bot'] }, startedAt: minutesAgo(5), runBy: 'admin', eventsGenerated: 8090, detections: 324, riskDecisions: 280, alerts: 85, actions: 52, highestRisk: 'CRITICAL', usersAffected: 187, transactionsGenerated: 890, peakEps: 145, durationSeconds: 120 },
  { id: 'sim-002', simulationId: 'sim-002', name: 'Credential-stuffing drill', description: 'Simulate a distributed brute-force campaign.', type: 'BRUTE_FORCE', status: 'RUNNING', configuration: { numberOfUsers: 200, durationSeconds: 90, eventsPerSecond: 30, attackRate: 12 }, startedAt: minutesAgo(9), runBy: 'admin', eventsGenerated: 4500, detections: 180, riskDecisions: 150, alerts: 45, actions: 28, highestRisk: 'HIGH', usersAffected: 200, transactionsGenerated: 0, peakEps: 85, durationSeconds: 90 },
  { id: 'sim-003', simulationId: 'sim-003', name: 'Card-fraud scenario', description: 'Card-not-present fraud with velocity spikes.', type: 'PAYMENT_FRAUD', status: 'COMPLETED', configuration: { numberOfUsers: 150, durationSeconds: 180, txnVolume: 500 }, startedAt: minutesAgo(150), completedAt: minutesAgo(120), runBy: 'analyst', eventsGenerated: 2100, detections: 95, riskDecisions: 80, alerts: 22, actions: 15, highestRisk: 'CRITICAL', usersAffected: 150, transactionsGenerated: 500, peakEps: 65, durationSeconds: 180 },
  { id: 'sim-004', simulationId: 'sim-004', name: 'API abuse test', description: 'Replay tokens and malformed payloads.', type: 'API_ABUSE', status: 'COMPLETED', configuration: { numberOfUsers: 50, durationSeconds: 60, endpoints: ['/api/payments', '/api/users'] }, startedAt: minutesAgo(400), completedAt: minutesAgo(360), runBy: 'engineer', eventsGenerated: 12000, detections: 420, riskDecisions: 380, alerts: 95, actions: 60, highestRisk: 'HIGH', usersAffected: 50, transactionsGenerated: 0, peakEps: 320, durationSeconds: 60 },
  { id: 'sim-005', simulationId: 'sim-005', name: 'Bot fleet drill', description: 'Bot user-agents mixed with legitimate traffic.', type: 'BOT_ACTIVITY', status: 'COMPLETED', configuration: { numberOfUsers: 300, durationSeconds: 240, bots: 60, rpsPerBot: 18 }, startedAt: minutesAgo(620), completedAt: minutesAgo(580), runBy: 'engineer', eventsGenerated: 15000, detections: 280, riskDecisions: 220, alerts: 55, actions: 35, highestRisk: 'MEDIUM', usersAffected: 300, transactionsGenerated: 0, peakEps: 180, durationSeconds: 240 },
  { id: 'sim-006', simulationId: 'sim-006', name: 'Insider data exfil', description: 'Bulk export from a compromised analyst role.', type: 'DATA_ACCESS', status: 'FAILED', configuration: { numberOfUsers: 10, durationSeconds: 300, volume: 'high' }, startedAt: minutesAgo(900), completedAt: minutesAgo(860), runBy: 'admin', eventsGenerated: 850, detections: 45, riskDecisions: 38, alerts: 12, actions: 8, highestRisk: 'CRITICAL', usersAffected: 10, transactionsGenerated: 0, peakEps: 25, durationSeconds: 300, errors: ['Connection timeout at 85% progress'] },
  { id: 'sim-007', simulationId: 'sim-007', name: 'DDoS absorbing', description: 'Inbound network flood simulation.', type: 'NETWORK', status: 'PENDING', configuration: { numberOfUsers: 1000, durationSeconds: 600, rate: 4000 }, runBy: 'engineer', eventsGenerated: 0, detections: 0, riskDecisions: 0, alerts: 0, actions: 0, highestRisk: 'LOW', usersAffected: 0, transactionsGenerated: 0, peakEps: 0, durationSeconds: 600 },
  { id: 'sim-008', simulationId: 'sim-008', name: 'Account takeover chain', description: 'Compromise followed by privileged actions.', type: 'ACCOUNT_TAKEOVER', status: 'COMPLETED', configuration: { numberOfUsers: 80, durationSeconds: 150 }, startedAt: minutesAgo(1300), completedAt: minutesAgo(1250), runBy: 'analyst', eventsGenerated: 3200, detections: 150, riskDecisions: 120, alerts: 35, actions: 22, highestRisk: 'CRITICAL', usersAffected: 80, transactionsGenerated: 120, peakEps: 95, durationSeconds: 150 },
  { id: 'sim-009', simulationId: 'sim-009', name: 'Transaction velocity burst', description: 'Rapid-fire transactions per user.', type: 'TRANSACTION_VELOCITY', status: 'COMPLETED', configuration: { numberOfUsers: 100, durationSeconds: 90 }, startedAt: minutesAgo(1500), completedAt: minutesAgo(1480), runBy: 'analyst', eventsGenerated: 5500, detections: 210, riskDecisions: 180, alerts: 48, actions: 30, highestRisk: 'HIGH', usersAffected: 100, transactionsGenerated: 2800, peakEps: 250, durationSeconds: 90 },
  { id: 'sim-010', simulationId: 'sim-010', name: 'Port scan detection', description: 'Sequential probes across many ports.', type: 'NETWORK', status: 'CANCELLED', configuration: { numberOfUsers: 5, durationSeconds: 120 }, startedAt: minutesAgo(2000), completedAt: minutesAgo(1950), runBy: 'engineer', eventsGenerated: 1800, detections: 75, riskDecisions: 60, alerts: 18, actions: 12, highestRisk: 'MEDIUM', usersAffected: 5, transactionsGenerated: 0, peakEps: 45, durationSeconds: 120 },
];

// Simulation detail datasets for the details view.
export const mockSimulationEvents = (simId: string) => {
  const base = mockSimulations.find((s) => s.simulationId === simId);
  if (!base) return [];
  const count = Math.min(base.eventsGenerated ?? 0, 50);
  const events = [];
  for (let i = 0; i < count; i++) {
    events.push({
      id: `evt-${simId}-${i}`,
      simulationId: simId,
      campaignId: base.campaignId,
      eventType: ['LOGIN_ATTEMPT', 'API_REQUEST', 'PAYMENT_AUTH', 'NETWORK_CONNECTION', 'DATA_ACCESS'][i % 5],
      severity: i % 7 === 0 ? 'CRITICAL' : i % 5 === 0 ? 'HIGH' : i % 3 === 0 ? 'MEDIUM' : 'LOW',
      sourceIp: `192.168.${(i % 255) + 1}.${(i * 7) % 255}`,
      userId: `user-${(i % 100) + 1}`,
      action: ['LOGIN', 'API_CALL', 'PAYMENT', 'CONNECT', 'EXPORT'][i % 5],
      outcome: i % 4 === 0 ? 'DENIED' : i % 3 === 0 ? 'BLOCKED' : 'SUCCESS',
      description: `Simulated ${['authentication', 'API', 'payment', 'network', 'data'][i % 5]} event`,
      occurredAt: minutesAgo(Math.max(0, (base.durationSeconds ?? 60) - i * 2)),
    });
  }
  return events;
};

export const mockSimulationDetections = (simId: string) => {
  const base = mockSimulations.find((s) => s.simulationId === simId);
  if (!base) return [];
  const count = Math.min(base.detections ?? 0, 20);
  const rules = ['Brute Force Detection', 'Velocity Check', 'Geo Anomaly', 'Bot Signature', 'Data Exfil Pattern'];
  const detections = [];
  for (let i = 0; i < count; i++) {
    detections.push({
      id: `det-${simId}-${i}`,
      simulationId: simId,
      ruleName: rules[i % rules.length],
      severity: i % 5 === 0 ? 'CRITICAL' : i % 3 === 0 ? 'HIGH' : 'MEDIUM',
      confidence: 0.7 + (i % 30) / 100,
      eventIds: [`evt-${simId}-${i * 3}`, `evt-${simId}-${i * 3 + 1}`],
      description: `Detected ${rules[i % rules.length].toLowerCase()} pattern`,
      detectedAt: minutesAgo(Math.max(0, (base.durationSeconds ?? 60) - i * 5)),
    });
  }
  return detections;
};

export const mockSimulationRiskDecisions = (simId: string) => {
  const base = mockSimulations.find((s) => s.simulationId === simId);
  if (!base) return [];
  const count = Math.min(base.riskDecisions ?? 0, 15);
  const decisions = [];
  for (let i = 0; i < count; i++) {
    decisions.push({
      id: `risk-${simId}-${i}`,
      simulationId: simId,
      subjectId: `user-${(i % 50) + 1}`,
      subjectType: i % 3 === 0 ? 'USER' : i % 3 === 1 ? 'DEVICE' : 'SESSION',
      riskLevel: i % 6 === 0 ? 'CRITICAL' : i % 4 === 0 ? 'HIGH' : i % 3 === 0 ? 'MEDIUM' : 'LOW',
      riskScore: 0.3 + (i % 70) / 100,
      factors: { velocity: i % 2 === 0, geo: i % 3 === 0, device: i % 4 === 0 },
      action: i % 4 === 0 ? 'BLOCK' : i % 3 === 0 ? 'CHALLENGE' : i % 2 === 0 ? 'MONITOR' : 'ALLOW',
      decidedAt: minutesAgo(Math.max(0, (base.durationSeconds ?? 60) - i * 8)),
    });
  }
  return decisions;
};

export const mockSimulationAlerts = (simId: string) => {
  const base = mockSimulations.find((s) => s.simulationId === simId);
  if (!base) return [];
  const count = Math.min(base.alerts ?? 0, 10);
  const titles = ['Suspicious login pattern', 'High-value transaction anomaly', 'API rate limit exceeded', 'Unusual data access', 'Bot activity detected'];
  const alerts = [];
  for (let i = 0; i < count; i++) {
    alerts.push({
      id: `alrt-${simId}-${i}`,
      simulationId: simId,
      title: titles[i % titles.length],
      description: `Alert triggered by ${titles[i % titles.length].toLowerCase()}`,
      severity: i % 5 === 0 ? 'CRITICAL' : i % 3 === 0 ? 'HIGH' : 'MEDIUM',
      status: i % 4 === 0 ? 'OPEN' : i % 3 === 0 ? 'INVESTIGATING' : i % 2 === 0 ? 'RESOLVED' : 'FALSE_POSITIVE',
      triggeredAt: minutesAgo(Math.max(0, (base.durationSeconds ?? 60) - i * 10)),
    });
  }
  return alerts;
};

export const mockSimulationActions = (simId: string) => {
  const base = mockSimulations.find((s) => s.simulationId === simId);
  if (!base) return [];
  const count = Math.min(base.actions ?? 0, 10);
  const types = ['BLOCK_ACCOUNT', 'RATE_LIMIT', 'FORCE_LOGOUT', 'HOLD_PAYMENT', 'NOTIFY_ADMIN'];
  const actions = [];
  for (let i = 0; i < count; i++) {
    actions.push({
      id: `act-${simId}-${i}`,
      simulationId: simId,
      actionType: types[i % types.length],
      target: `user-${(i % 50) + 1}`,
      reason: `Automated response to ${types[i % types.length].toLowerCase().replace('_', ' ')}`,
      status: i % 10 === 0 ? 'FAILED' : 'SUCCESS',
      executedAt: minutesAgo(Math.max(0, (base.durationSeconds ?? 60) - i * 12)),
    });
  }
  return actions;
};

// Synthetic retail orders matching the retail-service Order contract.
export const mockOrders: Order[] = [
  { id: uid('o'), userId: USERS[3].id, status: 'PENDING', totalAmount: 1289.98, currency: 'USD', placedAt: minutesAgo(6), createdAt: minutesAgo(6) },
  { id: uid('o'), userId: USERS[5].id, status: 'PROCESSING', totalAmount: 49.99, currency: 'USD', placedAt: minutesAgo(38), createdAt: minutesAgo(38) },
  { id: uid('o'), userId: USERS[4].id, status: 'SHIPPED', totalAmount: 264.5, currency: 'EUR', placedAt: minutesAgo(96), createdAt: minutesAgo(96) },
  { id: uid('o'), userId: USERS[2].id, status: 'CANCELLED', totalAmount: 4800.0, currency: 'USD', placedAt: minutesAgo(150), createdAt: minutesAgo(150) },
  { id: uid('o'), userId: USERS[6].id, status: 'DELIVERED', totalAmount: 93.99, currency: 'USD', placedAt: minutesAgo(280), createdAt: minutesAgo(280) },
  { id: uid('o'), userId: USERS[3].id, status: 'DELIVERED', totalAmount: 1499.0, currency: 'GBP', placedAt: minutesAgo(420), createdAt: minutesAgo(420) },
  { id: uid('o'), userId: USERS[5].id, status: 'PENDING', totalAmount: 22.0, currency: 'USD', placedAt: minutesAgo(610), createdAt: minutesAgo(610) },
  { id: uid('o'), userId: USERS[4].id, status: 'PROCESSING', totalAmount: 899.0, currency: 'USD', placedAt: minutesAgo(800), createdAt: minutesAgo(800) },
];

export const mockProducts: Product[] = [
  { id: uid('pr'), sku: 'SNX-KEY-001', name: 'SentinelX YubiKey 5 NFC', category: 'hardware', price: 49.99, currency: 'USD', stock: 250, active: true },
  { id: uid('pr'), sku: 'SNX-SUB-ENT', name: 'SOC Console — Enterprise (1yr)', category: 'subscription', price: 4800.0, currency: 'USD', stock: 999, active: true },
  { id: uid('pr'), sku: 'SNX-TRN-RED', name: 'Red Team Ops Bootcamp', category: 'training', price: 1499.0, currency: 'USD', stock: 25, active: true },
  { id: uid('pr'), sku: 'SNX-CAM-010', name: 'EdgeCam 4K Dome', category: 'hardware', price: 189.0, currency: 'USD', stock: 60, active: true },
  { id: uid('pr'), sku: 'SNX-MUG-BLU', name: 'Blue Team Mug', category: 'merch', price: 14.99, currency: 'USD', stock: 750, active: true },
];

// Time-series for charts (last 24h, per hour).
export function buildTimeseries(seed: number, hours = 24): number[] {
  const out: number[] = [];
  for (let i = 0; i < hours; i += 1) {
    const wave = Math.sin((i + seed) / 4) * 0.5 + 0.5;
    const spike = (i + seed) % 7 === 0 ? 2.5 : 1;
    out.push(Math.max(1, Math.round((wave * 8 + 2) * spike)));
  }
  return out;
}

// --------------------------------------------------------------------- streaming
// Synthetic live-progress feed used when the real SSE endpoint is unreachable
// (offline / demo mode). Mirrors the backend SimulationProgressDto shape so the
// dashboard renders identically.

export interface MockStreamHandle {
  cancel: () => void;
}

function baseProgress(run: SimulationRun, id: string): SimulationProgress {
  const cfg = (run.configuration ?? {}) as Record<string, unknown>;
  const metrics = (run.metrics ?? {}) as Record<string, number>;
  const num = (k: string) => (typeof metrics[k] === 'number' ? (metrics[k] as number) : 0);
  const users = num('targetUsers') || num('users') || (Number(cfg.numberOfUsers) || 0);
  return {
    id,
    status: 'QUEUED',
    elapsed: 0,
    progress: 0,
    users,
    transactions: num('totalPayments') || Number(cfg.transactions) || 0,
    apiRequests: num('totalRequests') || 0,
    networkEvents: num('totalEvents') || 0,
    securityEvents: 0,
    detections: 0,
    riskDecisions: 0,
    alerts: 0,
    actions: 0,
    errors: [],
    eventsPerSec: 0,
    riskDistribution: { HIGH: 0, MEDIUM: 0, LOW: 0 },
    eventsPerSecond: [],
    alertsOverTime: [],
  };
}

/**
 * Drives a synthetic run from QUEUED → RUNNING → (COMPLETED | CANCELLED),
 * invoking onProgress each tick and onDone at the end. Returns a handle whose
 * cancel() flips the terminal state to CANCELLED immediately.
 */
export function mockStreamSimulation(
  run: SimulationRun,
  onProgress: (p: SimulationProgress) => void,
  onDone: (p: SimulationProgress) => void,
): MockStreamHandle {
  const id = run.simulationId ?? run.id ?? '';
  const base = baseProgress(run, id);
  const cfg = (run.configuration ?? {}) as Record<string, unknown>;
  const duration = Math.max(1, Number(cfg.durationSeconds) || 60);
  const ticks = Math.min(duration, 30);
  const intervalMs = 150;
  const totalEvents = Number(cfg.eventsPerSecond) * duration;
  const attackPercent = Number(cfg.attackPercentage) || 25;

  let tick = 0;
  let cancelled = false;
  let generated = 0;
  let detections = 0;
  let riskDecisions = 0;
  let alerts = 0;
  let actions = 0;
  const epsHistory: number[] = [];
  const alertsHistory: number[] = [];
  let highRisk = 0;
  let mediumRisk = 0;
  let lowRisk = 0;

  const finish = (status: 'COMPLETED' | 'CANCELLED' | 'FAILED') => {
    onDone({
      ...base,
      status,
      elapsed: tick,
      progress: cancelled ? tick / ticks : 1,
      eventsPerSec: 0,
      securityEvents: generated,
      detections,
      riskDecisions,
      alerts,
      actions,
      errors: cancelled ? ['Cancelled by user'] : [],
      eventsPerSecond: [...epsHistory],
      alertsOverTime: [...alertsHistory],
      riskDistribution: { HIGH: highRisk, MEDIUM: mediumRisk, LOW: lowRisk },
    });
  };

  const timer = setInterval(() => {
    if (cancelled) return;
    tick += 1;
    const progress = tick / ticks;
    // Simulate variable EPS with spikes
    const baseEps = Math.max(1, Math.round((base.users || 10) * 0.5));
    const spike = Math.sin(tick / 3) * 0.3 + 1;
    const eps = Math.round(baseEps * spike + tick * 0.5);
    generated += eps;

    // Simulate detections based on attack percentage
    const detectionRate = attackPercent / 100 * 0.08;
    const newDetections = Math.floor(eps * detectionRate);
    detections += newDetections;

    // Risk decisions follow detections
    riskDecisions = Math.floor(detections * 1.2);

    // Alerts follow detections
    const newAlerts = Math.floor(newDetections * 0.3);
    alerts += newAlerts;

    // Actions follow alerts
    const newActions = Math.floor(newAlerts * 0.7);
    actions += newActions;

    // Risk distribution
    highRisk += Math.floor(newDetections * 0.4);
    mediumRisk += Math.floor(newDetections * 0.35);
    lowRisk += Math.floor(newDetections * 0.25);

    epsHistory.push(eps);
    alertsHistory.push(alerts);

    onProgress({
      ...base,
      status: 'RUNNING',
      elapsed: tick,
      progress,
      users: base.users,
      transactions: Math.floor(base.transactions * progress),
      apiRequests: Math.floor((base.apiRequests || totalEvents * 0.3) * progress),
      networkEvents: Math.floor((base.networkEvents || totalEvents * 0.2) * progress),
      securityEvents: generated,
      detections,
      riskDecisions,
      alerts,
      actions,
      errors: [],
      eventsPerSec: eps,
      eventsPerSecond: [...epsHistory],
      alertsOverTime: [...alertsHistory],
      riskDistribution: { HIGH: highRisk, MEDIUM: mediumRisk, LOW: lowRisk },
    });

    if (tick >= ticks) {
      clearInterval(timer);
      finish('COMPLETED');
    }
  }, intervalMs);

  return {
    cancel: () => {
      cancelled = true;
      clearInterval(timer);
      finish('CANCELLED');
    },
  };
}


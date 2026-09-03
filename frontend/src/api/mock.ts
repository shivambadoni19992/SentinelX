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
  { id: uid('s'), name: 'Credential-stuffing drill', description: 'Simulate a distributed brute-force campaign.', scenario: 'BRUTE_FORCE', config: { users: 200, rate: 12 }, status: 'RUNNING', startedAt: minutesAgo(9), runBy: 'admin' },
  { id: uid('s'), name: 'Card-fraud scenario', description: 'Card-not-present fraud with velocity spikes.', scenario: 'CARD_FRAUD', config: { txnVolume: 500 }, status: 'COMPLETED', startedAt: minutesAgo(150), completedAt: minutesAgo(120), runBy: 'analyst' },
  { id: uid('s'), name: 'API abuse test', description: 'Replay tokens and malformed payloads.', scenario: 'API_ABUSE', config: { endpoints: ['/api/payments'] }, status: 'COMPLETED', startedAt: minutesAgo(400), completedAt: minutesAgo(360), runBy: 'engineer' },
  { id: uid('s'), name: 'Insider data exfil', description: 'Bulk export from a compromised analyst role.', scenario: 'INSIDER_THREAT', config: { volume: 'high' }, status: 'FAILED', startedAt: minutesAgo(900), completedAt: minutesAgo(860), runBy: 'admin' },
  { id: uid('s'), name: 'DDoS absorbing', description: 'Inbound network flood simulation.', scenario: 'DISTRIBUTED_DENIAL', config: { rate: 4000 }, status: 'PENDING', runBy: 'engineer' },
];

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

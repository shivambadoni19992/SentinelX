// SentinelX API contracts — mirror the backend DTOs exposed through the gateway.

export interface User {
  id: string;
  username: string;
  email: string;
  role: string;
  accountStatus: 'ACTIVE' | 'MONITORED' | 'BLOCKED' | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SecurityAlert {
  id: string;
  title: string;
  description?: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string;
  entityType: string;
  entityId?: string;
  eventId?: string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'FALSE_POSITIVE' | string;
  assignedTo?: string;
  action?: string;
  actor?: string;
  actionDetail?: Record<string, unknown>;
  triggeredAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SecurityEvent {
  id: string;
  eventType: string;
  userId?: string;
  deviceId?: string;
  sessionId?: string;
  actor?: string;
  action: string;
  outcome: string;
  severity: string;
  sourceIp?: string;
  metadata?: Record<string, unknown>;
  occurredAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export type PaymentStatus = 'PENDING' | 'APPROVED' | 'HELD' | 'DECLINED' | string;

// Mirrors backend PaymentResponse — deviceId/ipAddress arrive pre-masked.
export interface Payment {
  paymentId: string;
  customerId: string;
  merchantId: string;
  amount: number;
  currency: string;
  deviceId?: string;
  ipAddress?: string;
  status: PaymentStatus;
  createdAt?: string;
}

// Retail commerce contracts — mirror the retail-service DTOs.
export interface Product {
  id: string;
  sku: string;
  name: string;
  description?: string;
  category?: string;
  price: number;
  currency: string;
  stock: number;
  active: boolean;
  createdAt?: string;
  updatedAt?: string;
}

export interface Order {
  id: string;
  userId: string;
  status: 'PENDING' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED' | string;
  totalAmount: number;
  currency: string;
  placedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface CartLine {
  productId: string;
  sku: string;
  name: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface Cart {
  userId: string;
  items: CartLine[];
  total: number;
  currency: string;
}

export interface RiskDecision {
  id: string;
  subjectId?: string;
  subjectType: string;
  ruleVersion?: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  riskScore?: number;
  factors?: Record<string, unknown>;
  action: string;
  decisionAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AuditLog {
  id: string;
  userId?: string;
  action: string;
  actor?: string;
  resourceType: string;
  resourceId?: string;
  result: string;
  details?: Record<string, unknown>;
  occurredAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface SimulationRun {
  id?: string;
  simulationId?: string;
  campaignId?: string;
  name?: string;
  description?: string;
  scenario?: string;
  type?: string;
  configuration?: Record<string, unknown>;
  config?: Record<string, unknown>;
  status: 'QUEUED' | 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | string;
  startedAt?: string;
  completedAt?: string;
  eventsGenerated?: number;
  eventsProcessed?: number;
  detections?: number;
  riskDecisions?: number;
  alerts?: number;
  actions?: number;
  errors?: string[];
  /** Per-scenario live metrics (attackRate, failedLogins, targetUsers, sourceIps). */
  metrics?: Record<string, number>;
  runBy?: string;
  createdAt?: string;
  updatedAt?: string;
  /** Highest risk level observed during the run. */
  highestRisk?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  /** Number of users affected/targeted. */
  usersAffected?: number;
  /** Number of transactions generated. */
  transactionsGenerated?: number;
  /** Peak events per second. */
  peakEps?: number;
  /** Duration in seconds. */
  durationSeconds?: number;
}

/** Detailed simulation events for the details view. */
export interface SimulationEvent {
  id: string;
  simulationId: string;
  eventType: string;
  severity: string;
  sourceIp?: string;
  userId?: string;
  action: string;
  outcome: string;
  description?: string;
  occurredAt: string;
  campaignId?: string;
}

/** Detection record for the details view. */
export interface SimulationDetection {
  id: string;
  simulationId: string;
  ruleName: string;
  severity: string;
  confidence: number;
  eventIds: string[];
  description: string;
  detectedAt: string;
}

/** Risk decision for the details view. */
export interface SimulationRiskDecision {
  id: string;
  simulationId: string;
  subjectId: string;
  subjectType: string;
  riskLevel: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL' | string;
  riskScore: number;
  factors: Record<string, unknown>;
  action: string;
  decidedAt: string;
}

/** Alert for the details view. */
export interface SimulationAlert {
  id: string;
  simulationId: string;
  title: string;
  description: string;
  severity: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | string;
  status: 'OPEN' | 'INVESTIGATING' | 'RESOLVED' | 'FALSE_POSITIVE' | string;
  triggeredAt: string;
}

/** Action taken for the details view. */
export interface SimulationAction {
  id: string;
  simulationId: string;
  actionType: string;
  target: string;
  reason: string;
  status: 'SUCCESS' | 'FAILED' | 'PENDING' | string;
  executedAt: string;
}

/** Live progress snapshot streamed over SSE from /api/simulations/{id}/stream. */
export interface SimulationProgress {
  id: string;
  status: string;
  elapsed: number;
  progress: number;
  users: number;
  transactions: number;
  apiRequests: number;
  networkEvents: number;
  securityEvents: number;
  detections: number;
  riskDecisions: number;
  alerts: number;
  actions: number;
  errors: string[];
  eventsPerSec: number;
  riskDistribution: Record<string, number>;
  eventsPerSecond: number[];
  alertsOverTime: number[];
}


export interface ServiceHealth {
  id: string;
  name: string;
  url: string;
  status: 'UP' | 'DOWN' | 'unknown' | string;
  checkedAt?: string;
  details?: Record<string, unknown>;
}

// Dashboard aggregates used by the Overview page.
export interface OverviewStats {
  securityEvents: number;
  criticalAlerts: number;
  highRiskUsers: number;
  suspiciousTransactions: number;
  heldTransactions: number;
  blockedAccounts: number;
  apiAttacks: number;
  networkThreats: number;
  totalAlerts: number;
  totalTransactions: number;
  openAlerts: number;
  totalOrders: number;
  openOrders: number;
}
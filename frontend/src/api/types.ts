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
  status: 'OPEN' | 'ACKNOWLEDGED' | 'INVESTIGATING' | 'RESOLVED' | 'DISMISSED' | string;
  assignedTo?: string;
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

export interface Payment {
  id: string;
  userId?: string;
  orderId?: string;
  amount: number;
  currency: string;
  paymentMethod: string;
  transactionId?: string;
  status: string;
  riskScore?: number;
  failureReason?: string;
  originatedAt?: string;
  createdAt?: string;
  updatedAt?: string;
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
  id: string;
  name: string;
  description?: string;
  scenario: string;
  config?: Record<string, unknown>;
  status: 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED' | string;
  startedAt?: string;
  completedAt?: string;
  runBy?: string;
  createdAt?: string;
  updatedAt?: string;
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
}
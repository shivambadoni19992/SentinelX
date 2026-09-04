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
  totalOrders: number;
  openOrders: number;
}
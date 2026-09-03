// Typed accessors for SentinelX gateway endpoints.
// Each returns the raw backend array. Non-200 responses throw ApiError so the
// hook layer can decide between live resolution and demo fallback.

import { apiFetch } from './client';
import type {
  AuditLog,
  Cart,
  Order,
  Payment,
  Product,
  RiskDecision,
  SecurityAlert,
  SecurityEvent,
  ServiceHealth,
  SimulationRun,
  User,
} from './types';

interface ServicesResponse {
  revealed: boolean;
  count: number;
  services: ServiceHealth[];
}

// Public (gateway allows unauthenticated access) — used by the topbar.
export function fetchServices(): Promise<ServiceHealth[]> {
  return apiFetch<ServicesResponse>('/api/system/services', { token: false }).then(
    (r) => r.services ?? [],
  );
}

// Reachable, authenticated endpoints ------------------------------------------
export const listUsers = () => apiFetch<User[]>('/api/auth/users');
export const listAlerts = () => apiFetch<SecurityAlert[]>('/api/alerts');
export const listPayments = () => apiFetch<Payment[]>('/api/payments');

export interface NewPayment {
  customerId: string;
  merchantId: string;
  amount: number;
  currency: string;
  deviceId?: string;
  ipAddress?: string;
  idempotencyKey?: string;
}

/** POST /api/payments — create a synthetic payment (idempotent when a key is supplied). */
export function createPayment(body: NewPayment, idempotencyKey?: string): Promise<Payment> {
  return apiFetch<Payment>('/api/payments', {
    method: 'POST',
    body: JSON.stringify(body),
    headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined,
  });
}

/** GET /api/payments/{id} — fetch a single (masked) payment. */
export const getPayment = (id: string) => apiFetch<Payment>(`/api/payments/${id}`);

// Retail commerce ---------------------------------------------------------

export const listProducts = () => apiFetch<Product[]>('/api/retail/products');
export const listOrders = () => apiFetch<Order[]>('/api/retail/orders');
export const getCart = () => apiFetch<Cart>('/api/retail/cart');

/** POST /api/retail/cart/items — add (or merge) a quantity of a product. */
export function addToCart(productId: string, quantity: number): Promise<Cart> {
  return apiFetch<Cart>('/api/retail/cart/items', {
    method: 'POST',
    body: JSON.stringify({ productId, quantity }),
  });
}

/** POST /api/retail/checkout — convert the cart into an order. */
export const checkout = () =>
  apiFetch<Order>('/api/retail/checkout', { method: 'POST' });
export const listRiskDecisions = () => apiFetch<RiskDecision[]>('/api/risk/decisions');
export const listSimulations = () => apiFetch<SimulationRun[]>('/api/simulations');

// Endpoints whose gateway routing is not wired up yet (backend returns 404/503).
// We still attempt them so the dashboard uses them the moment the route exists.
export const listSecurityEvents = () => apiFetch<SecurityEvent[]>('/api/security/events');
export const listAuditLogs = () => apiFetch<AuditLog[]>('/api/security/audit-logs');
export const listDetections = () => apiFetch<SecurityEvent[]>('/api/detections');

export interface NewSimulation {
  name: string;
  description?: string;
  scenario: string;
  config?: Record<string, unknown>;
  status?: string;
  runBy?: string;
}

/** POST /api/simulations — launch a new simulation run. */
export function createSimulation(body: NewSimulation): Promise<SimulationRun> {
  return apiFetch<SimulationRun>('/api/simulations', {
    method: 'POST',
    body: JSON.stringify(body),
  });
}
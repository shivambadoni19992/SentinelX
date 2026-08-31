// Typed accessors for SentinelX gateway endpoints.
// Each returns the raw backend array. Non-200 responses throw ApiError so the
// hook layer can decide between live resolution and demo fallback.

import { apiFetch } from './client';
import type {
  AuditLog,
  Payment,
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
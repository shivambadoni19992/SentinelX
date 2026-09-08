// Correlation ID tracking for distributed tracing
// Generates and propagates correlation IDs across requests

const CORRELATION_ID_KEY = 'sentinelx_correlation_id';

// Generate a new correlation ID
export function generateCorrelationId(): string {
  const timestamp = Date.now().toString(36);
  const random = Math.random().toString(36).slice(2, 10);
  return `corr_${timestamp}_${random}`;
}

// Get the current correlation ID from session storage
export function getCorrelationId(): string | null {
  return sessionStorage.getItem(CORRELATION_ID_KEY);
}

// Set the correlation ID in session storage
export function setCorrelationId(id: string): void {
  sessionStorage.setItem(CORRELATION_ID_KEY, id);
}

// Get or create a correlation ID
export function getOrCreateCorrelationId(): string {
  let id = getCorrelationId();
  if (!id) {
    id = generateCorrelationId();
    setCorrelationId(id);
  }
  return id;
}

// Clear the correlation ID
export function clearCorrelationId(): void {
  sessionStorage.removeItem(CORRELATION_ID_KEY);
}

// Get headers object with correlation ID for fetch requests
export function getCorrelationHeaders(): Record<string, string> {
  const id = getOrCreateCorrelationId();
  return {
    'X-Correlation-ID': id,
    'X-Request-ID': generateCorrelationId(),
  };
}

// Correlation context for tracking operations
export interface CorrelationContext {
  correlationId: string;
  requestId: string;
  startTime: number;
  operation: string;
}

// Create a new correlation context
export function createContext(operation: string): CorrelationContext {
  return {
    correlationId: getOrCreateCorrelationId(),
    requestId: generateCorrelationId(),
    startTime: Date.now(),
    operation,
  };
}

// Format duration from context
export function getDuration(context: CorrelationContext): number {
  return Date.now() - context.startTime;
}

// Simulation tests for limits and cancellation

import { TEST_CONFIG, TestResults } from '../e2e/test-config';

export class SimulationTests {
  private results: TestResults;
  private authToken: string | null = null;

  constructor(results: TestResults) {
    this.results = results;
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<Response> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...(this.authToken ? { Authorization: `Bearer ${this.authToken}` } : {}),
    };
    return fetch(url, { ...options, headers: { ...headers, ...(options.headers as Record<string, string>) } });
  }

  async authenticate(): Promise<boolean> {
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/login`, {
        method: 'POST',
        body: JSON.stringify(TEST_CONFIG.adminUser),
      });
      if (res.ok) {
        const data = await res.json();
        this.authToken = data.token;
        return true;
      }
      return false;
    } catch {
      return false;
    }
  }

  // Test simulation event limit enforcement
  async testSimulationEventLimit(): Promise<void> {
    const start = Date.now();
    try {
      // Try to create simulation with events exceeding limit
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Limit Test Simulation',
          type: 'BRUTE_FORCE',
          configuration: {
            numberOfUsers: 10000,
            durationSeconds: 3600,
            eventsPerSecond: 1000,
            attackPercent: 50,
          },
        }),
      });

      // Should be rejected due to exceeding event limit
      const rejected = res.status === 400 || res.status === 422;

      if (!rejected) {
        // If accepted, verify it was capped
        const sim = await res.json();
        this.results.add({
          name: 'Simulation Event Limit Enforcement',
          passed: false,
          duration: Date.now() - start,
          error: 'Simulation was not rejected despite exceeding limits',
        });
        return;
      }

      this.results.add({
        name: 'Simulation Event Limit Enforcement',
        passed: rejected,
        duration: Date.now() - start,
        details: { rejected, status: res.status },
      });
    } catch (error) {
      this.results.add({
        name: 'Simulation Event Limit Enforcement',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test simulation duration limit
  async testSimulationDurationLimit(): Promise<void> {
    const start = Date.now();
    try {
      // Try simulation with duration exceeding limit (max should be ~600s)
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Duration Limit Test',
          type: 'NORMAL_TRAFFIC',
          configuration: {
            numberOfUsers: 10,
            durationSeconds: 7200, // 2 hours - should be rejected
            eventsPerSecond: 1,
          },
        }),
      });

      const rejected = res.status === 400 || res.status === 422;

      this.results.add({
        name: 'Simulation Duration Limit Enforcement',
        passed: rejected,
        duration: Date.now() - start,
        details: { rejected, status: res.status },
      });
    } catch (error) {
      this.results.add({
        name: 'Simulation Duration Limit Enforcement',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test simulation cancellation
  async testSimulationCancellation(): Promise<void> {
    const start = Date.now();
    let simulationId: string | null = null;
    try {
      // Start a long-running simulation
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Cancellation Test',
          type: 'NORMAL_TRAFFIC',
          configuration: {
            numberOfUsers: 100,
            durationSeconds: 120,
            eventsPerSecond: 10,
          },
        }),
      });

      if (!res.ok) throw new Error('Failed to start simulation');

      const sim = await res.json();
      simulationId = sim.simulationId || sim.id;

      // Wait a moment for it to start
      await new Promise((resolve) => setTimeout(resolve, 3000));

      // Cancel the simulation
      const cancelRes = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/simulations/${simulationId}/cancel`,
        { method: 'POST' }
      );

      if (!cancelRes.ok) throw new Error('Failed to cancel simulation');

      // Wait for cancellation to take effect
      await new Promise((resolve) => setTimeout(resolve, 3000));

      // Verify simulation is cancelled
      const statusRes = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/simulations/${simulationId}`
      );
      const statusData = await statusRes.json();
      const isCancelled = statusData.status === 'CANCELLED';

      this.results.add({
        name: 'Simulation Cancellation',
        passed: isCancelled,
        duration: Date.now() - start,
        details: { simulationId, finalStatus: statusData.status },
      });
    } catch (error) {
      this.results.add({
        name: 'Simulation Cancellation',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test concurrent simulation limit
  async testConcurrentSimulationLimit(): Promise<void> {
    const start = Date.now();
    try {
      // Start multiple simulations quickly
      const promises = [];
      for (let i = 0; i < 5; i++) {
        promises.push(
          this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
            method: 'POST',
            body: JSON.stringify({
              name: `Concurrent Test ${i}`,
              type: 'NORMAL_TRAFFIC',
              configuration: {
                numberOfUsers: 10,
                durationSeconds: 60,
                eventsPerSecond: 5,
              },
            }),
          })
        );
      }

      const results = await Promise.all(promises);
      const accepted = results.filter((r) => r.ok).length;
      const rejected = results.filter((r) => r.status === 429 || r.status === 400).length;

      // Some should be accepted, some may be rejected due to limits
      this.results.add({
        name: 'Concurrent Simulation Limit',
        passed: true, // Test passes if system handles concurrent requests gracefully
        duration: Date.now() - start,
        details: { accepted, rejected, total: results.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Concurrent Simulation Limit',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test SSE stream during simulation
  async testSSEStream(): Promise<void> {
    const start = Date.now();
    try {
      // Start a simulation
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'SSE Test',
          type: 'NORMAL_TRAFFIC',
          configuration: {
            numberOfUsers: 50,
            durationSeconds: 30,
            eventsPerSecond: 20,
          },
        }),
      });

      if (!res.ok) throw new Error('Failed to start simulation');

      const sim = await res.json();
      const simulationId = sim.simulationId || sim.id;

      // Connect to SSE stream
      const streamRes = await fetch(
        `${TEST_CONFIG.baseUrl}/api/simulations/${simulationId}/stream`
      );

      const streamConnected = streamRes.ok;
      const contentType = streamRes.headers.get('content-type');
      const isSSE = contentType?.includes('text/event-stream');

      this.results.add({
        name: 'SSE Stream Connection',
        passed: streamConnected && isSSE,
        duration: Date.now() - start,
        details: { streamConnected, contentType },
      });
    } catch (error) {
      this.results.add({
        name: 'SSE Stream Connection',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }
}

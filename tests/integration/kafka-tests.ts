// Kafka integration tests

import { TEST_CONFIG, TestResults } from '../e2e/test-config';

export class KafkaTests {
  private results: TestResults;

  constructor(results: TestResults) {
    this.results = results;
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<Response> {
    return fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers as Record<string, string>) } });
  }

  // Test Kafka message production
  async testKafkaProduce(): Promise<void> {
    const start = Date.now();
    try {
      // Trigger an event that should produce a Kafka message
      const res = await this.makeRequest(`${TEST_CONFIG.securityUrl}/api/security/events`, {
        method: 'POST',
        body: JSON.stringify({
          eventType: 'TEST_EVENT',
          severity: 'LOW',
          userId: 'test-user',
          action: 'TEST_ACTION',
          outcome: 'SUCCESS',
          sourceIp: '127.0.0.1',
        }),
      });

      const produced = res.ok;

      this.results.add({
        name: 'Kafka - Message Production',
        passed: produced,
        duration: Date.now() - start,
        details: { produced, status: res.status },
      });
    } catch (error) {
      this.results.add({
        name: 'Kafka - Message Production',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test Kafka message consumption
  async testKafkaConsume(): Promise<void> {
    const start = Date.now();
    try {
      // Wait a moment for message processing
      await new Promise((resolve) => setTimeout(resolve, 2000));

      // Check if the event was processed
      const res = await this.makeRequest(
        `${TEST_CONFIG.securityUrl}/api/security/events?eventType=TEST_EVENT`
      );

      const data = res.ok ? await res.json() : [];
      const consumed = Array.isArray(data) && data.length > 0;

      this.results.add({
        name: 'Kafka - Message Consumption',
        consumed,
        duration: Date.now() - start,
        details: { eventsProcessed: data.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Kafka - Message Consumption',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test end-to-end Kafka flow
  async testKafkaEndToEnd(): Promise<void> {
    const start = Date.now();
    try {
      // Produce a security event
      const produceRes = await this.makeRequest(`${TEST_CONFIG.securityUrl}/api/security/events`, {
        method: 'POST',
        body: JSON.stringify({
          eventType: 'BRUTE_FORCE_ATTEMPT',
          severity: 'HIGH',
          userId: 'victim-user',
          action: 'LOGIN_FAILED',
          outcome: 'DENIED',
          sourceIp: '10.0.0.1',
        }),
      });

      // Wait for processing
      await new Promise((resolve) => setTimeout(resolve, 3000));

      // Check for detection
      const detectionRes = await this.makeRequest(
        `${TEST_CONFIG.detectionUrl}/api/detections?eventType=BRUTE_FORCE_ATTEMPT`
      );
      const detections = detectionRes.ok ? await detectionRes.json() : [];

      // Check for risk decision
      const riskRes = await this.makeRequest(
        `${TEST_CONFIG.riskUrl}/api/risk/decisions?subjectId=victim-user`
      );
      const risks = riskRes.ok ? await riskRes.json() : [];

      const fullFlow = produceRes.ok && detections.length > 0 && risks.length > 0;

      this.results.add({
        name: 'Kafka - End-to-End Event Flow',
        passed: fullFlow,
        duration: Date.now() - start,
        details: {
          produced: produceRes.ok,
          detections: detections.length,
          riskDecisions: risks.length,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Kafka - End-to-End Event Flow',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }
}

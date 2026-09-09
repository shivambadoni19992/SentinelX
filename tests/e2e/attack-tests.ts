// Attack scenario end-to-end tests
// Tests the full flow: attack → detection → risk → alert → action

import { TEST_CONFIG, TestResults } from './test-config';

export class AttackTests {
  private results: TestResults;
  private authToken: string | null = null;

  constructor(results: TestResults) {
    this.results = results;
  }

  private async makeRequest(
    url: string,
    options: RequestInit = {}
  ): Promise<Response> {
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

  // Test 1: Normal Traffic - should NOT trigger detections
  async testNormalTraffic(): Promise<void> {
    const start = Date.now();
    try {
      // Simulate normal traffic
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Normal Traffic Test',
          type: 'NORMAL_TRAFFIC',
          configuration: {
            numberOfUsers: 50,
            durationSeconds: 30,
            eventsPerSecond: 5,
            attackPercent: 0,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify no detections
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasDetections = detectionData.length > 0;

      this.results.add({
        name: 'Normal Traffic - No false positives',
        passed: !hasDetections,
        duration: Date.now() - start,
        details: { simulationId: sim.simulationId, detections: detectionData.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Normal Traffic - No false positives',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 2: Brute Force - should trigger detection and risk
  async testBruteForce(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Brute Force Test',
          type: 'BRUTE_FORCE',
          configuration: {
            numberOfUsers: TEST_CONFIG.bruteForce.targetUsers,
            durationSeconds: 30,
            eventsPerSecond: TEST_CONFIG.bruteForce.attemptsPerSecond,
            targetUsers: TEST_CONFIG.bruteForce.targetUsers,
            failedAttemptsPerUser: TEST_CONFIG.bruteForce.attemptsPerUser,
            attemptsPerSecond: TEST_CONFIG.bruteForce.attemptsPerSecond,
            authSourceIps: 5,
            authDevices: 3,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify detections were created
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();

      // Verify risk decisions were made
      const risks = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/risk/decisions?simulationId=${sim.simulationId || sim.id}`
      );
      const riskData = await risks.json();

      // Verify alerts were generated
      const alerts = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/alerts?simulationId=${sim.simulationId || sim.id}`
      );
      const alertData = await alerts.json();

      const hasDetections = detectionData.length > 0;
      const hasRiskDecisions = riskData.length > 0;
      const hasAlerts = alertData.length > 0;
      const hasCriticalAlert = alertData.some((a: { severity: string }) => a.severity === 'CRITICAL' || a.severity === 'HIGH');

      this.results.add({
        name: 'Brute Force → Detection → Risk → Alert',
        passed: hasDetections && hasRiskDecisions && hasAlerts,
        duration: Date.now() - start,
        details: {
          detections: detectionData.length,
          riskDecisions: riskData.length,
          alerts: alertData.length,
          hasCriticalAlert,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Brute Force → Detection → Risk → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 3: Account Takeover - should result in CRITICAL alert and BLOCKED account
  async testAccountTakeover(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Account Takeover Test',
          type: 'ACCOUNT_TAKEOVER',
          configuration: {
            numberOfUsers: TEST_CONFIG.accountTakeover.targetUsers,
            durationSeconds: 30,
            eventsPerSecond: 10,
            targetUsers: TEST_CONFIG.accountTakeover.targetUsers,
            failedAttemptsPerUser: TEST_CONFIG.accountTakeover.failedAttemptsPerUser,
            newIpPercentage: TEST_CONFIG.accountTakeover.newIpPercentage,
            newDevicePercentage: TEST_CONFIG.accountTakeover.newDevicePercentage,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify CRITICAL alert was generated
      const alerts = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/alerts?simulationId=${sim.simulationId || sim.id}`
      );
      const alertData = await alerts.json();
      const hasCriticalAlert = alertData.some((a: { severity: string }) => a.severity === 'CRITICAL');

      // Verify account was blocked
      const actions = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/actions?simulationId=${sim.simulationId || sim.id}`
      );
      const actionData = await actions.json();
      const hasBlockAction = actionData.some((a: { actionType: string }) =>
        a.actionType === 'BLOCK_ACCOUNT' || a.actionType === 'BLOCK'
      );

      this.results.add({
        name: 'Account Takeover → CRITICAL → Account BLOCKED',
        passed: hasCriticalAlert && hasBlockAction,
        duration: Date.now() - start,
        details: {
          criticalAlerts: alertData.filter((a: { severity: string }) => a.severity === 'CRITICAL').length,
          blockActions: actionData.filter((a: { actionType: string }) => a.actionType.includes('BLOCK')).length,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Account Takeover → CRITICAL → Account BLOCKED',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 4: Payment Fraud - should result in payment HELD
  async testPaymentFraud(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Payment Fraud Test',
          type: 'PAYMENT_FRAUD',
          configuration: {
            numberOfUsers: 20,
            durationSeconds: 30,
            eventsPerSecond: 15,
            transactions: TEST_CONFIG.paymentFraud.transactions,
            highValuePercentage: TEST_CONFIG.paymentFraud.highValuePercentage,
            newDevicePercentage: TEST_CONFIG.paymentFraud.newDevicePercentage,
            suspiciousIpPercentage: TEST_CONFIG.paymentFraud.suspiciousIpPercentage,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify payments were held
      const payments = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/payments?simulationId=${sim.simulationId || sim.id}&status=HELD`
      );
      const paymentData = await payments.json();
      const hasHeldPayments = paymentData.length > 0;

      // Verify fraud detection
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasFraudDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('fraud') || d.ruleName?.toLowerCase().includes('payment')
      );

      this.results.add({
        name: 'Payment Fraud → Detection → Risk → Alert → Payment HELD',
        passed: hasHeldPayments && hasFraudDetection,
        duration: Date.now() - start,
        details: {
          heldPayments: paymentData.length,
          fraudDetections: detectionData.length,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Payment Fraud → Detection → Risk → Alert → Payment HELD',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 5: Transaction Velocity - should trigger velocity detection
  async testTransactionVelocity(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Transaction Velocity Test',
          type: 'TRANSACTION_VELOCITY',
          configuration: {
            numberOfUsers: TEST_CONFIG.transactionVelocity.users,
            durationSeconds: 30,
            eventsPerSecond: 50,
            users: TEST_CONFIG.transactionVelocity.users,
            transactionsPerUser: TEST_CONFIG.transactionVelocity.transactionsPerUser,
            timeWindowSeconds: TEST_CONFIG.transactionVelocity.timeWindowSeconds,
            amount: 1000,
            concurrentUsers: 5,
            attackPercentage: 40,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify velocity detection
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasVelocityDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('velocity')
      );

      this.results.add({
        name: 'Transaction Velocity → Detection → Alert',
        passed: hasVelocityDetection,
        duration: Date.now() - start,
        details: { detections: detectionData.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Transaction Velocity → Detection → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 6: API Abuse - should trigger rate limiting and alert
  async testApiAbuse(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'API Abuse Test',
          type: 'API_ABUSE',
          configuration: {
            numberOfUsers: TEST_CONFIG.apiAbuse.users,
            durationSeconds: TEST_CONFIG.apiAbuse.durationSeconds,
            eventsPerSecond: TEST_CONFIG.apiAbuse.attackRps,
            apiUsers: TEST_CONFIG.apiAbuse.users,
            apiSourceIps: 10,
            targetEndpoint: '/api/payments',
            normalRps: TEST_CONFIG.apiAbuse.normalRps,
            attackRps: TEST_CONFIG.apiAbuse.attackRps,
            apiDurationSeconds: TEST_CONFIG.apiAbuse.durationSeconds,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify rate limiting was applied
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasRateLimitDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('rate') || d.ruleName?.toLowerCase().includes('api')
      );

      // Verify alert was generated
      const alerts = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/alerts?simulationId=${sim.simulationId || sim.id}`
      );
      const alertData = await alerts.json();

      this.results.add({
        name: 'API Abuse → Rate Limiting → Alert',
        passed: hasRateLimitDetection && alertData.length > 0,
        duration: Date.now() - start,
        details: {
          detections: detectionData.length,
          alerts: alertData.length,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'API Abuse → Rate Limiting → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 7: Bot Activity - should detect bot patterns
  async testBotActivity(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Bot Activity Test',
          type: 'BOT_ACTIVITY',
          configuration: {
            numberOfUsers: TEST_CONFIG.botActivity.botCount,
            durationSeconds: 30,
            eventsPerSecond: TEST_CONFIG.botActivity.rpsPerBot * TEST_CONFIG.botActivity.botCount,
            botCount: TEST_CONFIG.botActivity.botCount,
            requestsPerBot: TEST_CONFIG.botActivity.requestsPerBot,
            rpsPerBot: TEST_CONFIG.botActivity.rpsPerBot,
            targetEndpoints: '/api/products, /api/search',
            sessionDurationSeconds: 30,
            botUserAgentPattern: 'TestBot/$VERSION',
            botDurationSeconds: 30,
            attackPercentage: 70,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify bot detection
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasBotDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('bot')
      );

      this.results.add({
        name: 'Bot Activity → Detection → Alert',
        passed: hasBotDetection,
        duration: Date.now() - start,
        details: { detections: detectionData.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Bot Activity → Detection → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 8: Network Scan - should trigger detection, risk, and alert
  async testNetworkScan(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Network Scan Test',
          type: 'PORT_SCAN',
          configuration: {
            numberOfUsers: 1,
            durationSeconds: 30,
            eventsPerSecond: 20,
            sourceContainers: TEST_CONFIG.networkScan.sourceContainers.join(','),
            targetContainers: TEST_CONFIG.networkScan.targetContainers.join(','),
            ports: TEST_CONFIG.networkScan.ports,
            attempts: TEST_CONFIG.networkScan.attempts,
            connectionsPerSecond: 20,
            networkDurationSeconds: 30,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify network scan detection
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasScanDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('scan') || d.ruleName?.toLowerCase().includes('port')
      );

      // Verify risk assessment
      const risks = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/risk/decisions?simulationId=${sim.simulationId || sim.id}`
      );
      const riskData = await risks.json();

      // Verify alert
      const alerts = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/alerts?simulationId=${sim.simulationId || sim.id}`
      );
      const alertData = await alerts.json();

      this.results.add({
        name: 'Network Scan → Detection → Risk → Alert',
        passed: hasScanDetection && riskData.length > 0 && alertData.length > 0,
        duration: Date.now() - start,
        details: {
          detections: detectionData.length,
          riskDecisions: riskData.length,
          alerts: alertData.length,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Network Scan → Detection → Risk → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 9: Data Access - should detect unauthorized access
  async testDataAccess(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Data Access Test',
          type: 'UNAUTHORIZED_DATA_ACCESS',
          configuration: {
            numberOfUsers: TEST_CONFIG.dataAccess.users,
            durationSeconds: 30,
            eventsPerSecond: 10,
            users: TEST_CONFIG.dataAccess.users,
            volume: TEST_CONFIG.dataAccess.volume,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id);

      // Verify data access detection
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();
      const hasDataAccessDetection = detectionData.some((d: { ruleName: string }) =>
        d.ruleName?.toLowerCase().includes('data') || d.ruleName?.toLowerCase().includes('access')
      );

      this.results.add({
        name: 'Data Access → Detection → Alert',
        passed: hasDataAccessDetection,
        duration: Date.now() - start,
        details: { detections: detectionData.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Data Access → Detection → Alert',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test 10: Mixed Attack - should trigger multiple detections
  async testMixedAttack(): Promise<void> {
    const start = Date.now();
    try {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations`, {
        method: 'POST',
        body: JSON.stringify({
          name: 'Mixed Attack Test',
          type: 'MIXED_ATTACK',
          configuration: {
            numberOfUsers: 100,
            durationSeconds: 60,
            eventsPerSecond: 50,
            attackPercent: 35,
            attackIntensity: 75,
            attackVectors: ['auth', 'api', 'payment', 'network', 'bot'],
            includeAuth: true,
            includeApi: true,
            includePayment: true,
            includeNetwork: true,
            includeBot: true,
          },
        }),
      });

      if (!res.ok) throw new Error(`Failed to start simulation: ${res.status}`);

      const sim = await res.json();
      await this.waitForCompletion(sim.simulationId || sim.id, 90000);

      // Verify multiple detection types
      const detections = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/detections?simulationId=${sim.simulationId || sim.id}`
      );
      const detectionData = await detections.json();

      // Verify campaign ID was generated
      const simDetails = await this.makeRequest(
        `${TEST_CONFIG.baseUrl}/api/simulations/${sim.simulationId || sim.id}`
      );
      const simData = await simDetails.json();
      const hasCampaignId = !!simData.campaignId;

      this.results.add({
        name: 'Mixed Attack → Multiple Detections → Campaign ID',
        passed: detectionData.length >= 3 && hasCampaignId,
        duration: Date.now() - start,
        details: {
          detections: detectionData.length,
          hasCampaignId,
          campaignId: simData.campaignId,
        },
      });
    } catch (error) {
      this.results.add({
        name: 'Mixed Attack → Multiple Detections → Campaign ID',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  private async waitForCompletion(simulationId: string, timeout = 60000): Promise<void> {
    const start = Date.now();
    while (Date.now() - start < timeout) {
      const res = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/simulations/${simulationId}`);
      if (res.ok) {
        const data = await res.json();
        if (data.status === 'COMPLETED' || data.status === 'FAILED' || data.status === 'CANCELLED') {
          return;
        }
      }
      await new Promise((resolve) => setTimeout(resolve, 2000));
    }
    throw new Error(`Simulation ${simulationId} did not complete within ${timeout}ms`);
  }
}

// End-to-end test configuration

export const TEST_CONFIG = {
  // API endpoints
  baseUrl: process.env.API_BASE_URL || 'http://localhost:8080',
  authUrl: process.env.AUTH_URL || 'http://localhost:8081',
  paymentUrl: process.env.PAYMENT_URL || 'http://localhost:8082',
  retailUrl: process.env.RETAIL_URL || 'http://localhost:8083',
  securityUrl: process.env.SECURITY_URL || 'http://localhost:8084',
  detectionUrl: process.env.DETECTION_URL || 'http://localhost:8085',
  riskUrl: process.env.RISK_URL || 'http://localhost:8086',
  alertUrl: process.env.ALERT_URL || 'http://localhost:8087',
  simulationUrl: process.env.SIMULATION_URL || 'http://localhost:8088',

  // Test timeouts
  timeout: 30000,
  simulationTimeout: 120000,

  // Test data
  testUser: {
    username: 'testuser',
    email: 'testuser@sentinelx.io',
    password: 'TestPassword123!',
    role: 'CUSTOMER',
  },
  adminUser: {
    username: 'admin',
    email: 'admin@sentinelx.io',
    password: 'admin',
    role: 'ADMIN',
  },
  analystUser: {
    username: 'analyst',
    email: 'analyst@sentinelx.io',
    password: 'analyst',
    role: 'SOC_ANALYST',
  },

  // Attack simulation parameters
  bruteForce: {
    targetUsers: 3,
    attemptsPerUser: 50,
    attemptsPerSecond: 10,
  },
  accountTakeover: {
    targetUsers: 2,
    failedAttemptsPerUser: 20,
    newIpPercentage: 100,
    newDevicePercentage: 100,
  },
  paymentFraud: {
    transactions: 100,
    highValuePercentage: 30,
    newDevicePercentage: 50,
    suspiciousIpPercentage: 40,
  },
  transactionVelocity: {
    users: 10,
    transactionsPerUser: 50,
    timeWindowSeconds: 60,
  },
  apiAbuse: {
    users: 5,
    normalRps: 10,
    attackRps: 100,
    durationSeconds: 30,
  },
  botActivity: {
    botCount: 20,
    requestsPerBot: 100,
    rpsPerBot: 10,
  },
  networkScan: {
    sourceContainers: ['attacker-container'],
    targetContainers: ['api-gateway', 'postgres'],
    ports: '22,80,443,5432,6379',
    attempts: 50,
  },
  dataAccess: {
    users: 3,
    volume: 'high',
  },
};

// Test result tracking
export interface TestResult {
  name: string;
  passed: boolean;
  duration: number;
  error?: string;
  details?: Record<string, unknown>;
}

export class TestResults {
  results: TestResult[] = [];

  add(result: TestResult) {
    this.results.push(result);
  }

  get passed() {
    return this.results.filter((r) => r.passed).length;
  }

  get failed() {
    return this.results.filter((r) => !r.passed).length;
  }

  get total() {
    return this.results.length;
  }

  print() {
    console.log('\n=== Test Results ===');
    console.log(`Total: ${this.total}, Passed: ${this.passed}, Failed: ${this.failed}`);
    console.log('');
    this.results.forEach((r) => {
      const status = r.passed ? '✓ PASS' : '✗ FAIL';
      console.log(`${status} - ${r.name} (${r.duration}ms)`);
      if (r.error) {
        console.log(`  Error: ${r.error}`);
      }
    });
  }
}

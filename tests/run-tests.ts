// Main test runner for SentinelX end-to-end tests

import { TestResults } from './e2e/test-config';
import { AttackTests } from './e2e/attack-tests';
import { SecurityTests } from './security/security-tests';
import { SimulationTests } from './simulation/simulation-tests';
import { KafkaTests } from './integration/kafka-tests';

async function runTests() {
  console.log('╔════════════════════════════════════════════════════════════╗');
  console.log('║        SentinelX End-to-End Test Suite                    ║');
  console.log('╚════════════════════════════════════════════════════════════╝');
  console.log('');

  const results = new TestResults();

  // Security Tests
  console.log('── Security Tests ──');
  const securityTests = new SecurityTests(results);
  await securityTests.testJWT();
  await securityTests.testRBAC();
  await securityTests.testDataMasking();
  await securityTests.testAuditLogging();

  // Simulation Tests
  console.log('\n── Simulation Tests ──');
  const simTests = new SimulationTests(results);
  await simTests.authenticate();
  await simTests.testSimulationEventLimit();
  await simTests.testSimulationDurationLimit();
  await simTests.testSimulationCancellation();
  await simTests.testConcurrentSimulationLimit();
  await simTests.testSSEStream();

  // Kafka Integration Tests
  console.log('\n── Kafka Integration Tests ──');
  const kafkaTests = new KafkaTests(results);
  await kafkaTests.testKafkaProduce();
  await kafkaTests.testKafkaConsume();
  await kafkaTests.testKafkaEndToEnd();

  // Attack Scenario Tests
  console.log('\n── Attack Scenario Tests ──');
  const attackTests = new AttackTests(results);
  await attackTests.authenticate();
  await attackTests.testNormalTraffic();
  await attackTests.testBruteForce();
  await attackTests.testAccountTakeover();
  await attackTests.testPaymentFraud();
  await attackTests.testTransactionVelocity();
  await attackTests.testApiAbuse();
  await attackTests.testBotActivity();
  await attackTests.testNetworkScan();
  await attackTests.testDataAccess();
  await attackTests.testMixedAttack();

  // Print results
  results.print();

  // Exit with appropriate code
  process.exit(results.failed > 0 ? 1 : 0);
}

runTests().catch((error) => {
  console.error('Test suite failed:', error);
  process.exit(1);
});

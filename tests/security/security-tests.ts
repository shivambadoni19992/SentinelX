// Security tests for JWT, RBAC, masking, and audit

import { TEST_CONFIG, TestResults } from '../e2e/test-config';

export class SecurityTests {
  private results: TestResults;

  constructor(results: TestResults) {
    this.results = results;
  }

  private async makeRequest(url: string, options: RequestInit = {}): Promise<Response> {
    return fetch(url, { ...options, headers: { 'Content-Type': 'application/json', ...(options.headers as Record<string, string>) } });
  }

  // Test JWT token generation and validation
  async testJWT(): Promise<void> {
    const start = Date.now();
    try {
      // Login to get token
      const loginRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/login`, {
        method: 'POST',
        body: JSON.stringify(TEST_CONFIG.adminUser),
      });

      if (!loginRes.ok) throw new Error('Login failed');

      const loginData = await loginRes.json();
      const token = loginData.token;
      const hasToken = !!token;
      const isJWT = token && token.split('.').length === 3;

      // Use token to access protected resource
      const protectedRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/me`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      const tokenValid = protectedRes.ok;

      // Test with invalid token
      const invalidRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/me`, {
        headers: { Authorization: 'Bearer invalid_token' },
      });
      const invalidTokenRejected = invalidRes.status === 401;

      this.results.add({
        name: 'JWT - Token generation and validation',
        passed: hasToken && isJWT && tokenValid && invalidTokenRejected,
        duration: Date.now() - start,
        details: { hasToken, isJWT, tokenValid, invalidTokenRejected },
      });
    } catch (error) {
      this.results.add({
        name: 'JWT - Token generation and validation',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test RBAC - Role-Based Access Control
  async testRBAC(): Promise<void> {
    const start = Date.now();
    try {
      // Login as customer (low privilege)
      const customerRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/login`, {
        method: 'POST',
        body: JSON.stringify(TEST_CONFIG.testUser),
      });
      const customerToken = customerRes.ok ? (await customerRes.json()).token : null;

      // Customer should NOT access admin endpoints
      const adminRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/users`, {
        headers: customerToken ? { Authorization: `Bearer ${customerToken}` } : {},
      });
      const customerBlockedFromAdmin = adminRes.status === 403;

      // Login as admin (high privilege)
      const adminLoginRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/login`, {
        method: 'POST',
        body: JSON.stringify(TEST_CONFIG.adminUser),
      });
      const adminToken = adminLoginRes.ok ? (await adminLoginRes.json()).token : null;

      // Admin SHOULD access admin endpoints
      const adminAccessRes = await this.makeRequest(`${TEST_CONFIG.authUrl}/api/auth/users`, {
        headers: adminToken ? { Authorization: `Bearer ${adminToken}` } : {},
      });
      const adminCanAccess = adminAccessRes.ok;

      this.results.add({
        name: 'RBAC - Role-based access control',
        passed: customerBlockedFromAdmin && adminCanAccess,
        duration: Date.now() - start,
        details: { customerBlockedFromAdmin, adminCanAccess },
      });
    } catch (error) {
      this.results.add({
        name: 'RBAC - Role-based access control',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test data masking
  async testDataMasking(): Promise<void> {
    const start = Date.now();
    try {
      // Create a payment
      const paymentRes = await this.makeRequest(`${TEST_CONFIG.paymentUrl}/api/payments`, {
        method: 'POST',
        body: JSON.stringify({
          customerId: 'test-customer',
          merchantId: 'test-merchant',
          amount: 99.99,
          currency: 'USD',
          deviceId: 'device-1234567890',
          ipAddress: '192.168.1.100',
        }),
      });

      if (!paymentRes.ok) throw new Error('Payment creation failed');

      const payment = await paymentRes.json();

      // Verify device ID is masked
      const deviceMasked = payment.deviceId && payment.deviceId.includes('*');

      // Verify IP address is masked
      const ipMasked = payment.ipAddress && payment.ipAddress.includes('*');

      this.results.add({
        name: 'Data Masking - PII protection',
        passed: deviceMasked && ipMasked,
        duration: Date.now() - start,
        details: { deviceMasked, ipMasked },
      });
    } catch (error) {
      this.results.add({
        name: 'Data Masking - PII protection',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }

  // Test audit logging
  async testAuditLogging(): Promise<void> {
    const start = Date.now();
    try {
      // Perform an action that should be audited
      const actionRes = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/alerts/test-alert/action`, {
        method: 'POST',
        body: JSON.stringify({
          action: 'ACKNOWLEDGE',
          actor: 'test-user',
        }),
      });

      // Check if audit log was created
      const auditRes = await this.makeRequest(`${TEST_CONFIG.baseUrl}/api/audit-logs?resourceType=ALERT&action=ACKNOWLEDGE`);
      const auditData = auditRes.ok ? await auditRes.json() : [];
      const hasAuditEntry = auditData.length > 0;

      this.results.add({
        name: 'Audit - Action logging',
        passed: hasAuditEntry,
        duration: Date.now() - start,
        details: { auditEntries: auditData.length },
      });
    } catch (error) {
      this.results.add({
        name: 'Audit - Action logging',
        passed: false,
        duration: Date.now() - start,
        error: error instanceof Error ? error.message : 'Unknown error',
      });
    }
  }
}

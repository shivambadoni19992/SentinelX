-- SentinelX Database Initialization
-- Create schemas and tables for all services

-- Create schema for each service
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS payments;
CREATE SCHEMA IF NOT EXISTS retail;
CREATE SCHEMA IF NOT EXISTS security;
CREATE SCHEMA IF NOT EXISTS risk;
CREATE SCHEMA IF NOT EXISTS alerts;
CREATE SCHEMA IF NOT EXISTS simulations;

-- Auth service tables
CREATE TABLE IF NOT EXISTS auth.users (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  username VARCHAR(255) UNIQUE NOT NULL,
  email VARCHAR(255) UNIQUE NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(50) NOT NULL DEFAULT 'CUSTOMER',
  account_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Payments service tables
CREATE TABLE IF NOT EXISTS payments.transactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  payment_id VARCHAR(100) UNIQUE NOT NULL,
  customer_id UUID NOT NULL,
  merchant_id VARCHAR(100) NOT NULL,
  amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  device_id VARCHAR(100),
  ip_address INET,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Retail service tables
CREATE TABLE IF NOT EXISTS retail.products (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  sku VARCHAR(100) UNIQUE NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT,
  category VARCHAR(100),
  price DECIMAL(10,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  stock INTEGER NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT true,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS retail.orders (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,
  status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  total_amount DECIMAL(12,2) NOT NULL,
  currency VARCHAR(3) NOT NULL DEFAULT 'USD',
  placed_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Security service tables
CREATE TABLE IF NOT EXISTS security.events (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event_type VARCHAR(100) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  user_id UUID,
  device_id VARCHAR(100),
  session_id VARCHAR(100),
  actor VARCHAR(255),
  action VARCHAR(255) NOT NULL,
  outcome VARCHAR(50) NOT NULL,
  source_ip INET,
  metadata JSONB,
  correlation_id VARCHAR(100),
  occurred_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS security.detections (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  rule_name VARCHAR(255) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  confidence DECIMAL(3,2) NOT NULL,
  event_ids UUID[],
  description TEXT,
  correlation_id VARCHAR(100),
  detected_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Risk service tables
CREATE TABLE IF NOT EXISTS risk.decisions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  subject_id UUID NOT NULL,
  subject_type VARCHAR(50) NOT NULL,
  rule_version VARCHAR(50),
  risk_level VARCHAR(20) NOT NULL,
  risk_score DECIMAL(3,2),
  factors JSONB,
  action VARCHAR(50) NOT NULL,
  correlation_id VARCHAR(100),
  decided_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Alerts service tables
CREATE TABLE IF NOT EXISTS alerts.alerts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  severity VARCHAR(20) NOT NULL,
  entity_type VARCHAR(50) NOT NULL,
  entity_id VARCHAR(100),
  event_id UUID,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  assigned_to VARCHAR(100),
  action VARCHAR(100),
  actor VARCHAR(100),
  action_detail JSONB,
  correlation_id VARCHAR(100),
  triggered_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Simulations service tables
CREATE TABLE IF NOT EXISTS simulations.runs (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  simulation_id VARCHAR(100) UNIQUE NOT NULL,
  campaign_id VARCHAR(100),
  name VARCHAR(255) NOT NULL,
  description TEXT,
  scenario VARCHAR(100) NOT NULL,
  type VARCHAR(100) NOT NULL,
  configuration JSONB,
  status VARCHAR(20) NOT NULL DEFAULT 'QUEUED',
  started_at TIMESTAMP WITH TIME ZONE,
  completed_at TIMESTAMP WITH TIME ZONE,
  events_generated INTEGER DEFAULT 0,
  events_processed INTEGER DEFAULT 0,
  detections INTEGER DEFAULT 0,
  risk_decisions INTEGER DEFAULT 0,
  alerts INTEGER DEFAULT 0,
  actions INTEGER DEFAULT 0,
  errors TEXT[],
  metrics JSONB,
  run_by VARCHAR(100),
  correlation_id VARCHAR(100),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Create indexes for common queries
CREATE INDEX IF NOT EXISTS idx_events_type ON security.events(event_type);
CREATE INDEX IF NOT EXISTS idx_events_severity ON security.events(severity);
CREATE INDEX IF NOT EXISTS idx_events_occurred ON security.events(occurred_at);
CREATE INDEX IF NOT EXISTS idx_events_correlation ON security.events(correlation_id);
CREATE INDEX IF NOT EXISTS idx_detections_rule ON security.detections(rule_name);
CREATE INDEX IF NOT EXISTS idx_detections_detected ON security.detections(detected_at);
CREATE INDEX IF NOT EXISTS idx_risk_decisions_level ON risk.decisions(risk_level);
CREATE INDEX IF NOT EXISTS idx_risk_decisions_decided ON risk.decisions(decided_at);
CREATE INDEX IF NOT EXISTS idx_alerts_status ON alerts.alerts(status);
CREATE INDEX IF NOT EXISTS idx_alerts_severity ON alerts.alerts(severity);
CREATE INDEX IF NOT EXISTS idx_alerts_triggered ON alerts.alerts(triggered_at);
CREATE INDEX IF NOT EXISTS idx_simulations_status ON simulations.runs(status);
CREATE INDEX IF NOT EXISTS idx_simulations_type ON simulations.runs(type);
CREATE INDEX IF NOT EXISTS idx_simulations_created ON simulations.runs(created_at);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON payments.transactions(status);
CREATE INDEX IF NOT EXISTS idx_transactions_customer ON payments.transactions(customer_id);

-- Insert sample data
INSERT INTO auth.users (username, email, password_hash, role, account_status) VALUES
  ('admin', 'admin@sentinelx.io', 'hashed_password', 'ADMIN', 'ACTIVE'),
  ('analyst', 'analyst@sentinelx.io', 'hashed_password', 'SOC_ANALYST', 'ACTIVE'),
  ('auditor', 'auditor@sentinelx.io', 'hashed_password', 'AUDITOR', 'ACTIVE')
ON CONFLICT (username) DO NOTHING;

INSERT INTO retail.products (sku, name, description, category, price, stock) VALUES
  ('PROD-001', 'Premium Widget', 'High-quality widget', 'Electronics', 99.99, 100),
  ('PROD-002', 'Basic Gadget', 'Simple gadget', 'Electronics', 29.99, 500),
  ('PROD-003', 'Deluxe Item', 'Premium item', 'Accessories', 149.99, 50)
ON CONFLICT (sku) DO NOTHING;

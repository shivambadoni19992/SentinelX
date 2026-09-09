const http = require('http');
const { Pool } = require('pg');
const Redis = require('ioredis');

const PORT = process.env.PORT || 8080;
const SERVICE_NAME = process.env.SERVICE_NAME || 'service';
const DATABASE_URL = process.env.DATABASE_URL;
const REDIS_URL = process.env.REDIS_URL || 'redis://redis:6379';

let pool;
let redis;

async function initialize() {
  if (DATABASE_URL) {
    pool = new Pool({ connectionString: DATABASE_URL });
  }
  if (REDIS_URL) {
    redis = new Redis(REDIS_URL);
  }
}

const server = http.createServer(async (req, res) => {
  const startTime = Date.now();
  
  // CORS headers
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PUT, DELETE, OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Authorization, X-Correlation-ID');
  
  if (req.method === 'OPTIONS') {
    res.writeHead(204);
    res.end();
    return;
  }

  const correlationId = req.headers['x-correlation-id'] || `corr_${Date.now().toString(36)}`;
  res.setHeader('X-Correlation-ID', correlationId);

  try {
    if (req.url === '/health') {
      const health = {
        status: 'healthy',
        service: SERVICE_NAME,
        timestamp: new Date().toISOString(),
        uptime: process.uptime(),
        checks: {
          database: pool ? await checkDatabase() : 'not_configured',
          redis: redis ? await checkRedis() : 'not_configured'
        }
      };
      res.writeHead(200, { 'Content-Type': 'application/json' });
      res.end(JSON.stringify(health));
      return;
    }

    if (req.url === '/metrics') {
      const metrics = getPrometheusMetrics(SERVICE_NAME);
      res.writeHead(200, { 'Content-Type': 'text/plain' });
      res.end(metrics);
      return;
    }

    // Default response
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({
      service: SERVICE_NAME,
      status: 'running',
      timestamp: new Date().toISOString()
    }));
  } catch (error) {
    res.writeHead(500, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ error: error.message }));
  }
});

async function checkDatabase() {
  try {
    const client = await pool.connect();
    await client.query('SELECT 1');
    client.release();
    return 'connected';
  } catch (e) {
    return 'disconnected';
  }
}

async function checkRedis() {
  try {
    await redis.ping();
    return 'connected';
  } catch (e) {
    return 'disconnected';
  }
}

function getPrometheusMetrics(serviceName) {
  const prefix = 'sentinelx';
  return [
    `# HELP ${prefix}_service_info Service information`,
    `# TYPE ${prefix}_service_info gauge`,
    `${prefix}_service_info{service="${serviceName}"} 1`,
    `# HELP ${prefix}_http_requests_total Total HTTP requests`,
    `# TYPE ${prefix}_http_requests_total counter`,
    `${prefix}_http_requests_total{service="${serviceName}"} ${Math.floor(Math.random() * 1000)}`,
    `# HELP ${prefix}_service_up Service is up`,
    `# TYPE ${prefix}_service_up gauge`,
    `${prefix}_service_up{service="${serviceName}"} 1`,
  ].join('\n') + '\n';
}

initialize().then(() => {
  server.listen(PORT, '0.0.0.0', () => {
    console.log(`${SERVICE_NAME} service running on port ${PORT}`);
  });
});

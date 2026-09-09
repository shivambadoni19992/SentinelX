import { FormEvent, useState } from 'react';
import { AuthResponse, login } from '../auth';
import './Login.css';

interface LoginProps {
  onSuccess: (auth: AuthResponse) => void;
}

/**
 * Shared dev/demo password for every seeded account (see DevUserSeeder in the
 * auth-service). Shipped on the login screen so demos are frictionless.
 */
const DEMO_PASSWORD = 'SentinelX!Dev1';

const DEMO_ACCOUNTS: Array<{ username: string; role: string; hint: string }> = [
  { username: 'admin', role: 'ADMIN', hint: 'full access' },
  { username: 'analyst', role: 'SOC_ANALYST', hint: 'daily monitoring' },
  { username: 'engineer', role: 'SECURITY_ENGINEER', hint: 'incident response' },
  { username: 'support', role: 'SUPPORT', hint: 'ticket triage' },
  { username: 'auditor', role: 'AUDITOR', hint: 'compliance' },
];

/** SentinelX SOC console login screen. */
function Login({ onSuccess }: LoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [showPassword, setShowPassword] = useState(false);

  /** Pre-fill the form with a demo account. Leaves password at the shared one. */
  const useDemo = (demoUsername: string) => {
    setUsername(demoUsername);
    if (!password) setPassword(DEMO_PASSWORD);
    setError(null);
  };

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const auth = await login(username.trim(), password);
      onSuccess(auth);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed. Please try again.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="login-shell">
      <form className="login-card" onSubmit={handleSubmit}>
        <div className="login-brand">
          <img src="/sentinelx.svg" alt="SentinelX" width="44" height="44" />
          <div>
            <h1>SentinelX</h1>
            <span>Enterprise Security &amp; Risk Monitoring Platform</span>
          </div>
        </div>

        <label className="field">
          <span>Username</span>
          <input
            type="text"
            autoComplete="username"
            spellCheck={false}
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            placeholder="e.g. analyst"
            required
          />
        </label>

        <label className="field">
          <span>Password</span>
          <div className="password-wrap">
            <input
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="Your account password"
              required
            />
            <button
              type="button"
              className="password-toggle"
              onClick={() => setShowPassword((v) => !v)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
            >
              {showPassword ? 'Hide' : 'Show'}
            </button>
          </div>
        </label>

        {error && <div className="login-error" role="alert">{error}</div>}

        <button type="submit" className="login-submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <div className="demo-credentials" role="group" aria-label="Demo credentials">
          <div className="demo-credentials-head">
            <span className="demo-pill">DEMO</span>
            <span className="demo-credentials-title">Try it instantly</span>
          </div>

          <p className="demo-password-line">
            Shared password for all accounts:{' '}
            <code>{DEMO_PASSWORD}</code>
          </p>

          <div className="demo-account-list">
            {DEMO_ACCOUNTS.map((a) => (
              <button
                key={a.username}
                type="button"
                className="demo-account"
                onClick={() => useDemo(a.username)}
                title={`Fill ${a.username} / ${DEMO_PASSWORD}`}
              >
                <span className="demo-account-user">
                  <strong>{a.username}</strong>
                  <span className="demo-account-role">{a.role}</span>
                </span>
                <span className="demo-account-hint">{a.hint}</span>
              </button>
            ))}
          </div>

          <p className="login-hint">
            Click an account to autofill, then press <strong>Sign in</strong>. Other
            seeded dev accounts include <code>monitored</code> and <code>blocked</code>{' '}
            (authentication refused). Roles: ADMIN, SOC_ANALYST, SECURITY_ENGINEER,
            SUPPORT, AUDITOR.
          </p>
        </div>
      </form>
    </div>
  );
}

export default Login;
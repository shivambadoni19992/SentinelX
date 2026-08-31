import { FormEvent, useState } from 'react';
import { AuthResponse, login } from '../auth';
import './Login.css';

interface LoginProps {
  onSuccess: (auth: AuthResponse) => void;
}

/** SentinelX SOC console login screen. */
function Login({ onSuccess }: LoginProps) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

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
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            placeholder="Your account password"
            required
          />
        </label>

        {error && <div className="login-error" role="alert">{error}</div>}

        <button type="submit" className="login-submit" disabled={submitting}>
          {submitting ? 'Signing in…' : 'Sign in'}
        </button>

        <p className="login-hint">
          Development accounts: <code>admin</code>, <code>analyst</code>,
          <code> engineer</code>, <code>support</code>, <code>auditor</code>,
          <code> monitored</code>, <code>blocked</code>. See README for dev
          credentials. Roles: ADMIN, SOC_ANALYST, SECURITY_ENGINEER, SUPPORT, AUDITOR.
        </p>
      </form>
    </div>
  );
}

export default Login;
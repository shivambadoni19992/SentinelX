import { useCallback, useEffect, useMemo, useState } from 'react';
import { AuthResponse, CurrentUser, clearToken, fetchMe, getToken, setToken } from './auth';
import Login from './components/Login';
import { AppShell } from './components/layout/AppShell';
import { useHashRoute } from './hooks/useHashRoute';
import { Overview } from './pages/Overview';
import { Alerts } from './pages/Alerts';
import { Transactions } from './pages/Transactions';
import { Users } from './pages/Users';
import { ApiSecurity } from './pages/ApiSecurity';
import { NetworkSecurity } from './pages/NetworkSecurity';
import { RiskAnalysis } from './pages/RiskAnalysis';
import { AuditLogs } from './pages/AuditLogs';
import { SimulationCenter } from './pages/SimulationCenter';
import { SimulationHistory } from './pages/SimulationHistory';
import { SystemHealth } from './pages/SystemHealth';
import './App.css';

function App() {
  // undefined = restoring an existing session; null = logged out.
  const [user, setUser] = useState<CurrentUser | null | undefined>(undefined);
  const [gateway, setGateway] = useState<'UP' | 'DOWN' | 'unknown'>('unknown');
  const [refreshTick, setRefreshTick] = useState(0);
  const [refreshing, setRefreshing] = useState(false);
  const { route, navigate } = useHashRoute();

  // Restore the session from a stored token on first load, or log out.
  useEffect(() => {
    let cancelled = false;
    const token = getToken();
    if (!token) {
      if (!cancelled) setUser(null);
      return;
    }
    (async () => {
      try {
        const me = await fetchMe(token);
        if (!cancelled) setUser(me);
      } catch {
        if (!cancelled) {
          clearToken();
          setUser(null);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, []);

  const handleLoginSuccess = useCallback((auth: AuthResponse) => {
    setToken(auth.token);
    setUser(auth.user);
  }, []);

  const handleLogout = useCallback(() => {
    clearToken();
    setUser(null);
  }, []);

  // Poll live gateway health while authenticated.
  useEffect(() => {
    if (!user) return;
    let cancelled = false;
    const probe = async () => {
      try {
        const res = await fetch('/actuator/health');
        const json = (await res.json()) as { status?: string };
        if (!cancelled) setGateway(json?.status === 'UP' ? 'UP' : 'DOWN');
      } catch {
        if (!cancelled) setGateway('DOWN');
      }
    };
    probe();
    const id = setInterval(probe, 15_000);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [user]);

  const handleRefresh = useCallback(() => {
    setRefreshing(true);
    setRefreshTick((t) => t + 1);
    setTimeout(() => setRefreshing(false), 600);
  }, []);

  // Routes re-keyed on refreshTick so every page re-fetches on demand.
  const page = useMemo(() => {
    switch (route) {
      case 'alerts':
        return <Alerts key={refreshTick} />;
      case 'transactions':
        return <Transactions key={refreshTick} />;
      case 'users':
        return <Users key={refreshTick} />;
      case 'api-security':
        return <ApiSecurity key={refreshTick} />;
      case 'network-security':
        return <NetworkSecurity key={refreshTick} />;
      case 'risk-analysis':
        return <RiskAnalysis key={refreshTick} />;
      case 'audit-logs':
        return <AuditLogs key={refreshTick} />;
      case 'simulation-center':
        return <SimulationCenter key={refreshTick} user={user ?? undefined} />;
      case 'simulation-history':
        return <SimulationHistory key={refreshTick} />;
      case 'system-health':
        return <SystemHealth key={refreshTick} gateway={gateway} />;
      case 'overview':
      default:
        return <Overview key={refreshTick} onNavigate={navigate} />;
    }
  }, [route, refreshTick, gateway, navigate, user]);

  // Still restoring an existing session.
  if (user === undefined) {
    return (
      <div className="session-loading">
        <p className="muted">Restoring session…</p>
      </div>
    );
  }

  // Not authenticated → login screen.
  if (user === null) {
    return <Login onSuccess={handleLoginSuccess} />;
  }

  return (
    <AppShell
      route={route}
      onNavigate={navigate}
      user={user}
      gateway={gateway}
      onLogout={handleLogout}
      onRefresh={handleRefresh}
      refreshing={refreshing}
      alertCount={0}
    >
      {page}
    </AppShell>
  );
}

export default App;
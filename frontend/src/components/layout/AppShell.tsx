import type { CurrentUser } from '../../auth';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

export function AppShell({
  route,
  onNavigate,
  user,
  gateway,
  onLogout,
  onRefresh,
  refreshing,
  alertCount,
  children,
}: {
  route: string;
  onNavigate: (r: string) => void;
  user: CurrentUser;
  gateway: string;
  onLogout: () => void;
  onRefresh: () => void;
  refreshing: boolean;
  alertCount: number;
  children: React.ReactNode;
}) {
  return (
    <div className="shell">
      <Sidebar route={route} onNavigate={onNavigate} user={user} alertCount={alertCount} />
      <div className="shell-main">
        <Topbar
          route={route}
          user={user}
          gateway={gateway}
          onLogout={onLogout}
          onRefresh={onRefresh}
          refreshing={refreshing}
        />
        <main className="content">{children}</main>
        <footer className="footer">SentinelX · Enterprise Security Operations Platform · Synthetic SOC environment</footer>
      </div>
    </div>
  );
}
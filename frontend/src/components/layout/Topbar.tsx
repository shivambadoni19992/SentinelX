import type { CurrentUser } from '../../auth';
import { PAGE_TITLES } from '../../nav';

function gatewayLabel(status: string): { text: string; cls: string } {
  switch (status) {
    case 'UP':
      return { text: 'Gateway Online', cls: 'up' };
    case 'DOWN':
      return { text: 'Gateway Offline', cls: 'down' };
    default:
      return { text: 'Gateway …', cls: 'unknown' };
  }
}

export function Topbar({
  route,
  user,
  gateway,
  onLogout,
  onRefresh,
  refreshing,
}: {
  route: string;
  user: CurrentUser;
  gateway: string;
  onLogout: () => void;
  onRefresh: () => void;
  refreshing: boolean;
}) {
  const info = PAGE_TITLES[route] ?? PAGE_TITLES.overview;
  const gw = gatewayLabel(gateway);
  return (
    <header className="topbar">
      <div className="topbar-title">
        <h1>{info.title}</h1>
        <span>{info.subtitle}</span>
      </div>
      <div className="topbar-right">
        <span className={`gateway-pill ${gw.cls}`}>
          <span className="dot" /> {gw.text}
        </span>
        <button
          type="button"
          className="icon-btn"
          onClick={onRefresh}
          disabled={refreshing}
          title="Refresh data"
          aria-label="Refresh data"
        >
          <span className={`refresh-icon ${refreshing ? 'spin' : ''}`}>↻</span>
        </button>
        <div className="user-chip" title={`${user.username} · ${user.role} · ${user.accountStatus}`}>
          <span className="user-avatar">{user.username.slice(0, 1).toUpperCase()}</span>
          <span className="user-meta">
            <strong>{user.username}</strong>
            <em>{user.role}</em>
          </span>
        </div>
        <button type="button" className="logout-btn" onClick={onLogout}>
          Sign out
        </button>
      </div>
    </header>
  );
}
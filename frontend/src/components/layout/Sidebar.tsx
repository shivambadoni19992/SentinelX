import { NAV_ITEMS } from '../../nav';
import type { CurrentUser } from '../../auth';

const GROUPS: { key: string; label: string; ids: string[] }[] = [
  { key: 'overview', label: 'Overview', ids: ['overview'] },
  {
    key: 'investigate',
    label: 'Investigate',
    ids: NAV_ITEMS.filter((n) => n.group === 'investigate').map((n) => n.id),
  },
  {
    key: 'platform',
    label: 'Platform',
    ids: NAV_ITEMS.filter((n) => n.group === 'platform').map((n) => n.id),
  },
];

function shorten(s?: string, n = 22): string {
  if (!s) return '';
  return s.length > n ? `${s.slice(0, n - 1)}…` : s;
}

export function Sidebar({
  route,
  onNavigate,
  user,
  alertCount,
}: {
  route: string;
  onNavigate: (r: string) => void;
  user: CurrentUser;
  alertCount: number;
}) {
  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <img src="/sentinelx.svg" alt="SentinelX" width="30" height="30" />
        <div>
          <strong>SentinelX</strong>
          <span>Security Operations</span>
        </div>
      </div>

      <nav className="sidebar-nav">
        {GROUPS.map((group) => (
          <div key={group.key} className="nav-group">
            <span className="nav-group-label">{group.label}</span>
            {NAV_ITEMS.filter((n) => group.ids.includes(n.id)).map((item) => {
              const active = route === item.id;
              return (
                <button
                  key={item.id}
                  type="button"
                  className={`nav-item ${active ? 'active' : ''}`}
                  onClick={() => onNavigate(item.id)}
                >
                  <span className="nav-icon" aria-hidden>
                    {item.icon}
                  </span>
                  <span className="nav-label">{item.label}</span>
                  {item.id === 'alerts' && alertCount > 0 ? (
                    <span className="nav-count">{alertCount}</span>
                  ) : null}
                </button>
              );
            })}
          </div>
        ))}
      </nav>

      <div className="sidebar-footer">
        <div className="sidebar-user">
          <span className="user-avatar">{user.username.slice(0, 1).toUpperCase()}</span>
          <div className="user-meta">
            <strong>{user.username}</strong>
            <em>{shorten(user.role)}</em>
          </div>
        </div>
      </div>
    </aside>
  );
}
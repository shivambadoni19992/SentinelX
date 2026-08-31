// Navigation model for the SOC console sidebar.

export interface NavItem {
  id: string;
  label: string;
  icon: string;
  group: 'overview' | 'investigate' | 'platform';
}

export const NAV_ITEMS: NavItem[] = [
  { id: 'overview', label: 'Overview', icon: '◈', group: 'overview' },
  { id: 'alerts', label: 'Alerts', icon: '⚑', group: 'investigate' },
  { id: 'transactions', label: 'Transactions', icon: '⇄', group: 'investigate' },
  { id: 'users', label: 'Users', icon: '◉', group: 'investigate' },
  { id: 'api-security', label: 'API Security', icon: '⌗', group: 'investigate' },
  { id: 'network-security', label: 'Network Security', icon: '⬡', group: 'investigate' },
  { id: 'risk-analysis', label: 'Risk Analysis', icon: '▲', group: 'investigate' },
  { id: 'audit-logs', label: 'Audit Logs', icon: '≡', group: 'platform' },
  { id: 'simulation-center', label: 'Simulation Center', icon: '▶', group: 'platform' },
  { id: 'simulation-history', label: 'Simulation History', icon: '◷', group: 'platform' },
  { id: 'system-health', label: 'System Health', icon: '✚', group: 'platform' },
];

export const PAGE_TITLES: Record<string, { title: string; subtitle: string }> = {
  overview: { title: 'Overview', subtitle: 'Enterprise-wide security posture at a glance' },
  alerts: { title: 'Security Alerts', subtitle: 'Triage and respond to detected events' },
  transactions: { title: 'Transactions', subtitle: 'Monitor payment activity and holds' },
  users: { title: 'Users', subtitle: 'Accounts, roles and risk exposure' },
  'api-security': { title: 'API Security', subtitle: 'Attacks, anomalies and key hygiene' },
  'network-security': { title: 'Network Security', subtitle: 'Traffic, scans and threats' },
  'risk-analysis': { title: 'Risk Analysis', subtitle: 'Model decisions and factor scores' },
  'audit-logs': { title: 'Audit Logs', subtitle: 'Immutable trail of platform actions' },
  'simulation-center': { title: 'Simulation Center', subtitle: 'Launch controlled security scenarios' },
  'simulation-history': { title: 'Simulation History', subtitle: 'Past runs and outcomes' },
  'system-health': { title: 'System Health', subtitle: 'Microservice availability and uptime' },
};
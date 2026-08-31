import { useMemo, useState } from 'react';
import { mockUsers } from '../api/mock';
import { listUsers } from '../api/endpoints';
import type { User } from '../api/types';
import { useCollection } from '../hooks/useCollection';
import { Card, StatCard } from '../components/ui/Card';
import { DataTable, type Column } from '../components/ui/DataTable';
import { Badge, StatusBadge } from '../components/ui/Badge';
import { DemoBanner, ErrorState } from '../components/ui/StateViews';
import {
  Toolbar,
  FilterSelect,
  SearchInput,
  ToolbarSpacer,
} from '../components/ui/Filter';
import { HBarChart } from '../components/charts/BarChart';

const ROLES = ['ADMIN', 'SOC_ANALYST', 'SECURITY_ENGINEER', 'AUDITOR', 'SUPPORT', 'CUSTOMER'];
const STATUSES = ['ACTIVE', 'MONITORED', 'BLOCKED'];

export function Users() {
  const { data, loading, source, error, demoReason, refetch } = useCollection(
    listUsers,
    mockUsers,
    [],
    { fallback: 'auto', demoLabel: 'users API unreachable' },
  );

  const [query, setQuery] = useState('');
  const [role, setRole] = useState('all');
  const [accountStatus, setAccountStatus] = useState('all');

  const filtered = useMemo(() => {
    const q = query.toLowerCase();
    return data.filter((u) => {
      const matchesQ =
        !q ||
        (u.username || '').toLowerCase().includes(q) ||
        (u.email || '').toLowerCase().includes(q);
      const matchesRole = role === 'all' || (u.role || '').toUpperCase() === role;
      const matchesStatus =
        accountStatus === 'all' || (u.accountStatus || '').toUpperCase() === accountStatus;
      return matchesQ && matchesRole && matchesStatus;
    });
  }, [data, query, role, accountStatus]);

  const rolesDist = useMemo(() => {
    const map: Record<string, number> = {};
    data.forEach((u) => {
      const k = (u.role || 'UNKNOWN').toUpperCase();
      map[k] = (map[k] ?? 0) + 1;
    });
    return Object.entries(map)
      .map(([label, value]) => ({ label, value }))
      .sort((a, b) => b.value - a.value);
  }, [data]);

  const columns: Column<User>[] = [
    {
      key: 'user',
      header: 'User',
      render: (u) => (
        <div className="cell-title">
          <span className="cell-primary">{u.username}</span>
          <span className="cell-secondary">{u.email}</span>
        </div>
      ),
    },
    { key: 'role', header: 'Role', render: (u) => <Badge tone={roleTone(u.role)}>{u.role}</Badge> },
    { key: 'status', header: 'Account', render: (u) => <StatusBadge status={u.accountStatus} /> },
    {
      key: 'created',
      header: 'Created',
      render: (u) => (
        <span className="muted" title={u.createdAt ? new Date(u.createdAt).toLocaleString() : undefined}>
          {u.createdAt ? new Date(u.createdAt).toLocaleDateString() : '—'}
        </span>
      ),
    },
  ];

  if (source === 'error') {
    return <ErrorState title="Users could not be loaded" message={error ?? 'Unknown error.'} onRetry={refetch} />;
  }

  return (
    <div className="page">
      {source === 'demo' ? <DemoBanner reason={demoReason ?? 'Showing sample data.'} /> : null}

      <div className="stat-grid">
        <StatCard label="Total Accounts" value={data.length} tone="info" icon="◉" />
        <StatCard
          label="Monitored"
          value={data.filter((u) => (u.accountStatus || '').toUpperCase() === 'MONITORED').length}
          tone="warn"
          icon="⚠"
        />
        <StatCard
          label="Blocked"
          value={data.filter((u) => (u.accountStatus || '').toUpperCase() === 'BLOCKED').length}
          tone="bad"
          icon="⛔"
        />
        <StatCard
          label="Privileged (Admin/Engineer)"
          value={data.filter((u) => ['ADMIN', 'SECURITY_ENGINEER'].includes((u.role || '').toUpperCase())).length}
          tone="violet"
          icon="⌘"
        />
      </div>

      <Card title="Accounts by Role">
        <HBarChart items={rolesDist} color="#a78bfa" />
      </Card>

      <Card
        title={`Users (${filtered.length})`}
        actions={
          <Toolbar>
            <SearchInput value={query} onChange={setQuery} placeholder="Search users…" />
            <FilterSelect
              label="Role"
              value={role}
              onChange={setRole}
              options={ROLES.map((r) => ({ value: r, label: r }))}
            />
            <FilterSelect
              label="Status"
              value={accountStatus}
              onChange={setAccountStatus}
              options={STATUSES.map((s) => ({ value: s, label: s }))}
            />
            <ToolbarSpacer />
            <button type="button" className="btn" onClick={refetch}>
              Refresh
            </button>
          </Toolbar>
        }
      >
        <DataTable columns={columns} data={filtered} rowKey={(u) => u.id} loading={loading} itemName="users" />
      </Card>
    </div>
  );
}

function roleTone(role?: string): 'info' | 'violet' | 'warn' | 'neutral' | 'good' {
  switch ((role ?? '').toUpperCase()) {
    case 'ADMIN':
      return 'violet';
    case 'SECURITY_ENGINEER':
      return 'info';
    case 'SOC_ANALYST':
      return 'warn';
    case 'AUDITOR':
      return 'good';
    default:
      return 'neutral';
  }
}
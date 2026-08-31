import type { ReactNode } from 'react';
import { Spinner } from './Spinner';
import { EmptyState } from './StateViews';

export interface Column<T> {
  key: string;
  header: ReactNode;
  className?: string;
  render: (row: T) => ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  rowKey: (row: T) => string;
  loading?: boolean;
  /** i.e. 'Alerts' -> 'No alerts yet'. */
  itemName?: string;
  emptyMessage?: string;
  onRowClick?: (row: T) => void;
  action?: ReactNode;
}

export function DataTable<T>({
  columns,
  data,
  rowKey,
  loading,
  itemName = 'records',
  emptyMessage,
  onRowClick,
  action,
}: DataTableProps<T>) {
  if (loading) {
    return (
      <div className="table-wrap">
        <Spinner label={`Loading ${itemName.toLowerCase()}…`} />
      </div>
    );
  }

  if (data.length === 0) {
    return (
      <EmptyState
        title={`No ${itemName.toLowerCase()} yet`}
        message={emptyMessage ?? 'There is nothing to display for the current filter.'}
        action={action}
      />
    );
  }

  return (
    <div className="table-wrap">
      <table className="data-table">
        <thead>
          <tr>
            {columns.map((c) => (
              <th key={c.key} className={c.className}>
                {c.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {data.map((row) => (
            <tr
              key={rowKey(row)}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              className={onRowClick ? 'clickable' : undefined}
            >
              {columns.map((c) => (
                <td key={c.key} className={c.className}>
                  {c.render(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
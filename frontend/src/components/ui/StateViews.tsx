// Consistent state views: empty, error and a demo-data banner.

export function EmptyState({
  icon = '▢',
  title = 'No records yet',
  message = 'There is nothing to display for this view.',
  action,
}: {
  icon?: React.ReactNode;
  title?: string;
  message?: string;
  action?: React.ReactNode;
}) {
  return (
    <div className="state empty-state">
      <div className="state-icon">{icon}</div>
      <h3>{title}</h3>
      <p>{message}</p>
      {action ? <div className="state-action">{action}</div> : null}
    </div>
  );
}

export function ErrorState({
  title = 'Something went wrong',
  message = 'We could not load this data. Please try again.',
  onRetry,
}: {
  title?: string;
  message?: string;
  onRetry?: () => void;
}) {
  return (
    <div className="state error-state" role="alert">
      <div className="state-icon">!</div>
      <h3>{title}</h3>
      <p>{message}</p>
      {onRetry ? (
        <button type="button" className="btn" onClick={onRetry}>
          Retry
        </button>
      ) : null}
    </div>
  );
}

/** Shown when a page is rendering synthetic data because the live API is down. */
export function DemoBanner({ reason }: { reason: string }) {
  return (
    <div className="demo-banner" role="status">
      <span className="demo-badge">DEMO</span>
      <span className="demo-text">Displaying synthetic data — the live endpoint is unavailable. {reason}</span>
    </div>
  );
}
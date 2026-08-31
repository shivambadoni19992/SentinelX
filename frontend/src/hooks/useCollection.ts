import { useCallback, useEffect, useRef, useState } from 'react';

export type Source = 'loading' | 'live' | 'demo' | 'empty' | 'error';

export interface Collection<T> {
  data: T[];
  loading: boolean;
  source: Source;
  /** Non-null only when source === 'error'. */
  error: string | null;
  /** Human-readable notice for the demo banner (when source === 'demo'). */
  demoReason: string | null;
  refetch: () => void;
}

interface Opts {
  /** 'auto' falls back to demo data when the live fetch fails; 'live-only' surfaces an error. */
  fallback: 'auto' | 'live-only';
  demoLabel: string;
}

/**
 * Fetches a typed collection from a live endpoint. Resolves loading / empty /
 * error states and, in 'auto' mode, degrades to a synthetic demo dataset
 * (clearly flagged) so the console stays populated while backend routes are
 * being wired up.
 */
export function useCollection<T>(
  fetcher: () => Promise<T[]>,
  demoData: T[],
  deps: React.DependencyList,
  opts: Opts,
): Collection<T> {
  const { fallback = 'auto', demoLabel } = opts;

  const [data, setData] = useState<T[] | null>(null);
  const [loading, setLoading] = useState(true);
  const [source, setSource] = useState<Source>('loading');
  const [error, setError] = useState<string | null>(null);
  const [demoReason, setDemoReason] = useState<string | null>(null);

  // Keep the latest fetcher/demo data for a stable refetch identity.
  const fetcherRef = useRef(fetcher);
  fetcherRef.current = fetcher;
  const demoRef = useRef(demoData);
  demoRef.current = demoData;

  const run = useCallback(async () => {
    setLoading(true);
    setError(null);
    setDemoReason(null);
    try {
      const result = await fetcherRef.current();
      if (result.length === 0) {
        setData([]);
        setSource('empty');
      } else {
        setData(result);
        setSource('live');
      }
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Request failed.';
      if (fallback === 'live-only') {
        setData([]);
        setSource('error');
        setError(message);
      } else {
        setData(demoRef.current);
        setSource('demo');
        setDemoReason(`${demoLabel}: ${message}`);
      }
    } finally {
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  // Refresh when deps change or on mount.
  useEffect(() => {
    run().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [run]);

  const refetch = useCallback(() => run(), [run]);

  return {
    data: data ?? [],
    loading,
    source,
    error,
    demoReason,
    refetch,
  };
}
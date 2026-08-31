import { useCallback, useEffect, useState } from 'react';

/** Lightweight hash-based router (no dependency). `route` has no leading '#'. */
export function useHashRoute(): { route: string; navigate: (r: string) => void } {
  const read = () => window.location.hash.replace(/^#\/?/, '').split('?')[0] || 'overview';
  const [route, setRoute] = useState<string>(read);

  useEffect(() => {
    const onChange = () => setRoute(read());
    window.addEventListener('hashchange', onChange);
    return () => window.removeEventListener('hashchange', onChange);
  }, []);

  const navigate = useCallback((r: string) => {
    window.location.hash = `/${r}`;
  }, []);

  return { route, navigate };
}
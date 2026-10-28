'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { apiMessage, isEndpointMissing } from '@/lib/api/common';

export type Load<T> =
  | { status: 'loading' }
  | { status: 'ready'; data: T }
  /** The server does not have this endpoint yet (404/405/501 without an API envelope). */
  | { status: 'unavailable' }
  | { status: 'error'; message: string };

/**
 * Loads data once on mount and again on `reload()`.
 * While reloading, the previous result stays on screen (`busy` is true) instead of flashing a skeleton.
 * `reload()` resolves `true` on success, `false` on failure, which is what <RefreshButton> wants.
 */
export function useAsync<T>(fetcher: () => Promise<T>) {
  const [state, setState] = useState<Load<T>>({ status: 'loading' });
  const [busy, setBusy] = useState(false);
  const alive = useRef(true);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
    };
  }, []);

  const reload = useCallback(async (): Promise<boolean> => {
    setBusy(true);
    try {
      const data = await fetcher();
      if (alive.current) setState({ status: 'ready', data });
      return true;
    } catch (err) {
      if (alive.current) setState(isEndpointMissing(err) ? { status: 'unavailable' } : { status: 'error', message: apiMessage(err, '') });
      return false;
    } finally {
      if (alive.current) setBusy(false);
    }
  }, [fetcher]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    reload();
  }, [reload]);

  /** Put local edits (e.g. after an optimistic update) into the loaded data. */
  const mutate = useCallback((update: (data: T) => T) => {
    setState((cur) => (cur.status === 'ready' ? { status: 'ready', data: update(cur.data) } : cur));
  }, []);

  return { state, busy, reload, mutate };
}

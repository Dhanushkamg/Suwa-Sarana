'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';

export type ThemeMode = 'light' | 'dark' | 'system';
export const THEME_KEY = 'suwa_theme';

interface ThemeContextValue {
  mode: ThemeMode;
  /** What is actually painted right now */
  resolved: 'light' | 'dark';
  setMode: (mode: ThemeMode) => void;
}

const ThemeContext = createContext<ThemeContextValue>({ mode: 'system', resolved: 'light', setMode: () => {} });

/**
 * Runs before first paint (inlined in <head>) so there is no flash of the wrong theme.
 * Keep in sync with ThemeProvider below.
 */
export const THEME_INIT_SCRIPT = `(function(){try{var m=localStorage.getItem('${THEME_KEY}')||'system';var d=m==='dark'||(m==='system'&&window.matchMedia('(prefers-color-scheme: dark)').matches);document.documentElement.setAttribute('data-theme',d?'dark':'light');}catch(e){}})();`;

function systemPrefersDark() {
  return typeof window !== 'undefined' && window.matchMedia('(prefers-color-scheme: dark)').matches;
}

export function ThemeProvider({ children }: { children: React.ReactNode }) {
  const [mode, setModeState] = useState<ThemeMode>('system');
  const [resolved, setResolved] = useState<'light' | 'dark'>('light');

  // Read the saved choice once on the client
  useEffect(() => {
    try {
      const saved = localStorage.getItem(THEME_KEY) as ThemeMode | null;
      if (saved === 'light' || saved === 'dark' || saved === 'system') setModeState(saved);
    } catch {
      // storage unavailable: keep following the system
    }
  }, []);

  // Apply the choice, and follow the OS while in "system" mode
  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)');
    const apply = () => {
      const next = mode === 'system' ? (systemPrefersDark() ? 'dark' : 'light') : mode;
      setResolved(next);
      document.documentElement.setAttribute('data-theme', next);
    };
    apply();
    media.addEventListener('change', apply);
    return () => media.removeEventListener('change', apply);
  }, [mode]);

  const setMode = useCallback((next: ThemeMode) => {
    setModeState(next);
    try {
      localStorage.setItem(THEME_KEY, next);
    } catch {
      // ignore
    }
  }, []);

  const value = useMemo(() => ({ mode, resolved, setMode }), [mode, resolved, setMode]);
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  return useContext(ThemeContext);
}

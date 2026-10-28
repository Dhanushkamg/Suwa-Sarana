'use client';

import { useCallback, useSyncExternalStore } from 'react';

/**
 * Low-Data Mode: one switch that every data-heavy part of the app honours.
 *
 *   const { lowData, setLowData } = useLowData();
 *   if (lowData) return <TextAlternative />;        // maps: list instead of loading Leaflet and tiles
 *   const ms = lowDataInterval(30_000, lowData);    // polling / SSE retry: 4x slower
 *   const calm = lowData || reducedMotion;          // animations: off
 *
 * The choice is saved in localStorage ("lowDataMode") and shared between tabs. When the person never chose,
 * the browser's own "Data Saver" hint (navigator.connection.saveData) turns it on. No provider is needed:
 * the hook reads a tiny module-level store, so any client component can use it.
 */

export const LOW_DATA_KEY = 'lowDataMode';

/** Multiplier applied to polling intervals while Low-Data Mode is on. */
export const LOW_DATA_INTERVAL_FACTOR = 4;

type Choice = 'on' | 'off' | null;

const listeners = new Set<() => void>();
let hydrated = false;
let choice: Choice = null;
let saveData = false;

function readChoice(): Choice {
  try {
    const raw = localStorage.getItem(LOW_DATA_KEY);
    if (raw === 'true') return 'on';
    if (raw === 'false') return 'off';
  } catch {
    // storage unavailable (private mode): the choice only lives for this page view
  }
  return null;
}

function readSaveData(): boolean {
  const connection = (navigator as Navigator & { connection?: { saveData?: boolean } }).connection;
  return connection?.saveData === true;
}

function current(): boolean {
  return choice === 'on' || (choice === null && saveData);
}

function applyToDocument() {
  // Lets CSS (and other engineers' components) react without importing the hook
  document.documentElement.dataset.lowData = String(current());
}

function emit() {
  applyToDocument();
  listeners.forEach((l) => l());
}

function hydrate() {
  if (hydrated || typeof window === 'undefined') return;
  hydrated = true;
  choice = readChoice();
  saveData = readSaveData();
  applyToDocument();
  window.addEventListener('storage', (e) => {
    if (e.key !== LOW_DATA_KEY) return;
    choice = readChoice();
    emit();
  });
}

function subscribe(listener: () => void) {
  hydrate();
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

function getSnapshot(): boolean {
  hydrate();
  return current();
}

function getReady(): boolean {
  hydrate();
  return hydrated;
}

function setLowData(next: boolean) {
  hydrate();
  choice = next ? 'on' : 'off';
  try {
    localStorage.setItem(LOW_DATA_KEY, String(next));
  } catch {
    // ignore: see readChoice
  }
  emit();
}

/**
 * Server and first client render always see `lowData === false` and `ready === false`, so there is no hydration mismatch;
 * the real value follows right after. Components that would START something expensive (load Leaflet, open a stream)
 * should wait for `ready` so they never begin work just before learning that Low-Data Mode is on.
 */
export function useLowData() {
  const lowData = useSyncExternalStore(subscribe, getSnapshot, () => false);
  const ready = useSyncExternalStore(subscribe, getReady, () => false);
  const set = useCallback((next: boolean) => setLowData(next), []);
  const toggle = useCallback(() => setLowData(!getSnapshot()), []);
  return { lowData, ready, setLowData: set, toggle };
}

/** Polling interval for the current mode: `lowDataInterval(30_000, lowData)` is 30 s normally and 120 s in Low-Data Mode. */
export function lowDataInterval(baseMs: number, lowData: boolean): number {
  return lowData ? baseMs * LOW_DATA_INTERVAL_FACTOR : baseMs;
}

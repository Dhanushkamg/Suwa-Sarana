'use client';

import { useEffect, useSyncExternalStore } from 'react';
import apiClient from '@/lib/apiClient';
import { lowDataInterval, useLowData } from '@/lib/lowData';
import { asDate, asList, isEndpointMissing, num, str, unwrap } from './common';

/**
 * Notification inbox endpoints (backend engineer is building them; until then the UI falls back to the live SSE feed only):
 *   GET  /api/notifications              -> [{ id, type, title, body, read, createdAt, referenceId? }]
 *   GET  /api/notifications/unread-count -> { count }   (a bare number or { unreadCount } also works)
 *   POST /api/notifications/{id}/read
 *   POST /api/notifications/read-all
 */
export const NOTIFICATION_PATHS = {
  list: '/notifications',
  unreadCount: '/notifications/unread-count',
  markRead: (id: number | string) => `/notifications/${id}/read`,
  readAll: '/notifications/read-all',
  stream: '/notifications/stream',
} as const;

export type NotificationKind = 'MATCH' | 'ESCALATION' | 'DONATION' | 'SYSTEM';

export interface InboxNotification {
  id: number;
  kind: NotificationKind;
  title?: string;
  body?: string;
  read: boolean;
  createdAt: Date | null;
  referenceId?: number;
}

export function toKind(type: unknown): NotificationKind {
  const v = String(type ?? '').toUpperCase();
  if (v.includes('MATCH')) return 'MATCH';
  if (v.includes('ESCALAT')) return 'ESCALATION';
  if (v.includes('DONAT')) return 'DONATION';
  return 'SYSTEM';
}

export async function fetchInbox(): Promise<InboxNotification[]> {
  const res = await apiClient.get(NOTIFICATION_PATHS.list);
  return asList<Record<string, unknown>>(res.data).map((n) => ({
    id: num(n.id),
    kind: toKind(n.type),
    title: str(n.title),
    body: str(n.body) ?? str(n.message),
    read: n.read === true || n.isRead === true || n.readAt != null,
    createdAt: asDate(n.createdAt ?? n.timestamp),
    referenceId: n.referenceId == null ? undefined : num(n.referenceId),
  }));
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(NOTIFICATION_PATHS.markRead(id));
}

export async function markAllNotificationsRead(): Promise<void> {
  await apiClient.post(NOTIFICATION_PATHS.readAll);
}

export async function fetchUnreadCount(): Promise<number> {
  const res = await apiClient.get(NOTIFICATION_PATHS.unreadCount);
  const body = unwrap<unknown>(res.data);
  if (typeof body === 'number') return body;
  const o = (body ?? {}) as Record<string, unknown>;
  return num(o.count ?? o.unreadCount ?? o.unread);
}

export { isEndpointMissing };

/* ------------------------------------------------------------------
   Shared unread counter: the sidebar badge and the inbox page read the same number.
   `available` is false until the server answers the count endpoint (404 -> stays false, no badge, no more polling).
------------------------------------------------------------------- */
interface UnreadState {
  count: number;
  available: boolean;
}
let state: UnreadState = { count: 0, available: false };
let unsupported = false;
const listeners = new Set<() => void>();

function set(next: UnreadState) {
  state = next;
  listeners.forEach((l) => l());
}

export function setUnreadCount(count: number) {
  set({ count: Math.max(0, count), available: true });
}

export function adjustUnreadCount(delta: number) {
  set({ count: Math.max(0, state.count + delta), available: state.available });
}

export async function refreshUnreadCount(): Promise<void> {
  if (unsupported) return;
  try {
    setUnreadCount(await fetchUnreadCount());
  } catch (err) {
    if (isEndpointMissing(err)) {
      unsupported = true;
      set({ count: 0, available: false });
    }
    // other failures (offline, 500): keep the last known number
  }
}

export function resetUnread() {
  unsupported = false;
  set({ count: 0, available: false });
}

/** Unread count for the badge, or null when the server cannot say. */
export function useUnreadCount(): number | null {
  const snapshot = useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => {
        listeners.delete(l);
      };
    },
    () => state,
    () => state
  );
  return snapshot.available ? snapshot.count : null;
}

/** Polls the unread count while the dashboard is open (60 s; 4x slower in Low-Data Mode; paused in background tabs). */
export function useUnreadPolling(enabled: boolean) {
  const { lowData } = useLowData();
  useEffect(() => {
    if (!enabled) return;
    refreshUnreadCount();
    const timer = window.setInterval(() => {
      if (!document.hidden) refreshUnreadCount();
    }, lowDataInterval(60_000, lowData));
    return () => window.clearInterval(timer);
  }, [enabled, lowData]);
}

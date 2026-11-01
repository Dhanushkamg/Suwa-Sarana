'use client';

import { useEffect, useSyncExternalStore } from 'react';
import apiClient from '@/lib/apiClient';
import { lowDataInterval, useLowData } from '@/lib/lowData';
import { asDate, asList, isEndpointMissing, num, str, unwrap } from './common';

/**
 * Notification inbox (docs/API-CONTRACT.md section 6):
 *   GET  /api/notifications?cursor=&unread=&limit=  -> { items: [...], nextCursor, unreadCount }   (newest first, keyset paging)
 *   GET  /api/notifications/unread-count            -> { count }
 *   POST /api/notifications/{id}/read               -> the notification
 *   POST /api/notifications/read-all                -> { updated }
 *   GET  /api/notifications/stream                  -> SSE, event name = type, id = notification id, data = one notification
 *     (send Last-Event-ID when reconnecting: @microsoft/fetch-event-source does that by itself from the `id:` lines)
 */
export const NOTIFICATION_PATHS = {
  list: '/notifications',
  unreadCount: '/notifications/unread-count',
  markRead: (id: number | string) => `/notifications/${id}/read`,
  readAll: '/notifications/read-all',
  stream: '/notifications/stream',
} as const;

/** How a notification looks (icon and colour); the text comes from titleKey/bodyKey. */
export type NotificationKind = 'MATCH' | 'ESCALATION' | 'DONATION' | 'SYSTEM';

/** Types the server sends today (docs/API-CONTRACT.md section 6). Unknown types are shown with the server's own title and body. */
export const NOTIFICATION_TYPES = [
  'NEW_MATCH',
  'MATCH_ACCEPTED',
  'CIRCLE_PLEDGE',
  'SLOT_CONFIRMED',
  'SLOT_REMINDER',
  'LOW_STOCK',
  'REQUEST_CLOSED',
  'REQUEST_EXPIRED',
  'REQUEST_FULFILLED',
  'REQUEST_REOPENED',
] as const;

const KIND_BY_TYPE: Record<string, NotificationKind> = {
  NEW_MATCH: 'MATCH',
  MATCH_ACCEPTED: 'DONATION',
  CIRCLE_PLEDGE: 'DONATION',
  REQUEST_FULFILLED: 'DONATION',
  SLOT_REMINDER: 'ESCALATION',
  LOW_STOCK: 'ESCALATION',
  REQUEST_EXPIRED: 'ESCALATION',
  REQUEST_REOPENED: 'ESCALATION',
};

export function toKind(type: unknown): NotificationKind {
  return KIND_BY_TYPE[String(type ?? '').toUpperCase()] ?? 'SYSTEM';
}

export type NotificationParams = Record<string, string | number | boolean | null>;

export interface InboxNotification {
  id: number;
  type: string;
  kind: NotificationKind;
  /** Message keys such as "notifications.match.title"; translate with the params. */
  titleKey?: string;
  bodyKey?: string;
  params: NotificationParams;
  /** English renderings by the server: the fallback when a key is unknown to this app. */
  title?: string;
  body?: string;
  read: boolean;
  readAt: Date | null;
  createdAt: Date | null;
  referenceId?: number;
}

/** One NotificationDto (inbox item or SSE event data). */
export function toNotification(n: Record<string, unknown>): InboxNotification {
  const params: NotificationParams = {};
  if (n.params && typeof n.params === 'object') {
    for (const [k, v] of Object.entries(n.params as Record<string, unknown>)) {
      params[k] = typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean' ? v : null;
    }
  }
  return {
    id: num(n.id),
    type: String(n.type ?? ''),
    kind: toKind(n.type),
    titleKey: str(n.titleKey),
    bodyKey: str(n.bodyKey),
    params,
    title: str(n.title),
    body: str(n.body) ?? str(n.message),
    read: n.readAt != null || n.read === true,
    readAt: asDate(n.readAt),
    createdAt: asDate(n.createdAt),
    referenceId: n.referenceId == null ? undefined : num(n.referenceId),
  };
}

type Translate = (key: string, values?: Record<string, string | number | Date>) => string;
type Format = { dateTime: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) => string };

/** Parameters as message values: codes become words in the UI language, moments become dates, null becomes an empty string. */
function messageValues(n: InboxNotification, t: Translate, format?: Format): Record<string, string | number> {
  const out: Record<string, string | number> = {};
  for (const [k, v] of Object.entries(n.params)) {
    if (v === null) out[k] = '';
    else if (typeof v === 'boolean') out[k] = String(v);
    else out[k] = v;
  }
  const tr = (key: string, value: unknown) => {
    const text = t(`${key}.${String(value)}`);
    return text === `${key}.${String(value)}` ? String(value) : text;
  };
  if (typeof n.params.urgency === 'string') out.urgency = tr('dash.urgency', n.params.urgency);
  if (typeof n.params.component === 'string') out.component = tr('dashUi.inv.component', n.params.component);
  if (n.type === 'LOW_STOCK' && typeof n.params.status === 'string') out.status = tr('dashUi.inv.status', n.params.status);
  // "start" is a zone-less Colombo wall-clock value ("2026-12-05T10:00"): shown as it is, in Asia/Colombo
  if (format && typeof n.params.start === 'string') out.start = format.dateTime(n.params.start, { dateStyle: 'medium', timeStyle: 'short' });
  return out;
}

function translated(t: Translate, key: string | undefined, values: Record<string, string | number>): string | undefined {
  if (!key) return undefined;
  const text = t(key, values);
  return text && text !== key ? text : undefined;
}

/**
 * Title and body of a notification in the UI language: t(titleKey, params), else the server's English title/body, else a generic line.
 * Unknown keys or types (a newer server) therefore still show something readable.
 */
export function notificationText(n: InboxNotification, t: Translate, format?: Format): { title: string; body: string } {
  const values = messageValues(n, t, format);
  const title = translated(t, n.titleKey, values) ?? n.title ?? t(n.kind === 'MATCH' ? 'notifications.newMatch' : n.kind === 'ESCALATION' ? 'notifications.escalation' : 'notifications.platformAlert');
  const body = translated(t, n.bodyKey, values) ?? n.body ?? '';
  return { title, body };
}

export interface InboxPage {
  items: InboxNotification[];
  /** Pass as `cursor` to get the next (older) page; null = no more. */
  nextCursor: number | null;
  unreadCount: number;
}

export const INBOX_PAGE_SIZE = 30;

export function toInboxPage(body: Record<string, unknown>): InboxPage {
  return {
    items: asList<Record<string, unknown>>(body.items).map(toNotification),
    nextCursor: body.nextCursor == null ? null : num(body.nextCursor),
    unreadCount: num(body.unreadCount),
  };
}

export async function fetchInbox(options: { cursor?: number | null; unread?: boolean; limit?: number } = {}): Promise<InboxPage> {
  const res = await apiClient.get(NOTIFICATION_PATHS.list, {
    params: {
      ...(options.cursor != null ? { cursor: options.cursor } : {}),
      unread: options.unread === true,
      limit: options.limit ?? INBOX_PAGE_SIZE,
    },
  });
  return toInboxPage((unwrap<Record<string, unknown>>(res.data) ?? {}) as Record<string, unknown>);
}

export async function markNotificationRead(id: number): Promise<void> {
  await apiClient.post(NOTIFICATION_PATHS.markRead(id));
}

export async function markAllNotificationsRead(): Promise<number> {
  const res = await apiClient.post(NOTIFICATION_PATHS.readAll);
  return num((unwrap<Record<string, unknown>>(res.data) ?? {}).updated);
}

/** GET /api/notifications/unread-count -> { count } */
export async function fetchUnreadCount(): Promise<number> {
  const res = await apiClient.get(NOTIFICATION_PATHS.unreadCount);
  const body = unwrap<unknown>(res.data);
  if (typeof body === 'number') return body;
  return num(((body ?? {}) as Record<string, unknown>).count);
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

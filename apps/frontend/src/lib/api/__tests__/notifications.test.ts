import { beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ get: vi.fn(), put: vi.fn(), post: vi.fn(), delete: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { formatterIn, translateIn } from '@/lib/i18n';
import { fetchInbox, fetchUnreadCount, markAllNotificationsRead, notificationText, toNotification, toInboxPage, NOTIFICATION_TYPES } from '../notifications';

const envelope = (data: unknown) => ({ data: { success: true, message: 'Success', data, timestamp: '2026-10-31T08:47:50.712Z' } });

/** Real NotificationDto shapes (NotificationInboxService / NotificationTemplates). */
const match = {
  id: 812, type: 'NEW_MATCH', titleKey: 'notifications.match.title', bodyKey: 'notifications.match.body',
  params: { requestId: 41, bloodType: 'O+', hospital: 'Karapitiya Teaching Hospital', district: 'Galle', urgency: 'URGENT', units: 3 },
  title: 'Urgent blood request', body: 'O+ blood needed at Karapitiya Teaching Hospital (Galle). Urgency: URGENT.',
  referenceId: 41, createdAt: '2026-10-31T04:05:00.000Z', readAt: null,
};
const lowStock = {
  id: 815, type: 'LOW_STOCK', titleKey: 'notifications.lowStock.title', bodyKey: 'notifications.lowStock.body',
  params: { bloodType: 'O-', component: 'RBC', status: 'CRITICAL', units: 4 },
  title: 'Low blood stock', body: 'O- RBC stock is CRITICAL: 4 units available.', referenceId: null, createdAt: '2026-10-31T05:00:00.000Z', readAt: '2026-10-31T05:30:00.000Z',
};
const slot = {
  id: 820, type: 'SLOT_CONFIRMED', titleKey: 'notifications.slot.confirmed.title', bodyKey: 'notifications.slot.confirmed.body',
  params: { host: 'National Blood Centre', start: '2026-12-05T10:00', bookingId: 77 },
  title: 'Slot Booked!', body: 'Your donation slot at National Blood Centre is confirmed for 2026-12-05T10:00. See My Bookings for your QR code.',
  referenceId: 77, createdAt: '2026-10-31T06:00:00.000Z', readAt: null,
};

beforeEach(() => vi.clearAllMocks());

describe('inbox', () => {
  it('asks for a keyset page and reads items, nextCursor and unreadCount', async () => {
    http.get.mockResolvedValueOnce(envelope({ items: [match, lowStock], nextCursor: 800, unreadCount: 7 }));
    const page = await fetchInbox({ cursor: 900, unread: true, limit: 20 });
    expect(http.get).toHaveBeenCalledWith('/notifications', { params: { cursor: 900, unread: true, limit: 20 } });
    expect(page.nextCursor).toBe(800);
    expect(page.unreadCount).toBe(7);
    expect(page.items.map((n) => [n.id, n.read, n.kind])).toEqual([[812, false, 'MATCH'], [815, true, 'ESCALATION']]);
    expect(page.items[1].readAt?.toISOString()).toBe('2026-10-31T05:30:00.000Z');
  });

  it('the last page has no cursor; the first request has no cursor parameter', async () => {
    http.get.mockResolvedValueOnce(envelope({ items: [], nextCursor: null, unreadCount: 0 }));
    const page = await fetchInbox();
    expect(http.get).toHaveBeenCalledWith('/notifications', { params: { unread: false, limit: 30 } });
    expect(page.nextCursor).toBeNull();
  });

  it('unread count and read-all', async () => {
    http.get.mockResolvedValueOnce(envelope({ count: 3 }));
    expect(await fetchUnreadCount()).toBe(3);
    http.post.mockResolvedValueOnce(envelope({ updated: 5 }));
    expect(await markAllNotificationsRead()).toBe(5);
    expect(http.post).toHaveBeenCalledWith('/notifications/read-all');
  });

  it('an SSE event payload is the same NotificationDto', () => {
    const n = toNotification(JSON.parse(JSON.stringify(match)));
    expect(n).toMatchObject({ id: 812, type: 'NEW_MATCH', referenceId: 41, read: false });
    expect(n.params.units).toBe(3);
    expect(toInboxPage({ items: [match] }).items).toHaveLength(1);
  });
});

describe('notificationText', () => {
  const en = (key: string, values?: Record<string, string | number | Date>) => translateIn('en', key, values);
  const si = (key: string, values?: Record<string, string | number | Date>) => translateIn('si', key, values);
  const ta = (key: string, values?: Record<string, string | number | Date>) => translateIn('ta', key, values);
  const fmt = formatterIn('en');

  it('renders titleKey/bodyKey with the params in the UI language, with the urgency word translated', () => {
    const text = notificationText(toNotification(match), en, fmt);
    expect(text.title).toBe('Urgent blood request');
    expect(text.body).toBe('O+ blood needed at Karapitiya Teaching Hospital (Galle). Urgency: Urgent.');
    const sinhala = notificationText(toNotification(match), si, formatterIn('si'));
    expect(sinhala.title).toBe('හදිසි රුධිර ඉල්ලීමක්');
    expect(sinhala.body).toContain('Karapitiya Teaching Hospital');
    expect(sinhala.body).not.toContain('URGENT');
  });

  it('translates the component and the status of a low-stock alert', () => {
    expect(notificationText(toNotification(lowStock), en, fmt).body).toBe('O- Red cells stock is Critical: 4 units available.');
  });

  it('shows a slot start in Colombo time as it was sent', () => {
    expect(notificationText(toNotification(slot), en, fmt).body).toMatch(/National Blood Centre is confirmed for .*10:00/);
  });

  it('falls back to the server English title and body for an unknown key or a newer type', () => {
    const future = toNotification({ id: 9, type: 'SOMETHING_NEW', titleKey: 'notifications.future.title', bodyKey: 'notifications.future.body', params: {}, title: 'Hello', body: 'World text', createdAt: '2026-10-31T04:05:00Z', readAt: null });
    expect(notificationText(future, ta, fmt)).toEqual({ title: 'Hello', body: 'World text' });
    expect(future.kind).toBe('SYSTEM');
  });

  it('falls back to a generic localised title when the server sent neither key nor text', () => {
    const bare = toNotification({ id: 10, type: 'NEW_MATCH', params: {}, createdAt: '2026-10-31T04:05:00Z', readAt: null });
    expect(notificationText(bare, en, fmt).title).toBe('New Blood Match Dispatch');
  });

  it('has a title and body translation for every key the server uses, in en, si and ta', () => {
    const keys = ['match', 'accepted', 'fulfilled', 'closed', 'expired', 'reopened', 'lowStock', 'pledge', 'generic', 'slot.confirmed'];
    const all = { bloodType: 'O+', hospital: 'H', district: 'D', urgency: 'U', accepted: 1, needed: 2, component: 'C', status: 'S', units: 3, volunteerName: 'V', pledgeCount: 1, title: 'T', body: 'B', host: 'X', start: 'S' };
    for (const t of [en, si, ta]) {
      for (const k of keys) {
        expect(t(`notifications.${k}.title`, all)).not.toBe(`notifications.${k}.title`);
        expect(t(`notifications.${k}.body`, all)).not.toBe(`notifications.${k}.body`);
      }
    }
    expect(NOTIFICATION_TYPES).toHaveLength(10);
  });
});

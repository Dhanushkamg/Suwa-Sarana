import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const http = vi.hoisted(() => ({ patch: vi.fn(), get: vi.fn(), post: vi.fn() }));
vi.mock('@/lib/apiClient', () => ({ default: http }));

import { I18nProvider, useI18n } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';
import { LANGUAGE_SYNC_DELAY_MS, LanguageSync } from '../LanguageSync';

function Switcher() {
  const { setLocale } = useI18n();
  return (
    <>
      <button onClick={() => setLocale('si')}>si</button>
      <button onClick={() => setLocale('ta')}>ta</button>
    </>
  );
}

function setup() {
  return render(
    <I18nProvider defaultLocale="en">
      <LanguageSync />
      <Switcher />
    </I18nProvider>
  );
}

beforeEach(() => {
  vi.useFakeTimers({ shouldAdvanceTime: true });
  http.patch.mockReset().mockResolvedValue({ data: {} });
  useAuthStore.setState({ user: { id: 1, email: 'a@b.lk', phoneNumber: '', role: 'DONOR', preferredLanguage: 'en' }, isAuthenticated: true, isInitialized: true, accessToken: 't' });
});

afterEach(() => {
  vi.useRealTimers();
  localStorage.clear();
});

describe('LanguageSync', () => {
  it('PATCHes the preferred language once, after the person stopped switching (debounced)', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    setup();
    await user.click(screen.getByText('si'));
    await user.click(screen.getByText('ta'));
    expect(http.patch).not.toHaveBeenCalled();
    await act(async () => {
      vi.advanceTimersByTime(LANGUAGE_SYNC_DELAY_MS + 50);
    });
    expect(http.patch).toHaveBeenCalledTimes(1);
    expect(http.patch).toHaveBeenCalledWith('/users/me', { preferredLanguage: 'ta' });
    expect(useAuthStore.getState().user?.preferredLanguage).toBe('ta');
  });

  it('does nothing for a signed-out visitor, and swallows a failure', async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    useAuthStore.setState({ user: null, isAuthenticated: false });
    setup();
    await user.click(screen.getByText('si'));
    await act(async () => {
      vi.advanceTimersByTime(LANGUAGE_SYNC_DELAY_MS + 50);
    });
    expect(http.patch).not.toHaveBeenCalled();

    useAuthStore.setState({ user: { id: 1, email: 'a@b.lk', phoneNumber: '', role: 'DONOR', preferredLanguage: 'en' }, isAuthenticated: true });
    http.patch.mockRejectedValueOnce(new Error('offline'));
    await user.click(screen.getByText('ta'));
    await act(async () => {
      vi.advanceTimersByTime(LANGUAGE_SYNC_DELAY_MS + 50);
    });
    expect(http.patch).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().user?.preferredLanguage).toBe('en'); // not updated locally when the server did not accept it
  });
});

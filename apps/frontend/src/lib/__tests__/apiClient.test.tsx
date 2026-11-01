import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it } from 'vitest';
import type { InternalAxiosRequestConfig } from 'axios';
import apiClient, { isPublicAuthPath } from '../apiClient';
import { I18nProvider, useI18n } from '../i18n';

/** Runs the request interceptors the way axios does and returns the final headers. */
function headersFor(url: string): Record<string, unknown> {
  const handlers = (apiClient.interceptors.request as unknown as { handlers: { fulfilled: (c: InternalAxiosRequestConfig) => InternalAxiosRequestConfig }[] }).handlers;
  let config = { url, headers: {} } as unknown as InternalAxiosRequestConfig;
  for (const h of handlers) if (h?.fulfilled) config = h.fulfilled(config);
  return config.headers as unknown as Record<string, unknown>;
}

function Picker() {
  const { setLocale } = useI18n();
  return <button onClick={() => setLocale('ta')}>ta</button>;
}

afterEach(() => localStorage.clear());

describe('apiClient', () => {
  it('sends Accept-Language with the language the person picked in the UI', async () => {
    const user = userEvent.setup();
    render(
      <I18nProvider defaultLocale="en">
        <Picker />
      </I18nProvider>
    );
    expect(headersFor('/requests')['Accept-Language']).toBe('en');
    await user.click(screen.getByText('ta'));
    expect(headersFor('/requests')['Accept-Language']).toBe('ta');
  });

  it('only public auth endpoints skip the token refresh; resend-verification needs a refreshed session', () => {
    for (const url of ['/auth/login', '/auth/register', '/auth/refresh', '/auth/logout', '/auth/send-otp', '/auth/verify-otp', '/auth/reset-password', '/auth/reset-password/token', '/auth/forgot-password', '/auth/verify-email', '/auth/accept-invite', '/auth/google/verify', '/auth/google/link']) {
      expect(isPublicAuthPath(url), url).toBe(true);
    }
    for (const url of ['/auth/resend-verification', '/users/me', '/users/me/change-password', '/requests', undefined]) {
      expect(isPublicAuthPath(url), String(url)).toBe(false);
    }
  });
});

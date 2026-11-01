'use client';

import { useEffect, useRef } from 'react';
import apiClient from '@/lib/apiClient';
import { onUserLocaleChange } from '@/lib/i18n';
import { useAuthStore } from '@/store/authStore';

/** Waits this long after the last language switch before telling the server (people often try all three). */
export const LANGUAGE_SYNC_DELAY_MS = 1500;

/**
 * Keeps `preferredLanguage` on the account (which decides the language of SMS and e-mail) in line with the language the
 * person picks in the UI. Best effort: a failure is ignored, the UI language is stored in the browser anyway.
 */
export function LanguageSync() {
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  useEffect(() => {
    const off = onUserLocaleChange((locale) => {
      if (timer.current) clearTimeout(timer.current);
      timer.current = setTimeout(() => {
        const { isAuthenticated, user, updateUser } = useAuthStore.getState();
        if (!isAuthenticated || !user || user.preferredLanguage === locale) return;
        apiClient
          .patch('/users/me', { preferredLanguage: locale })
          .then(() => updateUser({ preferredLanguage: locale }))
          .catch(() => {
            // best effort
          });
      }, LANGUAGE_SYNC_DELAY_MS);
    });
    return () => {
      off();
      if (timer.current) clearTimeout(timer.current);
    };
  }, []);

  return null;
}

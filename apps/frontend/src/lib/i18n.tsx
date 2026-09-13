'use client';

import React, { createContext, useContext, useState, useEffect, useCallback } from 'react';
import en from '../../messages/en.json';
import si from '../../messages/si.json';
import ta from '../../messages/ta.json';

export type Locale = 'en' | 'si' | 'ta';

const messageBundles: Record<Locale, Record<string, unknown>> = {
  en,
  si,
  ta,
};

interface I18nContextValue {
  locale: Locale;
  setLocale: (loc: Locale) => void;
  t: (key: string, params?: Record<string, string | number>) => string;
}

const I18nContext = createContext<I18nContextValue>({
  locale: 'en',
  setLocale: () => {},
  t: (key) => key,
});

function getNestedValue(obj: Record<string, unknown>, path: string): string {
  const keys = path.split('.');
  let current: unknown = obj;
  for (const key of keys) {
    if (typeof current !== 'object' || current === null) return path;
    current = (current as Record<string, unknown>)[key];
  }
  return typeof current === 'string' ? current : path;
}

export function I18nProvider({
  children,
  defaultLocale = 'en',
}: {
  children: React.ReactNode;
  defaultLocale?: Locale;
}) {
  const [locale, setLocaleState] = useState<Locale>(defaultLocale);

  useEffect(() => {
    try {
      const saved = localStorage.getItem('suwa_locale') as Locale;
      if (saved && (saved === 'en' || saved === 'si' || saved === 'ta')) {
        // eslint-disable-next-line
        setLocaleState(saved);
        document.documentElement.lang = saved;
      }
    } catch {
      // Ignore localStorage errors in restricted environments
    }
  }, []);

  const setLocale = useCallback((newLoc: Locale) => {
    setLocaleState(newLoc);
    try {
      if (typeof window !== 'undefined') {
        localStorage.setItem('suwa_locale', newLoc);
        document.documentElement.lang = newLoc;
      }
    } catch {
      // Ignore localStorage errors
    }
  }, []);

  const t = useCallback(
    (key: string, params?: Record<string, string | number>) => {
      const activeBundle = messageBundles[locale] || messageBundles.en;
      let text = getNestedValue(activeBundle, key);

      // Fallback to English if missing in current locale
      if (text === key && locale !== 'en') {
        text = getNestedValue(messageBundles.en, key);
      }

      if (params && typeof text === 'string') {
        Object.entries(params).forEach(([paramKey, val]) => {
          text = text.replace(new RegExp(`\\{${paramKey}\\}`, 'g'), String(val));
        });
      }
      return text;
    },
    [locale]
  );

  return (
    <I18nContext.Provider value={{ locale, setLocale, t }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}

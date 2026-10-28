'use client';

import React, { createContext, useContext, useState, useEffect, useCallback, useMemo } from 'react';
import { createTranslator, createFormatter } from 'use-intl/core';
import en from '../../messages/en.json';
import si from '../../messages/si.json';
import ta from '../../messages/ta.json';

export type Locale = 'en' | 'si' | 'ta';
export const LOCALES: Locale[] = ['en', 'si', 'ta'];
export const LOCALE_KEY = 'suwa_locale';

const messageBundles = { en, si, ta } as Record<Locale, Record<string, unknown>>;

type Values = Record<string, string | number | Date>;
type Translator = { (key: string, values?: Values): string; has(key: string): boolean };

// Regional formats: Sri Lanka uses 'en-LK' digits/dates; Sinhala and Tamil use their own locale data.
const INTL_LOCALE: Record<Locale, string> = { en: 'en-LK', si: 'si-LK', ta: 'ta-LK' };

interface I18nContextValue {
  locale: Locale;
  setLocale: (loc: Locale) => void;
  /**
   * Translate a dotted key. Values use ICU MessageFormat, so plurals work:
   *   "requests": "{count, plural, one {# request} other {# requests}}"  ->  t('x.requests', { count: 3 })
   * A key missing in the current language falls back to English, then to the key itself.
   */
  t: (key: string, values?: Values) => string;
  /** Locale-aware numbers and dates (Intl under the hood) */
  format: {
    number: (value: number, options?: Intl.NumberFormatOptions) => string;
    date: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) => string;
    dateTime: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) => string;
  };
}

const identity = (key: string) => key;
const I18nContext = createContext<I18nContextValue>({
  locale: 'en',
  setLocale: () => {},
  t: identity,
  format: {
    number: (v) => String(v),
    date: (v) => String(v),
    dateTime: (v) => String(v),
  },
});

function makeTranslator(locale: Locale): Translator {
  return createTranslator({
    locale: INTL_LOCALE[locale],
    messages: messageBundles[locale],
    // Missing keys are handled below (fallback to English); do not log noise at runtime
    onError: () => {},
    getMessageFallback: ({ namespace, key }) => (namespace ? `${namespace}.${key}` : key),
  }) as unknown as Translator;
}

const translators: Partial<Record<Locale, Translator>> = {};
function translatorFor(locale: Locale) {
  return (translators[locale] ??= makeTranslator(locale));
}

function isLocale(value: unknown): value is Locale {
  return value === 'en' || value === 'si' || value === 'ta';
}

export function I18nProvider({
  children,
  defaultLocale = 'en',
}: {
  children: React.ReactNode;
  defaultLocale?: Locale;
}) {
  const [locale, setLocaleState] = useState<Locale>(defaultLocale);

  // Restore the saved language after hydration
  useEffect(() => {
    try {
      const saved = localStorage.getItem(LOCALE_KEY);
      if (isLocale(saved)) {
        // eslint-disable-next-line react-hooks/set-state-in-effect
        setLocaleState(saved);
        document.documentElement.lang = saved;
      }
    } catch {
      // storage unavailable (private mode): stay on the default language
    }
  }, []);

  const setLocale = useCallback((next: Locale) => {
    setLocaleState(next);
    document.documentElement.lang = next;
    try {
      localStorage.setItem(LOCALE_KEY, next);
      // The API reads this to answer in the same language (see apiClient)
      document.cookie = `${LOCALE_KEY}=${next}; path=/; max-age=31536000; samesite=lax`;
    } catch {
      // ignore
    }
  }, []);

  const t = useCallback(
    (key: string, values?: Values) => {
      const current = translatorFor(locale);
      if (current.has(key)) return current(key, values);
      const english = translatorFor('en');
      if (locale !== 'en' && english.has(key)) return english(key, values);
      return key;
    },
    [locale]
  );

  const format = useMemo(() => {
    const f = createFormatter({ locale: INTL_LOCALE[locale], timeZone: 'Asia/Colombo' });
    return {
      number: (value: number, options?: Intl.NumberFormatOptions) => f.number(value, options as Parameters<typeof f.number>[1]),
      date: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) =>
        f.dateTime(new Date(value), (options ?? { dateStyle: 'medium' }) as Parameters<typeof f.dateTime>[1]),
      dateTime: (value: Date | string | number, options?: Intl.DateTimeFormatOptions) =>
        f.dateTime(new Date(value), (options ?? { dateStyle: 'medium', timeStyle: 'short' }) as Parameters<typeof f.dateTime>[1]),
    };
  }, [locale]);

  return <I18nContext.Provider value={{ locale, setLocale, t, format }}>{children}</I18nContext.Provider>;
}

export function useI18n() {
  return useContext(I18nContext);
}

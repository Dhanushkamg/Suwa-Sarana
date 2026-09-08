'use client';

import React, { createContext, useContext, useCallback } from 'react';

type Messages = Record<string, unknown>;

interface I18nContextValue {
  locale: string;
  t: (key: string) => string;
}

const I18nContext = createContext<I18nContextValue>({
  locale: 'en',
  t: (key) => key,
});

function getNestedValue(obj: Messages, path: string): string {
  const keys = path.split('.');
  let current: unknown = obj;
  for (const key of keys) {
    if (typeof current !== 'object' || current === null) return path;
    current = (current as Messages)[key];
  }
  return typeof current === 'string' ? current : path;
}

export function I18nProvider({
  locale,
  messages,
  children,
}: {
  locale: string;
  messages: Messages;
  children: React.ReactNode;
}) {
  const t = useCallback(
    (key: string) => getNestedValue(messages, key),
    [messages]
  );

  return (
    <I18nContext.Provider value={{ locale, t }}>
      {children}
    </I18nContext.Provider>
  );
}

export function useI18n() {
  return useContext(I18nContext);
}

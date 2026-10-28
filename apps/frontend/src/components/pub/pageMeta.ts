import { cookies } from 'next/headers';
import { createTranslator } from 'use-intl/core';
import type { Metadata } from 'next';
import en from '../../../messages/en.json';
import si from '../../../messages/si.json';
import ta from '../../../messages/ta.json';
// Not imported from lib/i18n: that module is a client module, and its constants are not readable from server code.
type Locale = 'en' | 'si' | 'ta';
const LOCALE_KEY = 'suwa_locale';

const bundles = { en, si, ta } as Record<Locale, Record<string, unknown>>;
const INTL: Record<Locale, string> = { en: 'en-LK', si: 'si-LK', ta: 'ta-LK' };

type Values = Record<string, string | number | Date>;
export type ServerTranslate = (key: string, values?: Values) => string;

/** Translator for server components/metadata. The language comes from the `suwa_locale` cookie the language switcher sets. */
export async function serverTranslator(): Promise<{ t: ServerTranslate; locale: Locale }> {
  const saved = (await cookies()).get(LOCALE_KEY)?.value;
  const locale: Locale = saved === 'si' || saved === 'ta' ? saved : 'en';
  const translate = (loc: Locale) =>
    createTranslator({ locale: INTL[loc], messages: bundles[loc] as never, onError: () => {}, getMessageFallback: ({ key }) => key }) as unknown as ServerTranslate & { has: (k: string) => boolean };
  const primary = translate(locale) as ServerTranslate & { has: (k: string) => boolean };
  const fallback = translate('en') as ServerTranslate & { has: (k: string) => boolean };
  const t: ServerTranslate = (key, values) => (primary.has(key) ? primary(key, values) : fallback.has(key) ? fallback(key, values) : key);
  return { t, locale };
}

/** Localised <title>/description for a page. Titles must not repeat the brand: the root layout's title template adds " | Suwa Sarana". */
export async function localizedMeta(titleKey: string, descriptionKey?: string): Promise<Metadata> {
  const { t } = await serverTranslator();
  return { title: t(titleKey), ...(descriptionKey ? { description: t(descriptionKey) } : {}) };
}

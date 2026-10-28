import { describe, expect, it } from 'vitest';
import { createTranslator } from 'use-intl/core';
import en from '../../../messages/en.json';
import si from '../../../messages/si.json';
import ta from '../../../messages/ta.json';

const bundles = { en, si, ta } as Record<string, Record<string, unknown>>;

function flatten(obj: Record<string, unknown>, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {};
  for (const [k, v] of Object.entries(obj)) {
    const key = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object') Object.assign(out, flatten(v as Record<string, unknown>, key));
    else out[key] = String(v);
  }
  return out;
}

const flat = Object.fromEntries(Object.entries(bundles).map(([l, b]) => [l, flatten(b)]));
const placeholders = (s: string) => [...s.matchAll(/\{(\w+)[,}]/g)].map((m) => m[1]).sort().join(',');

describe('translations', () => {
  for (const locale of ['si', 'ta']) {
    it(`${locale} has every key that en has (and no strays)`, () => {
      const missing = Object.keys(flat.en).filter((k) => !(k in flat[locale]));
      const extra = Object.keys(flat[locale]).filter((k) => !(k in flat.en));
      expect({ missing, extra }).toEqual({ missing: [], extra: [] });
    });

    it(`${locale} uses the same {placeholders} as en`, () => {
      const bad = Object.keys(flat.en).filter((k) => k in flat[locale] && placeholders(flat.en[k]) !== placeholders(flat[locale][k]));
      expect(bad).toEqual([]);
    });
  }

  for (const locale of ['en', 'si', 'ta']) {
    it(`${locale} messages are valid ICU (no syntax errors) and not empty`, () => {
      const errors: string[] = [];
      const t = createTranslator({
        locale,
        messages: bundles[locale] as never,
        onError: (e) => errors.push(e.message),
      }) as unknown as (key: string, values?: Record<string, unknown>) => string;
      const empty: string[] = [];
      for (const key of Object.keys(flat[locale])) {
        if (!flat[locale][key].trim()) empty.push(key);
        try {
          const values = Object.fromEntries([...flat[locale][key].matchAll(/\{(\w+)[,}]/g)].map((m) => [m[1], 1]));
          t(key, values);
        } catch (e) {
          errors.push(`${key}: ${(e as Error).message}`);
        }
      }
      expect({ errors, empty }).toEqual({ errors: [], empty: [] });
    });
  }
});

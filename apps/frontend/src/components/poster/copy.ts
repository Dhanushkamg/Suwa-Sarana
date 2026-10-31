import { translateIn } from '@/lib/i18n';
import type { PosterModel } from './model';
import { COPY_LIMITS, type PosterCopy, type PosterTemplate } from './types';

/**
 * Standard wording per template and language (messages `poster.copy.*`). Used whenever the AI copy is unavailable,
 * rejected by validateCopy, or not wanted. Calm on purpose: no scare language, no medical claims. Facts are
 * interpolated from the system data, never invented.
 */
export function fallbackCopy(template: PosterTemplate, model: PosterModel): PosterCopy {
  const { language } = model;
  const t = (key: string, values?: Record<string, string | number>) => translateIn(language, `poster.copy.${template}.${key}`, values);
  const values = {
    bloodType: model.bloodType ?? '',
    place: model.place ?? '',
    campName: model.campName ?? '',
    venue: model.venueText ?? model.venue ?? '',
    when: model.when ?? '',
    hasCamp: model.campName ? 'yes' : 'no',
  };

  const headline =
    template === 'URGENT_REQUEST' && model.units === undefined
      ? t('headlineNoUnits', values)
      : t('headline', { ...values, units: model.units ?? 0 });

  return {
    headline,
    body: t('body', values),
    caption: t('caption', values),
    hashtags: parseHashtags(t('hashtags')),
  };
}

// \p{M} and the zero-width joiner: Sinhala and Tamil vowel signs are combining marks
const HASHTAG = /^#[\p{L}\p{M}\p{N}_‍]{1,40}$/u;

/** "#a #b, c" -> ["#a", "#b", "#c"]: valid tags only, at most COPY_LIMITS.hashtags, no duplicates. */
export function parseHashtags(input: string | string[]): string[] {
  const parts = Array.isArray(input) ? input : input.split(/[\s,]+/);
  const seen = new Set<string>();
  const tags: string[] = [];
  for (const part of parts) {
    const raw = part.trim();
    if (!raw) continue;
    const tag = raw.startsWith('#') ? raw : `#${raw}`;
    if (!HASHTAG.test(tag) || seen.has(tag.toLowerCase())) continue;
    seen.add(tag.toLowerCase());
    tags.push(tag);
    if (tags.length >= COPY_LIMITS.hashtags) break;
  }
  return tags;
}

const digitsOnly = (s: string) => s.replace(/\D/g, '');

/**
 * Checks copy returned by the AI before it is shown. The model may only phrase facts the system supplied, so reject
 * (and let the caller fall back to the standard wording) when it:
 *  - is missing a field or is longer than the layout allows,
 *  - contains a link (the poster carries its own QR code and the caption gets the real link),
 *  - contains a phone number that is not the supplied contact number,
 *  - names a blood type that is not one of the supplied blood types, or a unit count different from the real one.
 */
export function validateCopy(raw: unknown, model: PosterModel): PosterCopy | null {
  if (!raw || typeof raw !== 'object') return null;
  const o = raw as Record<string, unknown>;
  const headline = typeof o.headline === 'string' ? o.headline.trim() : '';
  const body = typeof o.body === 'string' ? o.body.trim() : '';
  const caption = typeof o.caption === 'string' ? o.caption.trim() : '';
  if (!headline || !body || !caption) return null;
  if (headline.length > COPY_LIMITS.headline || body.length > COPY_LIMITS.body || caption.length > COPY_LIMITS.caption) return null;

  const all = `${headline}\n${body}\n${caption}`;
  if (/https?:\/\/|www\./i.test(all)) return null;

  const allowedPhone = model.contact ? digitsOnly(model.contact) : '';
  for (const match of all.matchAll(/\+?\d[\d\s-]{5,}\d/g)) {
    const digits = digitsOnly(match[0]);
    if (digits.length >= 7 && (!allowedPhone || !(allowedPhone.endsWith(digits) || digits.endsWith(allowedPhone)))) return null;
  }

  const allowedTypes = new Set(model.bloodTypes.map((b) => b.replace('−', '-').toUpperCase()));
  for (const match of all.matchAll(/(?<![A-Za-z])(AB|A|B|O)([+\-−])(?![A-Za-z0-9])/g)) {
    const type = `${match[1]}${match[2] === '+' ? '+' : '-'}`.toUpperCase();
    if (!allowedTypes.has(type)) return null;
  }

  if (model.units !== undefined) {
    for (const match of all.matchAll(/(\d+)\s*(?:units?|pints?|bags?)\b/gi)) {
      if (Number(match[1]) !== model.units) return null;
    }
  }

  const tags = Array.isArray(o.hashtags) ? parseHashtags(o.hashtags.filter((h): h is string => typeof h === 'string')) : [];
  return { headline, body, caption, hashtags: tags };
}

/** What "Copy caption" puts on the clipboard: caption, hashtags and the real link. */
export function captionForShare(copy: Pick<PosterCopy, 'caption' | 'hashtags'>, url: string): string {
  const tags = copy.hashtags.length ? `\n\n${copy.hashtags.join(' ')}` : '';
  return `${copy.caption.trim()}\n\n${url}${tags}`;
}

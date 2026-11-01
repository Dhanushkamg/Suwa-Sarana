import apiClient from '@/lib/apiClient';
import { retryAfterOf } from '@/lib/apiError';
import { asDate, unwrap } from './common';

/** FAQ chatbot endpoint. Kept in one place so the path can change without touching the UI. */
export const FAQ_PATHS = {
  ask: '/faq/ask',
} as const;

export const FAQ_MAX_QUESTION_LENGTH = 500;
/** The API accepts at most this many earlier messages as context. */
export const FAQ_MAX_HISTORY = 6;

export type FaqLanguage = 'en' | 'si' | 'ta';
export type FaqReplyStyle = 'auto' | 'script' | 'romanised';
/** SECONDARY = a source that is not the official one (shown, but never as "verified"). */
export type FaqSourceStatus = 'VERIFIED' | 'CONFLICT' | 'GAP' | 'SECONDARY';

/** Kinds of reply (docs/AI.md section 4). Only ANSWER can carry sources. */
export type FaqKind = 'ANSWER' | 'NO_ANSWER' | 'EMERGENCY' | 'OFF_TOPIC' | 'REFUSAL' | 'GREETING' | 'SMALL_TALK' | 'PLATFORM' | 'EMPTY';
export type FaqInputStyle = 'en' | 'si' | 'ta' | 'singlish' | 'tanglish' | 'unknown';

export interface FaqHistoryItem {
  role: 'user' | 'assistant';
  content: string;
}

export interface FaqSource {
  id: string;
  title: string;
  url: string;
  /** ISO timestamp, formatted by the UI with format.date */
  lastReviewed: string | null;
  status: FaqSourceStatus;
}

export interface FaqAnswer {
  answer: string;
  /** Language of the reply (never the deprecated `locale`). */
  language: FaqLanguage | null;
  kind: FaqKind | null;
  /** How the question was written (Sinhala letters, Singlish ...). */
  inputStyle: FaqInputStyle | null;
  /** True when the reply is an unreviewed draft translation (si / ta). `translationNote` is the localised notice. */
  translationDraft: boolean;
  translationNote: string | null;
  /** True only when at least one stored source was used. The green "Verified source" badge needs `grounded && !needsConfirmation`. */
  grounded: boolean;
  sources: FaqSource[];
  needsConfirmation: boolean;
  disclaimer: string;
  usedLlm: boolean;
  followUps: string[];
}

export interface FaqRequest {
  question: string;
  history?: FaqHistoryItem[];
  language?: FaqLanguage | 'auto';
  replyStyle?: FaqReplyStyle;
}

export type FaqErrorKind = 'offline' | 'rate_limit' | 'invalid' | 'server';

/** A failed FAQ call, classified so the chat can show the right message and offer a retry. */
export class FaqError extends Error {
  readonly kind: FaqErrorKind;
  /** Seconds to wait before retrying (rate limit only). */
  readonly retryAfterSeconds?: number;

  constructor(kind: FaqErrorKind, options: { retryAfterSeconds?: number } = {}) {
    super(`FAQ request failed: ${kind}`);
    this.name = 'FaqError';
    this.kind = kind;
    this.retryAfterSeconds = options.retryAfterSeconds;
  }
}

const DEFAULT_RETRY_SECONDS = 30;
const MAX_RETRY_SECONDS = 300;

/** Retry-After is either a number of seconds or an HTTP date. Always returns a sane positive number. */
export function parseRetryAfter(value: unknown, now: number = Date.now()): number {
  const raw = Array.isArray(value) ? value[0] : value;
  if (raw == null || raw === '') return DEFAULT_RETRY_SECONDS;
  const text = String(raw).trim();
  let seconds = Number(text);
  if (!Number.isFinite(seconds)) {
    const date = Date.parse(text);
    seconds = Number.isNaN(date) ? DEFAULT_RETRY_SECONDS : Math.ceil((date - now) / 1000);
  }
  return Math.min(MAX_RETRY_SECONDS, Math.max(1, Math.ceil(seconds)));
}

function readHeader(headers: unknown, name: string): unknown {
  if (!headers || typeof headers !== 'object') return undefined;
  const h = headers as { get?: (n: string) => unknown } & Record<string, unknown>;
  if (typeof h.get === 'function') {
    const viaGet = h.get(name);
    if (viaGet != null) return viaGet;
  }
  return h[name] ?? h[name.toLowerCase()];
}

/** Turns whatever axios threw into a FaqError. */
export function toFaqError(err: unknown): FaqError {
  if (err instanceof FaqError) return err;
  const response = (err as { response?: { status?: number; headers?: unknown; data?: unknown } } | null)?.response;
  if (!response) {
    return new FaqError('offline');
  }
  const status = response.status ?? 0;
  if (status === 429) {
    // data.retryAfterSeconds first (same number as the Retry-After header), then the header
    const seconds = retryAfterOf(response);
    return new FaqError('rate_limit', { retryAfterSeconds: seconds ? parseRetryAfter(seconds) : parseRetryAfter(readHeader(response.headers, 'retry-after')) });
  }
  if (status === 400 || status === 413 || status === 422) return new FaqError('invalid');
  return new FaqError('server');
}

const LANGUAGES: FaqLanguage[] = ['en', 'si', 'ta'];
const STATUSES: FaqSourceStatus[] = ['VERIFIED', 'CONFLICT', 'GAP', 'SECONDARY'];
const KINDS: FaqKind[] = ['ANSWER', 'NO_ANSWER', 'EMERGENCY', 'OFF_TOPIC', 'REFUSAL', 'GREETING', 'SMALL_TALK', 'PLATFORM', 'EMPTY'];
const INPUT_STYLES: FaqInputStyle[] = ['en', 'si', 'ta', 'singlish', 'tanglish', 'unknown'];

const text = (v: unknown): string => (typeof v === 'string' ? v : '');

/**
 * Normalises the /faq/ask payload. Tolerant on purpose (the backend is built in parallel): unknown fields are ignored,
 * missing ones get safe defaults. `grounded` is true ONLY for a literal `true`, and the deprecated
 * `groundedInRules` field is never read.
 */
export function normalizeFaqAnswer(body: unknown): FaqAnswer {
  const data = (unwrap<Record<string, unknown> | null>(body) ?? {}) as Record<string, unknown>;

  const sources: FaqSource[] = Array.isArray(data.sources)
    ? data.sources
        .filter((s): s is Record<string, unknown> => !!s && typeof s === 'object')
        .map((s, i) => ({
          id: text(s.id) || String(i),
          title: text(s.title),
          url: text(s.url),
          lastReviewed: asDate(s.lastReviewed)?.toISOString() ?? null,
          status: STATUSES.includes(s.status as FaqSourceStatus) ? (s.status as FaqSourceStatus) : 'VERIFIED',
        }))
        .filter((s) => s.title)
    : [];

  const language = LANGUAGES.includes(data.language as FaqLanguage) ? (data.language as FaqLanguage) : null;

  return {
    answer: text(data.answer).trim(),
    language,
    kind: KINDS.includes(data.kind as FaqKind) ? (data.kind as FaqKind) : null,
    inputStyle: INPUT_STYLES.includes(data.inputStyle as FaqInputStyle) ? (data.inputStyle as FaqInputStyle) : null,
    translationDraft: data.translationDraft === true,
    translationNote: text(data.translationNote).trim() || null,
    grounded: data.grounded === true,
    sources,
    needsConfirmation: data.needsConfirmation === true,
    disclaimer: text(data.disclaimer).trim(),
    usedLlm: data.usedLlm === true,
    followUps: Array.isArray(data.followUps)
      ? data.followUps.filter((q): q is string => typeof q === 'string' && q.trim().length > 0).map((q) => q.trim()).slice(0, 4)
      : [],
  };
}

/** Only http(s) links and same-site paths may become <a href>; anything else (javascript:, data:) is dropped. */
export function safeSourceUrl(url: string): string | null {
  if (!url) return null;
  if (url.startsWith('/') && !url.startsWith('//')) return url;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.toString() : null;
  } catch {
    return null;
  }
}

/** POST /api/faq/ask. Throws FaqError. */
export async function askFaq(request: FaqRequest): Promise<FaqAnswer> {
  try {
    const res = await apiClient.post<unknown>(FAQ_PATHS.ask, {
      question: request.question.slice(0, FAQ_MAX_QUESTION_LENGTH),
      history: (request.history ?? []).slice(-FAQ_MAX_HISTORY),
      language: request.language ?? 'auto',
      replyStyle: request.replyStyle ?? 'auto',
    });
    return normalizeFaqAnswer(res.data);
  } catch (err) {
    throw toFaqError(err);
  }
}

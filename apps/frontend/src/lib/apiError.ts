/**
 * Turns a failed API call into something a screen can show: a localised summary plus per-field messages.
 *
 * Backend envelope on failure (docs/API-CONTRACT.md 0.1):
 *   { success:false, message:"<English fallback>", code:"area.reason", data: null | {field: msg} | {retryAfterSeconds}, requestId }
 * - `code` is dotted lowercase ("auth.credentials.invalid", "rate.limited"); it is localised through
 *   the message key `authFlow.errors.<code>` (the dots are nested objects in messages/*.json).
 * - `data` of `validation.failed` is a field map. For the auth/account DTOs a field message is itself a code
 *   ("auth.password.tooShort"); for all other DTOs it is an English sentence ("must not be blank").
 * - The AI endpoints also put the code in `data.code`.
 * - 429 carries `data.retryAfterSeconds` and the `Retry-After` header.
 * Fallback order: translation of the code -> the server's English `message` -> a generic localised message.
 * This module has no React or i18n imports so it can be unit-tested and used from anywhere.
 */

export type Translate = (key: string, values?: Record<string, string | number | Date>) => string;

export interface ParsedApiError {
  /** Summary for the page-level alert (never the generic "Validation failed"). */
  message: string;
  /** Field name -> message, for validation failures. */
  fields: Record<string, string>;
  code?: string;
  status?: number;
  /** Seconds to wait (429, account lock-out, OTP cool-down); already appended to `message`. */
  retryAfterSeconds?: number;
  /** True when `message` is one of our translations, false when it is the server's English text or the caller's fallback. */
  localised: boolean;
}

export interface ParseOptions {
  /** Shown when nothing better is known. */
  fallback: string;
  /** Force a specific error code for an HTTP status, e.g. { 401: 'auth.credentials.invalid' } on the sign-in form. */
  byStatus?: Partial<Record<number, string>>;
  /** Rename server field names to the form's field names, e.g. { username: 'email' }. */
  fieldAliases?: Record<string, string>;
}

export const ERROR_PREFIX = 'authFlow.errors.';

/** A dotted server code ("auth.password.tooShort") or a legacy upper-case token ("PASSWORD_TOO_SHORT"). */
export const CODE_LIKE = /^(?:[a-z][A-Za-z0-9_]*(?:\.[A-Za-z0-9_]+)+|[A-Z][A-Z0-9_]+)$/;

/** Codes whose message gets the "try again in ..." sentence when the server sends a wait time. */
const WAIT_STATUSES = new Set([429]);

/** Translation of a code, or undefined when we have none (the key itself comes back from `t` for a missing key). */
export function localisedCode(t: Translate, code: string | undefined): string | undefined {
  if (!code) return undefined;
  const key = `${ERROR_PREFIX}${code}`;
  const text = t(key);
  return text === key ? undefined : text;
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

/** Seconds from `data.retryAfterSeconds` or the Retry-After header (delta-seconds form); undefined when absent or unusable. */
export function retryAfterOf(response: { headers?: unknown; data?: unknown } | undefined): number | undefined {
  const data = response?.data as { data?: { retryAfterSeconds?: unknown } } | undefined;
  const fromBody = Number(data?.data?.retryAfterSeconds);
  if (Number.isFinite(fromBody) && fromBody > 0) return Math.ceil(fromBody);
  const raw = readHeader(response?.headers, 'retry-after');
  const fromHeader = Number(Array.isArray(raw) ? raw[0] : raw);
  return Number.isFinite(fromHeader) && fromHeader > 0 ? Math.ceil(fromHeader) : undefined;
}

/** "Try again in 45 seconds." / "Try again in 3 minutes." */
export function retryText(t: Translate, seconds: number): string {
  if (seconds >= 90) return t(`${ERROR_PREFIX}retryInMinutes`, { minutes: Math.ceil(seconds / 60) });
  return t(`${ERROR_PREFIX}retryInSeconds`, { seconds });
}

export function parseApiError(err: unknown, t: Translate, options: ParseOptions): ParsedApiError {
  const response = (err as { response?: { status?: number; data?: unknown; headers?: unknown } } | null)?.response;
  const status = response?.status;

  if (!response) {
    const message = localisedCode(t, 'NETWORK');
    return { message: message ?? options.fallback, fields: {}, localised: message !== undefined };
  }

  const body = (response.data && typeof response.data === 'object' ? response.data : {}) as {
    message?: unknown;
    code?: unknown;
    data?: unknown;
  };

  const inner = body.data && typeof body.data === 'object' && !Array.isArray(body.data) ? (body.data as Record<string, unknown>) : undefined;
  const topCode = typeof body.code === 'string' && body.code ? body.code : undefined;
  const dataCode = typeof inner?.code === 'string' && inner.code ? inner.code : undefined;
  const rawCode = topCode ?? dataCode;
  const code = rawCode ?? (status !== undefined ? options.byStatus?.[status] : undefined);

  const retryAfterSeconds = status !== undefined && WAIT_STATUSES.has(status) ? retryAfterOf(response) : undefined;
  const withWait = (text: string) => (retryAfterSeconds ? `${text} ${retryText(t, retryAfterSeconds)}` : text);
  const base = { fields: {} as Record<string, string>, code, status, retryAfterSeconds };

  const fields: Record<string, string> = {};
  if (inner && code !== 'rate.limited') {
    for (const [name, value] of Object.entries(inner)) {
      if (name === 'code' || name === 'retryAfterSeconds') continue;
      if (typeof value !== 'string' || !value.trim()) continue;
      // A field message may itself be a code ("auth.password.tooShort"); otherwise it is the server's English text.
      let text = value;
      if (CODE_LIKE.test(value)) text = localisedCode(t, value) ?? localisedCode(t, 'validation.failed') ?? value;
      fields[options.fieldAliases?.[name] ?? name] = text;
    }
  }
  base.fields = fields;

  const byCode = localisedCode(t, code);
  if (byCode) return { ...base, message: withWait(byCode), localised: true };

  if (Object.keys(fields).length > 0) {
    return { ...base, message: localisedCode(t, 'validation.failed') ?? options.fallback, localised: true };
  }

  if (status === 429) {
    return { ...base, message: withWait(localisedCode(t, 'rate.limited') ?? options.fallback), localised: true };
  }
  if (status !== undefined && status >= 500) {
    return { ...base, message: localisedCode(t, 'internal.error') ?? options.fallback, localised: true };
  }

  const serverMessage = typeof body.message === 'string' ? body.message.trim() : '';
  // The server's English text is a safe fallback, but a bare code ("auth.xyz") is not
  if (serverMessage && !CODE_LIKE.test(serverMessage)) return { ...base, message: serverMessage, localised: false };
  return { ...base, message: options.fallback, localised: false };
}

/** Just the text: localised if we know the code, else the server's English message, else `fallback`. */
export function apiErrorText(err: unknown, t: Translate, fallback: string): string {
  return parseApiError(err, t, { fallback }).message;
}

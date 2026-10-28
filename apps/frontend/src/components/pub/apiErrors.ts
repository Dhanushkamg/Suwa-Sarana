/**
 * Turns a failed API call into something a form can show: a localised summary plus per-field messages.
 *
 * Backend envelope on failure: { success:false, message, code?, data?: { fieldName: message } }.
 * - `code` (when present) is localised through the key `authFlow.errors.<code>`.
 * - `data` (validation failures) is mapped onto the inputs by field name.
 * - Without a code we fall back to a status-based message (network, 429, 5xx) and finally to the server's own message.
 */

type Translate = (key: string, values?: Record<string, string | number | Date>) => string;

export interface ParsedApiError {
  /** Summary for the page-level alert (never the generic "Validation failed"). */
  message: string;
  /** Field name -> message, for validation failures. */
  fields: Record<string, string>;
  code?: string;
  status?: number;
  /** True when `message` is one of our translations (from a code or a status), false when it is the server's raw text or the fallback. */
  localised: boolean;
}

interface Options {
  /** Shown when nothing better is known. */
  fallback: string;
  /** Force a specific error code for an HTTP status, e.g. { 401: 'INVALID_CREDENTIALS' } on the sign-in form. */
  byStatus?: Partial<Record<number, string>>;
  /** Rename server field names to the form's field names, e.g. { username: 'email' }. */
  fieldAliases?: Record<string, string>;
}

const ERROR_PREFIX = 'authFlow.errors.';

function localisedCode(t: Translate, code: string | undefined): string | undefined {
  if (!code) return undefined;
  const key = `${ERROR_PREFIX}${code}`;
  const text = t(key);
  return text === key ? undefined : text;
}

export function parseApiError(err: unknown, t: Translate, options: Options): ParsedApiError {
  const response = (err as { response?: { status?: number; data?: unknown } } | null)?.response;
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

  const rawCode = typeof body.code === 'string' && body.code ? body.code : undefined;
  const code = rawCode ?? (status !== undefined ? options.byStatus?.[status] : undefined);

  const fields: Record<string, string> = {};
  if (body.data && typeof body.data === 'object' && !Array.isArray(body.data)) {
    for (const [name, value] of Object.entries(body.data as Record<string, unknown>)) {
      if (typeof value !== 'string' || !value.trim()) continue;
      // A field value may itself be a code (e.g. "PASSWORD_TOO_SHORT"); otherwise it is the server's text.
      const asCode = /^[A-Z][A-Z0-9_]+$/.test(value) ? localisedCode(t, value) : undefined;
      fields[options.fieldAliases?.[name] ?? name] = asCode ?? value;
    }
  }

  const byCode = localisedCode(t, code);
  if (byCode) return { message: byCode, fields, code, status, localised: true };

  if (Object.keys(fields).length > 0) {
    return { message: localisedCode(t, 'VALIDATION_FAILED') ?? options.fallback, fields, code, status, localised: true };
  }

  if (status === 429) return { message: localisedCode(t, 'TOO_MANY_REQUESTS') ?? options.fallback, fields, code, status, localised: true };
  if (status !== undefined && status >= 500) return { message: localisedCode(t, 'SERVER_ERROR') ?? options.fallback, fields, code, status, localised: true };

  const serverMessage = typeof body.message === 'string' ? body.message.trim() : '';
  return { message: serverMessage || options.fallback, fields, code, status, localised: false };
}

/** Moves keyboard focus to the first form control that reports an error (so screen-reader and keyboard users land on it). */
export function focusFirstInvalid(root: ParentNode = document) {
  const el = root.querySelector<HTMLElement>('[aria-invalid="true"]');
  el?.focus();
}

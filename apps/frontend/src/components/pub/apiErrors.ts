/**
 * Form-level error handling. The parsing itself lives in lib/apiError.ts (pure, unit-tested);
 * this file re-exports it for the screens and adds the focus helper.
 */
export { parseApiError, apiErrorText, retryText } from '@/lib/apiError';
export type { ParsedApiError, ParseOptions, Translate } from '@/lib/apiError';

/** Moves keyboard focus to the first form control that reports an error (so screen-reader and keyboard users land on it). */
export function focusFirstInvalid(root: ParentNode = document) {
  const el = root.querySelector<HTMLElement>('[aria-invalid="true"]');
  el?.focus();
}

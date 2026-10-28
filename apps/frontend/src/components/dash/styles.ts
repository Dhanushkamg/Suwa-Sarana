/** Shared class strings for the dashboard. Colours come from semantic tokens only (see globals.css). */

/** Small caption. Sinhala/Tamil get normal tracking because wide letter-spacing breaks their conjuncts. */
export const EYEBROW = 'eyebrow [:lang(si)_&]:tracking-normal [:lang(ta)_&]:tracking-normal';

export const EASE = [0.22, 1, 0.36, 1] as const;

/** The standard dashboard surface. */
export const CARD = 'rounded-2xl border border-line bg-surface shadow-[var(--shadow-card)]';

/** Row hover used by every list. */
export const ROW_HOVER = 'transition-colors duration-200 hover:bg-foreground/[0.04]';

/** Table header cell. Never truncates: long Sinhala/Tamil headings wrap. */
export const TH =
  'px-4 py-3 text-start text-xs font-semibold uppercase tracking-wide text-gray-500 [:lang(si)_&]:normal-case [:lang(ta)_&]:normal-case [:lang(si)_&]:tracking-normal [:lang(ta)_&]:tracking-normal';

/** Table body cell. */
export const TD = 'px-4 py-3.5 align-middle';

/** Link-styled text action (primary colour, underlined on hover). */
export const TEXT_LINK =
  'inline-flex min-h-11 items-center gap-1.5 rounded text-sm font-medium text-primary-text underline-offset-4 hover:underline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary';

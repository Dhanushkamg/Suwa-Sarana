/** Shared class strings for the dashboard. Colours come from tokens only (see DESIGN.md). */

/** Mono caption. Sinhala/Tamil get normal tracking because wide letter-spacing breaks their conjuncts. */
export const EYEBROW = 'eyebrow [:lang(si)_&]:tracking-normal [:lang(ta)_&]:tracking-normal';

export const EASE = [0.22, 1, 0.36, 1] as const;

/** Row hover used by every list: a faint bone wash. */
export const ROW_HOVER = 'transition-colors duration-200 hover:bg-foreground/[0.03]';

/** Table header cell. */
export const TH =
  'px-5 py-3.5 text-left font-mono text-[0.68rem] font-medium uppercase tracking-[0.12em] text-gray-500 [:lang(si)_&]:tracking-normal [:lang(ta)_&]:tracking-normal';

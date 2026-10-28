# Suwa Sarana — Design system

Calm, trustworthy and modern: the clarity of a good government service with an editorial touch (serif headlines, quiet
surfaces, restrained motion). Light, dark and "match system" themes share one set of **semantic tokens**; components never
contain raw colours.

## 1. Colour roles

| Role | Token / utility | Use | Never use for |
|---|---|---|---|
| Primary (blue) | `bg-primary text-primary-fg hover:bg-primary-hover`, `text-primary-text` (links), focus ring | Actions, active navigation, links, switches | Errors |
| Brand (crimson) | `bg-brand text-brand-fg`, `text-brand` | Logo mark, blood-type badges, the **one** urgent "Request blood" CTA per view | General buttons, nav, decoration |
| Danger | `text-danger-text`, `bg-danger-soft`, `border-danger/30`, `bg-danger` | Errors, destructive actions, critical shortage | Primary actions |
| Warning | `text-warning-text`, `bg-warning-soft` | Pending, low stock, expiring | – |
| Success | `text-success-text`, `bg-success-soft` | Verified, fulfilled, available | – |
| Info | `text-info-text`, `bg-info-soft` | Neutral notices | – |
| Surfaces | `bg-background`, `bg-surface`, `bg-surface-raised` | Page, cards, raised/inset areas | – |
| Text | `text-foreground`, `text-muted`, themed `text-gray-400/500` | Body, secondary, tertiary | `text-gray-600+` as readable text |
| Lines | `border-line` (hairline), `border-line-strong`, `border-control` (inputs/switches, ≥ 3:1) | Dividers, field borders | – |

Status is **never colour alone**: always icon + text + colour (`StatusBadge`, map markers, legends, charts).

Tokens live in `apps/frontend/src/app/globals.css` (light under `:root`, dark under `[data-theme="dark"]`);
Tailwind colour families (`red`, `amber`, `green`, `blue`, `gray`…) are remapped in the generated `theme.css` so legacy utilities
follow the theme (`gray-*` flips per theme; `white` means "text colour" in light mode). `pnpm check:contrast` verifies WCAG 2.2 AA
for every token pair in both themes and fails CI otherwise.

## 2. Theme system

- Modes: `light`, `dark`, `system` (default). `ThemeToggle` (radio group with text labels for screen readers) sits in every header.
- Choice persisted in `localStorage` (`suwa_theme`); an inline script in `<head>` sets `data-theme` before first paint → no flash.
- Maps: OSM tiles, dark theme applies `.dark-map-tiles`; markers/legends read token colours at runtime.

## 3. Typography

Instrument Serif (display, headings), Geist (UI), Geist Mono (small captions, numbers); **Noto Sans / Noto Serif Sinhala and Tamil**
are in the font stacks and download only when those scripts appear. Sinhala/Tamil need more vertical room: body `line-height 1.6`,
`.font-display` gets `line-height 1.3` and no negative tracking under `:lang(si|ta)`. Never fix heights on text, never truncate headings.

## 4. Components (`src/components/ui`)

`Button` (+`buttonClasses` for link buttons; variants primary/secondary/ghost/danger/brand; sizes with 44 px min height),
`Input`, `Select`, `Checkbox`, `Switch` (role=switch), `FormField` (label + help + error wiring), `Card`, `Table` (scrolls inside its own box),
`Tabs` (arrow-key navigation), `Breadcrumb`, `Dialog` (native `<dialog>`: focus trap, Esc, focus return), `Toast`/`useToast`
(polite/assertive live regions), `StatusBadge`, `Skeleton`, `EmptyState`, `SkipLink`. Brand/motion: `components/brand` (Logo, SriLankaMap),
`components/motion` (Reveal, AnimatedNumber, Spotlight, Marquee; all reduced-motion aware).

## 5. Rules

Do
- One primary button per view; secondary/ghost for the rest.
- Give every input a visible label, `autocomplete`, error text tied with `aria-describedby`, and a localised message.
- Provide loading (skeleton), empty (EmptyState) and error (retry) states for every async view; prevent double-submit.
- Use `t()` with ICU plurals and `format.number/date` — never concatenate or `toLocaleString()`.
- Keep touch targets ≥ 44 px, visible focus, keyboard-operable menus/dialogs, `prefers-reduced-motion`.

Don't
- Hard-code hex/rgb, use `text-white`/`bg-white/…` for themed surfaces, or use red for anything but errors/critical.
- Imply official NBTS/Ministry endorsement (no crests or ministry names without written permission).
- Put medical claims in UI strings; eligibility facts come from the versioned knowledge base with a cited source.

## 6. Accessibility checklist (per page, both themes)

Skip link and `main#main-content` · landmarks · heading order · form labels/autocomplete · focus management in dialogs/drawers ·
colour-independent status · contrast via script · axe (Playwright) clean · keyboard-only pass · reduced motion · 360 / 768 / 1024 / 1440 px with no horizontal scroll.

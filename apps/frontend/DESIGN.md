# Suwa Sarana — "Ink & Ember" design system

Warm near-black ink, bone-coloured type, one vermilion accent (ember) and a saffron highlight.
Editorial serif headlines, quiet hairline surfaces, restrained motion. No neon glows, no purple/blue AI gradients.

## Tokens (src/app/globals.css, theme.css)
- Surfaces: `bg-background` (ink), `bg-surface`, `bg-surface-raised`, `.glass-card` (hairline panel with top highlight)
- Lines: `border-line` (subtle), `border-line-strong`
- Text: `text-foreground` (bone), `text-gray-300/400/500/600` (warm stone)
- Accent: `bg-ember` / `text-ember` (vermilion), `text-saffron`; `red-*` and `rose-*` utilities are the ember ramp, `orange/amber` = saffron, `emerald/green/teal` = sage (success), `blue/sky/cyan` = muted mist, `purple/indigo/violet` = muted plum
- Fonts: `font-display` (Instrument Serif; Noto Serif for Sinhala/Tamil), default Geist, `font-mono` for captions and numbers, `.eyebrow` for small mono labels

## Building blocks
- `components/brand`: `Logo`, `LogoMark`, `SriLankaMap` (dot-matrix island with pulsing cities)
- `components/motion`: `Reveal` (scroll reveal), `AnimatedNumber`, `Spotlight` (cursor light), `Marquee`
- `components/ui`: `Button` (pill), `Input`, `Select`, `Skeleton`, `EmptyState`, `StatusBadge`

## Rules
- Animate `transform`/`opacity` only; respect `prefers-reduced-motion` (the motion components already do).
- Sinhala/Tamil strings are longer and taller: never fix heights on text blocks, never use `truncate` on headings.
- Every colour comes from a token above; no new hex values in components.

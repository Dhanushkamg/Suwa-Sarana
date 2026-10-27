# Suwa Sarana — Completion Plan

Source: *SUWA_SARANA_COMPLETION_PROPOSAL* (owner's brief) and the earlier status report. Findings live in
[`AUDIT.md`](AUDIT.md) and `docs/audit/*.md`. Work happens on branch `chore/completion`, in small commits authored as
the repository owner (`Dhanushka Madhushanka`). Nothing is pushed to `main` without the owner's go-ahead, and nothing
is deployed to AWS from here (no credentials; staging needs the owner's account).

## 0. What was already done before this plan (verified by running tests, not by trusting reports)

| Area | Done | Evidence |
|---|---|---|
| Security | Public sign-up can no longer create `ADMIN`; seeded demo logins disabled (V20); OTP lockout/cooldown/expiry; 401 for expired JWT; CORS allow-list; prod safety check | `AuthServiceTest`, `OtpServiceTest`, `AuthErrorStatusTest` (118 backend tests green) |
| Data leaks | `/donors/me/matches`, `/admin/reports` return DTOs; fraud fields admin-only | code review + tests |
| Booking/matching | slot cancel/rebook/ownership/bounds; match state guards; deferral after donation | `SlotBookingServiceTest`, `EscalationSchedulerTest` |
| Frontend | session restore on reload; role guards; forgot-password via phone OTP; i18n key parity (en/si/ta) | tsc, eslint, vitest, build |
| UI | "Ink & Ember" redesign (dark only) | screenshots at 1440 / 375 |

The redesign **conflicts with the proposal** on three points (dark-only, red as the primary colour, hard-coded
`white/…` utilities). Those are redone in Phase 5 (below) rather than defended.

## 1. Decisions taken (owner may overrule any of them)

| # | Decision | Reason |
|---|---|---|
| D1 | Colour system: **primary = blue-teal**, brand = restrained crimson (logo, blood-type badges, one urgent CTA), red/amber/green/blue strictly semantic; semantic tokens mapped separately for light and dark; no hard-coded hex in components | Proposal §5.2–5.3 |
| D2 | Keep the editorial type (Instrument Serif + Geist + Noto Sinhala/Tamil), motion and dot-matrix map from the redesign; only the palette/theming changes | Meets "modern" without throwing away working work |
| D3 | Themes: light / dark / system, toggle in header, persisted in `localStorage` + cookie-free pre-paint script (no flash) | §5.3 |
| D4 | i18n: keep the `useI18n().t()` call sites, back them with `use-intl` core (ICU plural/number/date) + `Accept-Language` sent to the API; backend messages localised through `MessageSource`; CI script fails on missing keys; `docs/i18n-glossary.md` | §3.3 without a 600-file rewrite |
| D5 | Roles at launch: Donor, Requester, Hospital, Blood bank, Admin (invite/seed only). No NBTS-staff role | §7 baseline; confirm in §14 |
| D6 | Branding: **no claim of NBTS/Ministry affiliation**; "NBTS grounded" badge only when an answer cites a stored source | §5.1, §4 |
| D7 | Chatbot: curated versioned knowledge base (JSON, each entry has source + last-reviewed + `needs_human_review`), lexical retrieval with Sinhala/Tamil/Singlish normalisation, LLM only phrases an answer from retrieved entries, deterministic fallback when the provider fails | §9.1 |
| D8 | Poster: deterministic HTML/SVG templates fed by system data; LLM only writes validated short copy; export PNG at 4 sizes with QR | §9.2 |
| D9 | Maps: OpenStreetMap-compliant tiles with attribution (URL configurable by env), `fitBounds` + `maxBounds` on Sri Lanka | §4 maps |
| D10 | Email: pluggable `EmailSender` (SMTP/SES via env) with a logging fallback; email verification + single-use hashed reset tokens | §3.5, §8 |
| D11 | Verification documents / S3 uploads, hospital staff sub-accounts, WhatsApp/Viber bots, ML forecasting: **deferred** (need AWS/S3, product decisions or are roadmap, not launch) — recorded in `AUDIT.md` with reasons | §0.6 "don't invent requirements" |

## 2. Phases and exit criteria

| Phase | Work | Exit criteria | Owner of work |
|---|---|---|---|
| 1 Audit | `docs/audit/{backend,frontend,ai}.md` → merged `AUDIT.md`; app run for real (Postgres 16 in Docker, backend, frontend) | AUDIT.md + this plan committed | done in this step |
| 2 Foundations | semantic tokens + light/dark + theme toggle; shared components (Toggle, Checkbox, Card, Badge, Table, Dialog, Toast, Tabs, Breadcrumb, FormField); `use-intl` plurals; Hikari pool, structured logs + request IDs, localised error envelope; demo-data seeder (all roles, 25 districts); CI (lint/type/unit/integration) | `pnpm lint/tsc/test/build` + `mvn test` green; app boots from clean clone | main agent + backend agent |
| 3 Auth & roles | login/register errors field-level + localised; Google fix + account linking; email verification/reset; lockout; RBAC matrix tests (allowed + forbidden per endpoint); role dashboards; contact reveal only after accept; data export/delete | permission test matrix passes | backend agent |
| 4 Core features | metrics correctness (heatmap, fulfilment, zero-data), plural bugs, real stats on donor profile, blood-bank inventory model, low-data mode actually used | numbers equal DB in an integration test; e2e flows | backend + frontend agents |
| 5 UI/UX | migrate every page to semantic tokens (no `text-white`, no `red-*` for primary), light/dark, 360/768/1024/1440, skip link, focus management, 44 px targets, status = icon+text+colour | axe clean in both themes on key pages | 3 frontend agents |
| 6 Multilingual | all strings keyed; Sinhala/Tamil wording pass with glossary; fonts/line-height; native-speaker review list | key-parity script green; `needs-review.md` produced | frontend agent |
| 7 AI | RAG chatbot + ≥100-question eval set; poster generator rebuild; triage transparency | retrieval pass-rate reported (≥90 % target); poster visual snapshots | AI agents |
| 8 Hardening | security fixes from AUDIT, privacy/consent/export/delete, perf (lazy maps, fonts), dependency audit | no Critical/High open | all |
| 9 AWS | Dockerfiles (multi-stage, non-root, health checks), compose, GitHub Actions with OIDC, `DEPLOYMENT-AWS.md` runbook; **no deploy** | docs + workflows reviewed by owner | main agent |

## 3. Working rules

- Verify before claiming: every "done" below is backed by a command that was run (`docs/TEST-REPORT.md`).
- Each wave of parallel work has disjoint file ownership; Maven/Next builds are run only by the main agent.
- Changelog: `docs/CHANGELOG-COMPLETION.md`. Final report: `docs/TEST-REPORT.md`.
- Credentials the owner must supply (not blocking local work): Groq/other AI key, Google OAuth client + secret and authorised origins, SMTP/SES, SMS gateway (Notify.lk / Dialog / Twilio), AWS account + domain, map tile key if a paid provider is chosen.

## 4. Open questions for the owner (answers change defaults above)

1. Launch roles and how each is verified (D5).
2. Any official NBTS/MoH affiliation (D6).
3. AI provider and budget; are Groq keys available (D7).
4. Authoritative NBTS eligibility documents + a native Sinhala/Tamil reviewer (§9.1).
5. Domain, AWS region (ap-south-1 or ap-southeast-1 for Sri Lanka latency), budget ceiling.
6. SMS/email providers; map tile provider preference.
7. Government stakeholder hosting/retention requirements; expected launch scale.

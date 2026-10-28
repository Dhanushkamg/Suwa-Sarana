# Suwa Sarana — Audit (Phase 1)

Consolidated from three code-reading audits plus the owner's proposal. Details, file:line references and fixes are in
[`audit/backend.md`](audit/backend.md) (40 findings, endpoint inventory, role matrix), [`audit/frontend.md`](audit/frontend.md)
(40 findings, UI checklist, colour/string/a11y audits) and [`audit/ai.md`](audit/ai.md) (24 findings, knowledge-base fact check).
Method: every "done" claim in the earlier status report was treated as unverified and checked against the code; items that
need a running system are marked **[verify]** and are exercised in Phase 4 against Postgres 16 (Docker) with Playwright.
The app was **not** run end-to-end for this audit: Postgres/Docker only became available afterwards.

## 1. Verdict on the earlier status report

| Claim | Verdict |
|---|---|
| Auth/RBAC "complete, production-ready" | **Partial.** Rotation + Google token verification exist; no email verification, no account lockout, Google sign-in links into unverified local accounts, a demo ADMIN row exists in every DB (B-F02, F05, F12) |
| Matching "production-ready, sub-millisecond index scans" | **False for the index claim.** The query shape defeats the GiST index; exact blood-type match only (no ABO/Rh compatibility); never run on Postgres in CI (B-F15, F16) |
| Notification cascade | **Stub.** SMS provider only logs and reports success; OTP and phone numbers are logged; SSE is single-node and fire-and-forget (B-F03, F09) |
| Escalation scheduler 5→50 km | **Partial.** Runs, but MATCHED requests are terminal, FULFILLED is never set (B-F08) |
| AI chatbot "NBTS grounded" | **False.** Badge is static; prompt allows general knowledge; fallback answers age but never weight (AI-01..03) |
| AI poster generator | **Not AI.** Fixed English template, no QR, no verification check (AI-04) |
| Analytics / heatmap | **Wrong at the source.** Fake demo numbers when empty, 100 % fulfilment for zero requests, unweighted national average (B-F14) |
| Tests "22 suites, high backend coverage" | **Weak where it matters.** CI runs H2 with Flyway off; the only end-to-end test is disabled; no role-matrix tests (B-F33) |

## 2. Findings by severity (counts; ids refer to the detailed reports)

| Severity | Count | Most important |
|---|---|---|
| Critical | 2 | B-F01 dev JWT secret ships as default and the prod guard never runs → forge ADMIN token; B-F02 demo admin/hospital accounts persist and are re-enabled by phone-OTP reset |
| High | 11 backend + 17 frontend + AI | SMS stub means nobody can register in prod (B-F03); rate limiting wrong behind a proxy + no lockout (F04, F05); AI endpoints unthrottled (F06); chatbot ungrounded/incomplete (F07, AI-01); request lifecycle never completes (F08); requester cannot see matches/confirm donations (F10); self-registered institutions publish camps unverified (F11); no email verification / Google pre-hijack (F12); demo data and runtime DDL in migrations/app code (F13); no theme mechanism and red-as-primary UI, primary button 2.96:1 contrast (FE-F1..F3); API dates may serialise as arrays (FE-F4, verify) |
| Medium | 15 backend + 18 frontend | analytics (F14), spatial index (F15), matching rules (F16), request spam (F17), IDOR on GET /requests/{id} (F18), verification workflow accountability (F19), moderation gaps (F20), unthrottled public writes (F21), IVR unsigned (F22), PDPA gaps (F23), PII in logs (F24), retention (F25), time zones (F26), scheduler double-fire (F27), camp/slot rules (F28), validation holes (F29), token hardening (F30), config hygiene (F31), observability (F32), test gaps (F33); maps without bounds/attribution/colour-only markers; no ICU plurals; hard-coded strings; a11y (skip link, dialog focus, targets < 44 px) |
| Low | 8 backend + 5 frontend | headers, dependency versions, API envelope consistency, enumeration, circle token storage, intake relocation, migration safety, duplicate UI primitives, copy |

## 3. Proposal items vs state (starting point for Phases 2-8)

| Proposal item | State before this work | Where it is handled |
|---|---|---|
| 3.1 Google auth | Token verification works; linking unsafe; env/OAuth values undocumented | Phase 3 (B), docs |
| 3.2 Every button works | Most do; Low-Data Mode is a no-op; Verify User takes a raw id; chips clip | Phase 5 (FE) — checklist in `audit/frontend.md` §1 |
| 3.3 Multilingual | Keys complete (0 missing) but no ICU, English flash, browser-locale dates, ~25 hard-coded strings | Phase 2 (done: ICU + tests) / 6 |
| 3.4 Chatbot | see AI-01..AI-24 | Phase 7 |
| 3.5 Login/registration | Generic "Validation failed", weak password policy, no lockout, no email verification | Phase 3 |
| 3.6 Roles | Hospital staff accounts, blood-bank inventory, CSV export, suspend/ban, announcements missing | Phase 3/4 (staff accounts + announcements deferred, see PLAN D11) |
| 3.7 Poster | Fixed template, English only | Phase 7 |
| 4 Screenshots | Mislabelled donor fields, red everywhere, white inputs, 1/ "1 requests", full-width bars, Carto key watermark (now OSM, bounds missing), data inconsistencies | Phase 4/5 |
| 5 Theme | Dark only | Phase 2 done (tokens + toggle + contrast script); pages migrating in Phase 5 |
| 8 Security | see backend §Section-8 table | Phase 3/8 |
| 9 AI | see ai.md | Phase 7 |
| 10 Testing | H2/Flyway off; no role matrix; frontend 1 test file | Phase 2-8 |
| 11 AWS | Dockerfiles + compose exist, no health checks / non-root issues / CI deploy | Phase 9 |

## 4. Open facts needing the owner (cannot be decided from code)

1. NBTS written donor-selection guideline for tattoos, medications, surgery, dental, breastfeeding, vaccination, alcohol; a ruling where a press interview (The Morning, 2 Dec 2025) conflicts with the NBTS website (first-time weight, malaria look-back, tattoos).
2. Whether "Raktha" (mentioned in knowledge-base entries) exists; correct emergency number for symptom replies.
3. Roles at launch and verification process (D5); official affiliation (D6).
4. Google OAuth client + secret and the production domain (authorised origins / redirect URIs).
5. SMS gateway account (Notify.lk / Dialog / Twilio), SMTP/SES, AI provider key and budget, AWS account/region/domain.
6. Native Sinhala/Tamil reviewer (see `i18n-review.md` once generated).
7. A real "contact" address/phone for the public contact page (currently placeholder).

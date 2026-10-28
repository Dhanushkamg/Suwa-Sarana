# Frontend audit (Next.js 16, `apps/frontend`)

Scope: read-only audit of the CURRENT code (post "Ink & Ember" redesign), checked against `docs/audit/_proposal.txt` (sections 3.2, 3.3, 4, 5, 6) and `apps/frontend/DESIGN.md`, plus the backend controllers/DTOs in `apps/backend/src/main/java/com/suwasarana/api`.
Nothing was built or run. Everything below comes from reading code and from four helper scripts. Where a conclusion depends on runtime behaviour, it is marked **(verify)**.

Helper scripts (scratchpad, `C:\Users\Kalana\AppData\Local\Temp\claude\E--INTERVIEW-PROJECTS-Suwa-Sarana\15238cc0-822a-4a23-8da5-9afd8da948eb\scratchpad\`):
`colour-audit.js`, `contrast.js`, `i18n-audit.js`, `strings-audit.js`. Their output is pasted below.

Legend for the checklist: **L/E/S** = has loading / error / success feedback (`-` = none). Status: OK / Suspicious / Broken.

---

## 0. Things that are already fixed or not what the proposal assumed

| Proposal claim | Current code |
|---|---|
| Donor profile label "Patient Blood Type" | Fixed. Donor page uses `dash.yourBloodType` ("Your blood type"). `request.patientBloodType` is only used on request screens. |
| Availability toggle with a location-pin icon | Icon is now a droplet (`donor/page.tsx:397`), but the label is still "Update availability" (`en.json:132`) and the location control is a separate button. |
| Carto basemap with "API KEY REQUIRED" watermark | No Carto/Mapbox URL anywhere (grep). All three maps use `https://{s}.tile.openstreetmap.org/...` directly (see section 6). The licensing problem is different, not gone. |
| "466K+ units/year" stat, 24 vs 25 districts | Not in code. `StatsBand.tsx` shows 25 / 8 / 3 and a live tile. Remaining 25-vs-"24" inconsistency: none found. |
| Admin district bars all full width | Bars scale to the max (`admin/page.tsx:185-194`), so the old symptom is gone; they are still full width if all counts are equal and there is no axis. |
| Dev indicator overlapping Sign out | Dev-only; sign out lives in the bottom of the sidebar (`DashShell.tsx:197`). Not verifiable without running. |

---

## 1. UI element checklist

Roles: G = guest, D = donor, R = requester, H = hospital, B = blood bank, A = admin.

### 1.1 Global / shared

| Page | Element | Expected | What the code does | L/E/S | Status |
|---|---|---|---|---|---|
| all (root layout) | Chatbot floating button | Opens assistant, not covering content | `DonorFaqChatbot.tsx:106` toggles local state. `fixed bottom-4 right-4 z-50` on every page, including over the donor Save bar (`sticky bottom-3 z-20`, `donor/page.tsx:384`) on mobile | -/-/- | Suspicious |
| all | Chatbot dialog (open) | Focus moves in, Esc closes, focus returns | `role="dialog"` only (line 123). No `aria-modal`, no focus move, no Esc handler, no focus return | - | Suspicious |
| all | Chatbot quick-reply chips (3) | Send the localised question | `handleSend(p.query)` -> `POST /faq/ask {question, locale}` (`:210`). Container is `flex overflow-x-auto`, chips `whitespace-nowrap shrink-0` (`:205-211`): clipped with no scroll cue. Any API error (incl. 400 for >500 chars, 429) shows the same "unable to reach engine" text (`:89-96`) | L (typing row) / E / - | Suspicious |
| all | Chatbot input + send | Submit question | `handleSend()` -> same endpoint. Input has no `maxLength` (backend max 500 -> 400 -> mis-labelled as "unreachable"). `focus:outline-none` with only a border colour change (`:233`) | L/E/- | Suspicious |
| all | Chatbot "NBTS Grounded" badge | Only when an answer was retrieved from a source | Static text (`:139-141`). Backend returns `groundedInRules` but the client never reads it; backend also sets it `true` even for free LLM answers (`DonorFaqAiService.java:101,116`) | - | Broken (claim not backed) |
| all | Chatbot close (X) | Close | `setIsOpen(false)`; 32px target | - | OK (small target) |
| landing, auth, pub, share, circle, dashboard | Language switcher | Switch EN/SI/TA, persist, set `lang` | `setLocale` -> `localStorage('suwa_locale')` + `documentElement.lang` (`i18n.tsx:60-70`). Restored only in `useEffect` (`:47-58`) so every load paints English first. `aria-label="Switch language"` / `"Language"` hard-coded English (`LanguageSwitcher.tsx:52,65`). `role="menu"` without arrow-key handling | - | Suspicious |
| dashboard | Sidebar nav links | Navigate, active state | `DashShell.tsx:158` `Link` + `aria-current`. Nested `aside[aria-label]` > `nav[aria-label]` with the same name | - | OK |
| dashboard | Mobile menu button / drawer | Open/close, focus in, Esc | `DashShell.tsx:78,53-69`: Esc, focus to close button, `inert` on main, scroll lock. No focus trap beyond `inert`; top bar stays reachable | - | OK |
| dashboard | **Sign out** | End session, clear cookie, go home | `onLogout` -> `authStore.logout()` -> `POST /auth/logout` (Bearer + cookie), clears state, `router.push('/')` (`layout.tsx:56-59`). Errors swallowed (`authStore.ts:68`) | - (no spinner) | OK |
| dashboard | Sidebar user block | Show email/role/verification | Display only; raw `user.email` | - | OK |

### 1.2 Landing `/` (G)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Skip link | Jump to `#main` | `LandingNav.tsx:46`, works. Exists ONLY on landing; no other layout has one | - | Suspicious |
| Nav links How it works / Features / Impact | Smooth anchor | `Link href="#how-it-works"` etc.; ids exist (`HowItWorks.tsx:27`, `Features.tsx:36`, `StatsBand.tsx:35`) | - | OK |
| Nav "Donation camps" | `/camps` | `Link` | - | OK |
| Sign in / Donate blood | `/login`, `/register` | `PillLink` | - | OK |
| Mobile menu toggle | Full-screen menu, Esc closes | `LandingNav.tsx:278`; Esc + scroll lock; no focus move/trap; menu overlay `z-0` under header | - | Suspicious |
| Hero "Register as donor" / "Request blood" | `/register`, `/register?role=REQUESTER` | `Hero.tsx:124,128`; register preselects role (`register/page.tsx:54`) | - | OK |
| Stock ticker | Show real shortages | `GET /inventory/stock-signals` every 5 min (`StockTicker.tsx:24`); failure -> `console.error`, renders nothing. No write endpoint for inventory exists in the backend, so data only comes from seeds | -/-/- | Suspicious |
| Closing CTA, footer links | Navigate | `ClosingCta.tsx:25,29`, `LandingFooter.tsx:9-13` | - | OK |

### 1.3 `/login` (G)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Email field | Label, `autocomplete="username"` | `autoComplete="email"` (`login/page.tsx:102`); `type=email required` -> browser-language native validation | - | Suspicious |
| Password + show/hide | `current-password`, toggle | `PasswordField` `autoComplete="current-password"` (`:119`); toggle has `aria-label`/`aria-pressed`, 44px | - | OK |
| Forgot password link | `/forgot-password` | `Link` in `labelAction` | - | OK |
| **Sign in** | Login, redirect by role | `POST /auth/login {username,email,password}` (`:57`) -> `login()` -> `homeForRole`. Error = `response.data.message` only; a 400 yields "Validation failed" with the per-field map in `data` discarded (`auth.ts:46-49`) | L/E/- | Suspicious |
| Google button | Sign in, or go to register for new users | `GoogleLogin` -> `POST /auth/google/verify {idToken}`; new user -> token in `sessionStorage`, `/register?google=1`. Button hidden silently if `NEXT_PUBLIC_GOOGLE_CLIENT_ID` is empty | L/E/- | OK |
| Register link / Home link | Navigate | Home link `hidden lg:inline-flex` (mobile uses logo) | - | OK |

### 1.4 `/register` (G)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Role select | Donor/Requester/Hospital/Blood bank only | 4 options, no ADMIN (`:90-95`); backend also rejects ADMIN (`AuthService.java:84`) | - | OK |
| Name/email/phone/NIC/password/confirm | Validate, autocomplete | Client validators (`:102-120`), `autoComplete` set on all but NIC. Password rule: length >= 8 only | -/E/- | OK |
| **Send OTP** | Send SMS, go to step 2 | `POST /auth/send-otp {phoneNumber:+94...}` | L/E/- | OK |
| OTP input | One-time-code | `autoComplete="one-time-code"`, `maxLength=6` | - | OK |
| **Verify & create** | Register + sign in | `POST /auth/verify-otp` then `POST /auth/register` (or `/auth/google/register`), `login()`, redirect | L/E/- | OK |
| Back / Resend | Return / resend after 60s | `setStep('info')`; `sendOtp` disabled while `resendIn>0` | - | OK |
| Google sign-up | Go to profile step | `POST /auth/google/verify` | L/E/- | OK |
| Terms/Privacy links | Navigate | `Link` | - | OK |

### 1.5 `/forgot-password` (G)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Phone + **Send code** | OTP by SMS | `POST /auth/send-otp` | L/E/- | OK |
| OTP + new password + **Reset** | Reset | `POST /auth/verify-otp` then `POST /auth/reset-password {phoneNumber,newPassword}` (matches `ResetPasswordDto`) | L/E/S (done card) | OK |
| Resend, Back to sign in | Work | 60s timer; `Link` | - | OK |

### 1.6 `/camps` (G, D)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Camp list | Donors see recommended camps | `role === 'DONOR' ? /camps/recommended : /camps` (`camps/page.tsx:144`). `initialize()` is never called on this page or its header, so after a reload `user` is null and a signed-in donor gets the generic list and "sign in as donor" on booking (`SlotPicker.tsx:61`) | L/E/- | Broken (after reload) |
| Retry ("Refresh") | Refetch | `fetchCamps` | - | OK |
| Show/hide slots | Expand SlotPicker | `aria-expanded` toggle | - | OK |
| Slot "Book" | Book slot | `POST /slots/{id}/book`; 409/422/401 mapped to messages; list polled every 15s | L/E/S | OK (needs auth bootstrap) |
| Date block | Localised date | `new Date(\`${iso}T00:00:00\`)`; falls back to raw string if unparsable (see F4) | - | Suspicious |

### 1.7 `/contact`, `/privacy`, `/terms` (G)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Contact form | Send message | `POST /contact` (matches `ContactRequestDto`), resets on success | L/E/S | OK |
| mailto / tel links | Real contact data | Hard-coded `support@suwasarana.lk`, `+94 11 234 5678` (`contact/page.tsx:44-51`) look like placeholders | - | Suspicious |
| Legal ToC | Scroll to section | Anchors + IntersectionObserver | - | OK |
| Back to home, footer | Navigate | `PubHeader`, `PubFooter` | - | OK |

### 1.8 `/share/[token]` and `/circle/[token]` (G)

| Page | Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|---|
| share | Card data | Server-rendered with 30s revalidate | `fetch(API_BASE_URL/circle/{token})` server-side with the **public** `NEXT_PUBLIC_API_URL` (`share/[token]/page.tsx:11`) - unreachable from a container if that is `localhost` | L/E/- | Suspicious |
| share | "Verified broadcast" label | Only for verified requesters | Unconditional (`ShareCardClient.tsx:191`) | - | Broken (claim) |
| share | Time left | Days included | `% 24` drops days (`:73`) | - | Broken (>24h) |
| share | Volunteer button/link | `/circle/{token}` | `Link` | - | OK |
| share | WhatsApp/Facebook/Telegram/Viber | Open share URLs | External `<a>`; text hard-coded English with emoji (`:96`) | - | Suspicious |
| share | Download image | PNG of OG image | `fetch('/share/{token}/opengraph-image')` -> blob download; failure uses `alert()` (`:120`) | -/E/- | Suspicious |
| share | Copy link | Copy text+URL | `navigator.clipboard.writeText` with no `catch` (`:99-103`); sr-only status | -/-/S | Suspicious |
| circle | Volunteer form | Register interest | `POST /circle/{token}/respond {responderName,responderPhone,bloodType,notes}`. Phone is sent raw; backend regex `^\+?[0-9]{10,15}$` rejects "077 123 4567" -> "Validation failed" (`CircleResponseClient.tsx:104-110`) | L/E/S | Suspicious |
| circle | Social card link, Register CTA | Navigate | `Link` | - | OK |
| circle | `isExpired` | Expired state | Backend serialises `expired` (getter `isExpired()`), client reads `isExpired`; benign because expired links return 410 | - | Low |

### 1.9 Donor (`/dashboard/donor*`)

| Page | Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|---|
| profile | Load | Fetch own profile | `GET /donors/me`; 404 treated as "new" via status or `/not found/i` regex (`:127`) | L/E/- | OK |
| profile | **Save** | Save profile | `PUT /donors/me {bloodType,dateOfBirth,weightKg,district,latitude,longitude}` (`:209`); client DOB age 18-60 and weight 30-300; sticky bar on mobile; success notice 3s. Server validation detail dropped (F5). `dateOfBirth` is a `LocalDate` and, per F4, may come back as an array so the date input would render empty after reload and block re-saving **(verify)** | L/E/S | Suspicious |
| profile | **Availability toggle** | Toggle availability | `PATCH /donors/me/availability {isAvailable}` (field name matches `AvailabilityDto`), optimistic with rollback (`:172-184`). No disabled/spinner while in flight, no success message; for a new donor it is only applied after the first Save | -/E/- | Suspicious |
| profile | **Low-Data Mode toggle** | Reduce data/animation everywhere | Writes `localStorage.lowDataMode` (`:233-237`); only effect in the whole codebase is hiding the three stat cards on this page (`:291`). grep for `lowData` finds no other reader | - | Broken (no-op) |
| profile | Use my location | Device coordinates | `navigator.geolocation` (`:153`); denial note | -/E/- | OK |
| profile | District / blood type / DOB / weight | Edit | Controlled inputs, DOB `min/max`, hints | - | OK |
| profile | **View My Matches** | Go to matches | `ButtonLink` -> `/dashboard/donor/matches`, only rendered when a profile exists (`:257`) | - | OK |
| profile | My bookings: Cancel | Cancel booking | `DELETE /slots/{slotId}/book`; QR via `qrcode.react` | L/E/S | OK |
| matches | Refresh | Reload | `RefreshButton` -> `GET /donors/me/matches`; auto-refresh 30s | L/E/- | OK |
| matches | Accept / Decline | Respond | `POST /matches/{id}/respond?response=ACCEPTED|DECLINED` (matches `MatchStatus`). Per-row disabled only | L/E/S | OK |
| matches | Status text | Localised | `t('dash.matchStatus.NO_RESPONSE')` has no key -> raw key shown (backend enum `NO_RESPONSE`) | - | Broken (that state) |
| matches | "Update donor profile" | Navigate | `ButtonLink` | - | OK |
| heatmap (D, A) | Refresh | Reload | `GET /analytics/district-summary` | L/E/- | OK |
| heatmap | Markers/popup | Inspect district | See section 6 | - | Suspicious |
| nav | "Camps" | Go to camps | Leaves the dashboard shell for the public `/camps` | - | Suspicious |
| nav | Notifications | Live feed | See 1.11 | - | Suspicious |

### 1.10 Requester / hospital shared request screens

| Page | Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|---|
| `/dashboard` (R) | Three action cards | Navigate | `Link` | - | OK |
| `/dashboard/requests/new` (R, H) | Mode chips AI / form | Switch | `FilterChips` | - | OK |
| new | **Extract** (AI draft) | Prefill form | `POST /requests/ai-draft {text,locale}`; failure -> message + form mode | L/E/S | OK |
| new | Sample prompt chips | Fill textarea | Only the active language is shown; button wraps text | - | OK |
| new | GPS detect | Fill lat/lng | geolocation | -/E/- | OK |
| new | **Submit** | Create request | `POST /requests {patientBloodType,unitsNeeded,urgency,hospitalName,district,latitude,longitude}` (matches `CreateRequestDto`); CRITICAL disabled unless VERIFIED; success notice then redirect after 2s. Raw numeric lat/lng inputs shown to end users. No `neededBy`, contact or hospital selection (proposal 7) | L/E/S | Suspicious |
| new | Back | `/dashboard` | For H this redirects on to `/dashboard/hospital` | - | OK |
| `/dashboard/requests` | Refresh, status chips, search | Filter | `GET /requests` (A) or `/requests/me` | L/E/- | OK |
| requests | "New request" | Create | `ButtonLink` shown to A as well, but `SECTION_ROLES` bounces A back to `/dashboard/admin` (`layout.tsx:19`) | - | Broken (admin) |
| `/dashboard/requests/[id]` | Back / detail | View | `GET /requests/{id}` (no ownership check in `RequestService.java:73`) | L/E/- | OK (see F20) |
| detail | **Invite circle** | Create invite | `POST /requests/{id}/circle` | L/E/- | OK |
| detail | Copy link / Preview card / WhatsApp | Share | clipboard (no error UI), `/share/{token}` new tab, `api.whatsapp.com` | -/-/S | OK |
| detail | Refresh circle | Reload responses | `GET /requests/{id}/circle/responses` | - | OK |
| detail | Live updates | SSE | `fetchEventSource /notifications/stream` (see 1.11) | - | Suspicious |
| detail | **Cancel / mark fulfilled / mark donated** | Close the loop | **No control exists.** Backend has `PUT /requests/{id}/cancel` and `POST /matches/{id}/donated`; the frontend never calls them | - | Broken (missing) |

### 1.11 Notifications (D, R, H, B, A)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Live feed | History + live | SSE only (`notifications/page.tsx:67`); nothing is fetched on load, so the page is empty after every reload. Backend keeps one emitter per user (`SseNotificationService.java:24`), so two tabs/pages kick each other and reconnect-loop **(verify)**. No unread badge in the nav | -/-/- (status pill) | Suspicious |

### 1.12 Hospital `/dashboard/hospital` (H)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Refresh | Reload | `GET /requests/me` | L/E/- | OK |
| Broadcast request | Create | `ButtonLink` -> `/dashboard/requests/new` | - | OK |
| KPIs | Real data | Derived from own requests | L | OK |
| Request rows | Open detail | `RequestRow` link | - | OK |
| Camp manager: Refresh / Create camp | Work | `GET /camps/my-camps`; modal -> `POST /camps` | L/E/- | OK |
| Create-camp modal | Accessible dialog | `aria-modal`, Esc closes; no focus move/trap/return; English fallbacks (`|| 'Campaign Name'`); local copy of `DISTRICTS`; lat/lng required, `0` fallback | L/E/- | Suspicious |
| Share poster | Download poster | `CampPostGenerator`: `html-to-image` JPG. English-only, preview never scaled (`--preview-zoom` is defined nowhere), dead `<style jsx>`, no Esc/focus handling | L/E/- | Suspicious |
| Manage slots / Bulk generate / Check-in | Work | `/hospitals/{id}/slots`, `/camps/{id}/slots`, `.../bulk`, `POST /slots/{s}/check-in/{b}` | L/E/S | OK |
| Scan QR modal | Scan | `html5-qrcode` in a dialog; no Esc/focus handling | -/E/S | Suspicious |
| Edit/cancel camp | Manage lifecycle | None. Backend `PUT /camps/{id}`, `PATCH /camps/{id}/status` unused | - | Missing |

### 1.13 Blood bank `/dashboard/blood-bank` (B)

| Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|
| Whole page | Inventory, thresholds, collections, camps (proposal 7) | Only `CampManagerDashboard`. No inventory UI; backend only has public `GET /inventory/stock-signals` | - | Missing |
| Nav | Camps, notifications | `/camps` leaves the shell | - | Suspicious |

### 1.14 Admin (`/dashboard/admin*`) (A)

| Page | Element | Expected | Actual | L/E/S | Status |
|---|---|---|---|---|---|
| overview | **Refresh** | Reload | `Promise.all(GET /admin/analytics, GET /admin/reports)` (`:49`); one failure hides both | L/E/- | OK |
| overview | KPI tiles | Real totals | Backend returns hard-coded demo numbers when the DB has no requests (F6) | L | Broken |
| overview | Hub: **AI Triage Queue** | Open triage | `Link /dashboard/admin/triage` | - | OK |
| overview | Hub: **Verification Portal** | Open queue | `Link /dashboard/admin/verifications` | - | OK |
| overview | Hub: "Interactive Heatmap" and the **"View Geospatial Map"** link (the "Open Interactive Map" suspect) | Open map | Both go to `/dashboard/admin/analytics` (`:89,165`) | - | OK (duplicate) |
| overview | Hub: Camps map | Open | `/dashboard/admin/camps` | - | OK |
| overview | District bars | Proportional | Relative to max, colour + number, no axis | L | Suspicious |
| overview | **Verify User** (raw ID) | Approve a pending account | `<input type=number>` + `PUT /admin/verifications/{id}/approve` (`:71-82`). No confirm, no lookup of who the ID is, generic error. Backend approves any existing user id, any role, any status (`AdminVerificationController.java:37-45`) | L/E/S (3s) | Broken (UX/safety) |
| overview | Flagged reports | Review/act | Read-only list; no action; `createdAt` is an `Instant` (see F4) | -/-/- | Suspicious |
| triage | Refresh | Reload | `GET /admin/triage/queue` | L/E/- | OK |
| triage | Risk tiles (filters) | Filter | `aria-pressed` buttons | - | OK |
| triage | View | Open request | `ButtonLink /dashboard/requests/{id}` | - | OK |
| triage | Dismiss flag | Mark reviewed | `PUT /admin/triage/{id}/review {notes}` with hard-coded English note (`:66`); item stays in list (score set to 0, queue is "all requests", see F15) | L/E/- | Suspicious |
| verifications | Refresh | Reload | `GET /admin/verifications/pending` (bare array; handled) | L/E/- | OK |
| verifications | Approve / Reject | Decide | `PUT .../approve|reject`, row removed, 4s success. No reason, no documents, raw `role.replace(/_/g,' ')` | L/E/S | Suspicious |
| analytics | Refresh telemetry | Reload | `GET /admin/analytics/district-summary` | L/E/- | OK |
| analytics | Filter chips | Filter map | Labels are raw enum text `CRITICAL`... (`:77`) | - | Suspicious |
| analytics | Map marker click | Popup + detail panel | Inline `onSelectDistrict` re-creates markers every render, closing the popup (F12) | - | Broken |
| analytics | Row select, search | Select/filter | Row `onClick` and inner `<button>` both set state; table search `aria-label` | - | OK |
| camps map | Back | `/dashboard/admin` | `PageHeader backHref` | - | OK |
| camps map | Legend | Match markers | Page legend says blue = active camps, ember = critical shortages; markers are ember for SCHEDULED (`AdminMap.tsx:75`) and grey otherwise, with a second legend inside the map (F31) | - | Broken |
| all admin subpages | **Back** | Up one level | `PageHeader backHref` link (not history back) | - | OK |

---

## 2. Colour audit

Method: `colour-audit.js` over all 127 `.ts/.tsx/.css` files under `src` (excluding `theme.css`, which is the palette definition). Regex counting; the red-split is a line-context heuristic, so treat it as approximate.

**Key structural facts**
- There is no theme mechanism: no `data-theme`, no `prefers-color-scheme`, no toggle, no `next-themes` (grep).
- `theme.css` (247 lines, "GENERATED") overwrites every Tailwind palette family (gray, red, rose, orange, amber, emerald, blue, ...) with dark-surface-tuned oklch ramps, and sets `--color-white: #f4eee4` (`theme.css:246`). So `text-white` is actually bone, `text-red-*` is the ember ramp, `text-gray-*` assumes a dark background. A light theme cannot be added by toggling a class; the remap has to be removed and replaced with semantic tokens.
- Tokens that exist (`globals.css:258-271`): `--background --surface --surface-raised --foreground --muted --line --line-strong --ember --ember-deep --saffron --sage`. All dark-only. No `primary`, `success`, `warning`, `error`, `info`.

**Totals:** hex literals 30, `rgb()/hsl()/oklch()` functions 24, arbitrary-value colour classes 12, red/rose utilities 55, white utilities 20, black utilities 6, `ember` references 201, other palette families (emerald/amber/blue...) 91.

### 2.1 Hard-coded colours per file (excl. theme.css)

| file | hex | rgb/hsl/oklch() | arbitrary-colour classes | total |
|---|--:|--:|--:|--:|
| app/globals.css | 9 | 15 | 0 | 24 |
| components/og/ogParts.tsx | 10 | 3 | 0 | 13 |
| app/global-error.tsx | 5 | 0 | 0 | 5 |
| components/maps/mapStyles.ts | 0 | 5 | 0 | 5 |
| components/chat/DonorFaqChatbot.tsx | 0 | 0 | 3 | 3 |
| components/brand/SriLankaMap.tsx | 2 | 0 | 0 | 2 |
| components/dash/ButtonLink.tsx | 0 | 0 | 2 | 2 |
| components/landing/landing.module.css | 2 | 0 | 0 | 2 |
| components/landing/shared.tsx | 0 | 0 | 2 | 2 |
| components/ui/Button.tsx | 0 | 0 | 2 | 2 |
| app/layout.tsx (`themeColor: '#0b0908'`) | 1 | 0 | 0 | 1 |
| components/camps/CampPostGenerator.tsx (`'#ffffff'`) | 1 | 0 | 0 | 1 |
| components/dash/DashShell.tsx | 0 | 0 | 1 | 1 |
| components/og/genericOgImage.tsx | 0 | 1 | 0 | 1 |
| components/pub/linkButton.ts | 0 | 0 | 1 | 1 |
| components/ui/LanguageSwitcher.tsx | 0 | 0 | 1 | 1 |

`globals.css` hard-codes: `--background/--surface/--surface-raised/--foreground/--muted` as hex (`:259-263`), rgba line colours, `.leaflet-container { background:#080605 }` (`:470`), popup `rgba(19,16,16,.96)`, scrollbar thumb, film-grain overlay `body::before` (z-100, `:312`), and `.gradient-text` hex fallback (`:361`). `og/ogParts.tsx:7-14` and `global-error.tsx:8-12` duplicate the palette as literals (these two render outside the CSS pipeline, so they need a generated constants file rather than CSS vars).

### 2.2 `red-*` / `rose-*` utilities per file (heuristic split)

Total 55: ~36 semantic error/destructive, ~19 decorative (18 are the poster). **In app UI, red is already mostly semantic**. The primary accent is the `ember` token, not `red-*` (see 2.4).

| file | total | semantic error/destructive | primary-action / decorative |
|---|--:|--:|--:|
| components/camps/CampPostGenerator.tsx | 18 | 0 | 18 (poster artwork) |
| app/dashboard/admin/verifications/page.tsx | 7 | 7 | 0 (Reject button, icons) |
| components/dash/Notice.tsx | 4 | 4 | 0 |
| components/ui/Button.tsx | 4 | 4 | 0 (`danger` variant) |
| components/ui/Input.tsx | 4 | 4 | 0 |
| components/ui/StatusBadge.tsx | 3 | 3 | 0 (CRITICAL, REJECTED, NO_SHOW) |
| components/pub/PasswordField.tsx | 2 | 2 | 0 |
| components/pub/Textarea.tsx | 2 | 2 | 0 |
| components/ui/Select.tsx | 2 | 2 | 0 |
| admin/analytics, admin/triage, donor/heatmap, donor/matches, donor/page, hospital, requests, requests/[id] (1 each, `text-red-400` on error icons) | 8 | 8 | 0 |
| app/globals.css | 1 | 0 | 1 |

Migration plan: keep `error` token for these; replace the ~19 poster uses with brand/crimson tokens (or a poster-specific palette).

### 2.3 `text-white` / `bg-white` / `border-white` (theme-blocking)

| file | white utilities | black utilities |
|---|--:|--:|
| components/camps/CampPostGenerator.tsx | 12 | 1 |
| components/chat/DonorFaqChatbot.tsx (`bg-ember text-white` x2: user bubble `:175`, send button `:239`) | 2 | 0 |
| components/dash/ButtonLink.tsx (`:10`) | 1 | 0 |
| components/landing/LandingNav.tsx (skip link `:47`) | 1 | 0 |
| components/landing/shared.tsx (`PillLink` `:50`) | 1 | 0 |
| components/pub/linkButton.ts (`:8`) | 1 | 0 |
| components/slots/MyBookings.tsx (`bg-white` QR plate `:81`, correct to keep) | 1 | 0 |
| components/ui/Button.tsx (`:24`) | 1 | 0 |
| DashShell (backdrop `bg-black/60`), CreateCampModal / SlotManager / CampPostGenerator (`bg-black/70-85` overlays), donor page & new-request page (`shadow-black/50`) | 0 | 5 |

Also `color-mix(... , white)` hover in 4 button primitives. Note the four primary-button implementations (`ui/Button.tsx`, `dash/ButtonLink.tsx`, `landing/shared.tsx PillLink`, `pub/linkButton.ts`) are copies of each other.

### 2.4 `ember` references (what must become `primary` vs `brand`) - 201 refs in 70 files

Top files: DonorFaqChatbot 17, admin/triage 8, camps 7, globals.css 7, ShareCardClient 7, StockTicker 7, admin/page 6, landing/shared 6, MyBookings 6, requests/new 5, DashShell 5, pub/UrgencyBadge 5, analytics 4, dashboard/page 4, SriLankaMap 4, CampPostGenerator 4, landing.module.css 4, ui/Button 4. 52 further files have 1-3 each. Uses fall into: primary buttons, focus ring (`:focus-visible` outline, `ring-ember/15`), nav active tick, links (`text-ember`), switch-on, eyebrow rules, chatbot, stat tones, urgent markers. Only blood-type tiles/logo/urgent CTA/critical markers should remain brand-crimson.

### 2.5 `text-gray-N` usage (remapped warm stone)
200: 4, 300: 48, 400: 82, 500: 86, **600: 14**, 700: 1, 900: 6 (the 900s are on the poster).

### 2.6 Other palette families by file (semantic success/warn/info via remap)
admin/triage 9, requests/[id] 9, ui/StatusBadge 9, dash/Notice 8, DonorFaqChatbot 7, admin/page 6, admin/verifications 6, donor/matches 6, CampPostGenerator 4, circle client 3, RequestDistanceMap 3, MyBookings 3, others 1-2. Replace with `success/warning/info` tokens.

---

## 3. String audit

### 3.1 i18n infrastructure (`lib/i18n.tsx`, `messages/*.json`)
- Home-made provider, not next-intl: `t(key, params)` does `{param}` substitution only. **0 ICU plural/select messages** in `en.json`.
- Key parity (script): en 804 keys; si 815; ta 821. Missing in si: 0; missing in ta: 0; extras not in en: si 11, ta 17. Placeholder mismatches: 0. Untranslated (identical to English): `contact.emailPlaceholder`, `bloodRequests.whatsapp` (acceptable).
- One real missing key: `dash.matchStatus.NO_RESPONSE` (dynamic key; backend enum `MatchStatus.NO_RESPONSE`). The script flagged `request.status` only because the code concatenates it (`request.status` + `Open`): all six suffix keys exist.
- Locale is read from `localStorage` inside `useEffect` (`i18n.tsx:47-58`), `<html lang="en">` is fixed on the server (`layout.tsx:54`): Sinhala/Tamil users see an English flash on every load, crawlers and SSR (metadata, OG images, `global-error`) are always English, no cookie.
- Fonts: Noto Sans/Serif Sinhala/Tamil loaded and `:lang(si)/(ta)` tweak line-height/tracking for `.font-display`, eyebrows. Body text line-height is not adjusted.

### 3.2 Hard-coded user-facing strings still in components (script + reading)

| file:line | example |
|---|---|
| `components/camps/CampPostGenerator.tsx` (12+ hits) | "Suwa Sarana", "National Blood Drive", "Presents", "ONE DONATION MANY Heartbeats", "Your generosity can make...", "Date/Time/Location", "Required Blood Types", "BE A LIFESAVER.", "Book your slot now", `'Failed to generate poster...'` (`:55`), `toLocaleDateString('en-US')` (`:124`) |
| `components/camps/CreateCampModal.tsx:105-203` | `|| 'Campaign Name'`, `|| 'District'`, `|| 'Venue / Location Details'`, `'Latitude'`, `'Date'`, `'Start Time'`, `'Create Camp'`, `'Cancel'` (fallbacks mask missing keys) |
| `components/chat/DonorFaqChatbot.tsx:138` | "Suwa Sarana AI" |
| `components/ui/LanguageSwitcher.tsx:52,65` | `aria-label="Switch language"`, `"Language"` |
| `components/pub/PubFooter.tsx:25`, `landing/LandingFooter.tsx:28`, `LandingNav.tsx:53` | `aria-label="Footer"`, `"Primary"` |
| `app/loading.tsx:5`, `components/ui/Skeleton.tsx:11` | `aria-label="Loading"` |
| `app/global-error.tsx:10-12` | "Something went wrong", "Please try again...", "Try again" |
| `app/dashboard/admin/triage/page.tsx:66` | note sent to API: "Dismissed by administrator from triage console." |
| `app/dashboard/requests/new/page.tsx:90` | English sample prompt (SI/TA ones exist; acceptable as examples) |
| `app/share/[token]/ShareCardClient.tsx:96-97` | share text "EMERGENCY: ... Your donation can save a life today..." (with emoji) |
| `app/share/[token]/page.tsx:28-34`, `circle/[token]/page.tsx:28` | metadata titles/descriptions, "URGENT" regardless of urgency |
| `components/og/*` | "Sarana", "Blood donation platform", "needed urgently", `unit/units` |
| `app/(auth)/*/layout.tsx`, `camps/contact/privacy/terms` | static English `metadata.title` ("Sign in", "Create account", "Reset password", "Donation camps", "Contact us", "Privacy Policy - Suwa Sarana", ...) |
| `analytics/page.tsx:77,150,265`, `SriLankaHeatmap.tsx:96`, `AdminMap.tsx:97` | raw enum text `CRITICAL / WARNING / BALANCED / SURPLUS`, camp `status` |
| `admin/verifications/page.tsx:188` | `u.role.replace(/_/g,' ')` (English enum words) |
| `app/dashboard/requests/new/page.tsx:347` | `'PENDING'` fallback shown inside a localised sentence |
| `components/ui/StatusBadge.tsx:25` | falls back to title-cased enum when no label is passed (camp status in `camps/page.tsx:70`, `CampManagerDashboard.tsx:104`, slot/booking badges) |
| Backend messages | `apiMessage()` shows `response.data.message` verbatim in every locale (English: "Validation failed", "Email is already in use!", "Invalid credentials") |
| `contact/page.tsx:44-51`, `PrivacyClient.tsx:92` | contact emails/phone literals |
| `en.json`: `ui.urgency.CRITICAL = "CRITICAL"` | untranslatable all-caps used in `StockTicker.tsx` |

### 3.3 Missing / weak aria-labels
- Language switcher (above). Chat panel `role="dialog"` has a label but no `aria-modal`. Sidebar `aside` and inner `nav` share one label.
- Icon-only controls without a name: none critical found; X buttons have `aria-label`. Map markers (Leaflet `divIcon`) have no `title`/`alt`, so keyboard/screen-reader users hear "Marker".
- Admin "Verify User" input has `aria-label` (good) but no `<label>`; the analytics/requests search inputs rely on `aria-label` only.
- Triage tiles: `role="group"` + `aria-pressed` ok, count not announced as part of name.

### 3.4 Pluralisation sites (none use ICU)
| site | issue |
|---|---|
| `admin/page.tsx:122,200` `common.requests` ("requests") next to any count -> "1 requests" | the proposal's "1 requests" bug is still present |
| `admin/page.tsx:264` `${flaggedReports} (${n})` | no plural |
| `dash.unitsCount = "{n} units"` (`matches/page.tsx:148`, `triage/page.tsx:181`) | "1 units" |
| `circle.unitsCount = "{count} Units"` (`CircleResponseClient.tsx:218`, `ShareCardClient.tsx:151,204`) | "1 Units" |
| `pub.campsCount = "{count} camps"` (`camps/page.tsx:168`) | "1 camps" |
| `share.timeLeftValue = "{hours}h {minutes}m left"` | no days; hours `% 24` |
| `SlotPicker.tsx:145` switches between `spotsLeft`/`spotLeft` keys manually | works for EN only; Sinhala/Tamil plural categories differ |
| `og/emergencyOgImage.tsx:22` `unit`/`units` ternary | English only |

### 3.5 Date / number formatting not tied to the app locale
All `toLocale*String([], ...)` / `toLocaleString()` calls use the browser locale, not `useI18n().locale` (Intl is used underneath, but the locale is wrong): `donor/matches/page.tsx:163,168`, `admin/page.tsx:278`, `requests/[id]/page.tsx:287,406`, `RequestRow.tsx:44`, `notifications/page.tsx:60`, `MyBookings.tsx:13,66,71`, `SlotManager.tsx:23,269`, `SlotPicker.tsx:19,23`, `CampManagerDashboard.tsx:112`. `AnimatedNumber.tsx:19,27` hard-codes `'en-US'`. `camps/page.tsx:33-41` is the only correct one. Numbers are never formatted per locale (Tamil/Sinhala digits, grouping).

### 3.6 Titles / metadata gaps
- Only `layout.tsx` (default, 47 chars: "Suwa Sarana - Community Blood Donation Platform"), 5 route layouts, privacy/terms and share/circle have titles. **No dashboard page has a title** (all are client components with no `metadata`), so every dashboard tab shows the long default, which truncates in the tab (proposal 4).
- `title.template '%s | Suwa Sarana'` + titles that already end with "| Suwa Sarana" or "- Suwa Sarana" (share, circle, privacy, terms) -> doubled brand.
- All titles/descriptions English; no `alternates`/hreflang; `themeColor` hard-coded dark.
- `not-found.tsx`, `error.tsx` have no metadata.

---

## 4. Accessibility audit

| Area | Finding | Evidence |
|---|---|---|
| Skip link | Only on the landing page. Auth, public, share, circle and the whole dashboard (`#dash-main` exists) have none | grep "skip": `LandingNav.tsx:46` |
| Landmarks | `main` present on all page types; `header/nav/footer` mostly right. Chatbot sits in `body` outside any landmark. Duplicate "Main navigation" label on `aside` + `nav` | `DashShell.tsx:102,152` |
| Labels | `Input/Select/PasswordField/Textarea` all wire `label htmlFor` + `aria-invalid` + `aria-describedby` (good). Error text is not `role="alert"`/`aria-live`; the page-level `Alert/Notice` is | `ui/Input.tsx:30-46` |
| Autocomplete | Login email uses `email` (should be `username`); register fields good; NIC has none | `login/page.tsx:102` |
| Focus: modals/drawers | Dashboard drawer is good (Esc, focus in/out, `inert`). `CreateCampModal` (Esc only), `CampPostGenerator`, QR scanner modal, chatbot dialog, landing mobile menu: no focus move, no trap, no return, mostly no Esc, no scroll lock | `CreateCampModal.tsx:45-52`, `CampPostGenerator.tsx:67`, `SlotManager.tsx:186`, `DonorFaqChatbot.tsx:123` |
| Language switcher | `role="menu"` + `menuitemradio` but focus is never moved into the menu and arrow keys do nothing | `LanguageSwitcher.tsx:63-90` |
| Focus visibility | Global `:focus-visible` ember outline (`globals.css:328`) and per-component rings, but inputs use `focus:outline-none` with a 1px border colour change + `ring-4 ring-ember/15` (Input, Select, Textarea, PasswordField); chat input only changes border colour (`DonorFaqChatbot.tsx:233`) | |
| Colour-only status | Map markers: ring colour + a number whose meaning changes (requests vs donors) (`SriLankaHeatmap.tsx:83`, `mapStyles.ts:35`); legend lists 3 of 4 levels. Admin overview bars: colour thresholds, number shown. Switch off/on: thumb position (ok). `StatusBadge` always has text (ok). Triage score: text + bar (ok). AdminMap: scheduled vs other = ember vs grey dot | |
| Touch targets (proposal: 44px) | Language switcher `h-9`, chat close `h-8`, `Switch` `h-6 w-11`, `FilterChips` `py-1.5`, `Button sm` `py-1.5` (~30px), table action buttons. All above WCAG 2.2 AA 24px minimum; below the owner's 44px rule. 44px ok: password toggle, menu button | |
| Headings | `PageHeader` h1 -> `SectionHeader` h2 -> `SectionLabel` h3 is consistent. Chat title is an h3 with no h2 above it in page order. Admin hub cards use h2 | |
| Reduced motion | CSS animations disabled under `prefers-reduced-motion` (`globals.css:447-458`), motion components use `useReducedMotion`, marquee has `motion-reduce` variants. Spinner and `scroll-behavior` handled. Film-grain overlay and pulsing markers are static/off | OK |
| Live regions | Chat messages `aria-live="polite"` (whole log, will re-read); notifications `ol aria-live`; request updates `aria-live`; connection pill `role="status"` | |
| Reflow / font size | 30 uses of `text-[9-11px]` and 0.68-0.72rem mono eyebrows/table headers (`styles.ts:13`) | |
| Language | `<html lang>` static `en`, updated post-hydration only | `layout.tsx:54` |

### 4.1 Computed contrast (script `contrast.js`, colours from `globals.css` / `theme.css`, oklch -> sRGB)

Surfaces: background `#0b0908`, surface `#131010`, raised `#1a1615`, foreground `#f4eee4`. Derived: gray-300 `#cbc6c3`, 400 `#b3ada7`, 500 `#98918b`, 600 `#7c7671`, ember `#f65139` (sRGB, in gamut).

| pair | ratio | verdict |
|---|--:|---|
| text-gray-300 on background / surface | 11.75 / 11.20 | pass |
| text-gray-400 on background / surface | 8.91 / 8.49 | pass |
| text-gray-500 on background / surface / raised | 6.37 / 6.07 / 5.76 | pass |
| **text-gray-600 on background / surface / raised** | **4.44 / 4.23 / 4.01** | **fail 4.5:1** (14 uses: footers, resend-disabled, stats index `StatsBand.tsx:20`) |
| **placeholder-gray-600 on input fill** | **4.21** | fail |
| muted `#a39a8e` on background | 7.16 | pass |
| foreground on background | 17.22 | pass |
| ember text on background / surface | 5.82 / 5.55 | pass |
| saffron / sage on background | 10.48 / 8.36 | pass |
| **white(#f4eee4) on ember (primary Button, ButtonLink, PillLink, linkButton, chat bubble/send)** | **2.96** (hover **2.66**) | **fail 4.5:1** |
| text-red-400 on background | 7.94 | pass |
| text-red-300 on Notice error fill | 10.05 | pass |
| text-emerald-400 on background | 9.26 | pass |
| text-emerald-300 on approve-button fill | 10.86 | pass |
| text-amber-400 on background | 8.82 | pass |
| **border-line (9% bone) vs background** | **1.19** | fail 3:1 (decorative dividers - acceptable) |
| **border-line-strong (16% bone) = input/select/textarea border vs background** | **1.46** | **fail 3:1 (WCAG 1.4.11)**; vs input fill 1.39 |
| **Switch off-track border vs background** | **1.46** | fail 3:1 (thumb 14:1 helps) |
| ember focus ring / switch-on vs background | 5.82 | pass |

Consequence for the redesign: the primary action colour and every form-control boundary need new values; the existing numbers cannot simply be re-used for the blue/teal primary or the light theme.

---

## 5. Integration audit (frontend calls vs backend)

Backend envelope: `ApiResponse{success,message,data,timestamp}`; `camps`, `inventory`, `admin/verifications/pending` return bare JSON. The client tolerates both (`unwrap`, `res.data.data ?? res.data`).

| # | Frontend call (file:line) | Backend | Verdict |
|---|---|---|---|
| 1 | `POST /auth/login {username,email,password}` (`login:57`) | `AuthController.login`, `LoginDto` (`@JsonAlias email`) | OK (redundant `email`) |
| 2 | `POST /auth/send-otp`, `/verify-otp`, `/register`, `/reset-password` | `OtpRequestDto`, `OtpVerifyDto`, `RegisterDto`, `ResetPasswordDto{newPassword}` | OK |
| 3 | `POST /auth/google/verify {idToken}`, `/google/register` | `GoogleVerifyRequest`, `GoogleRegisterRequest` (`requiresRegistration`, `authResponse`) | OK |
| 4 | `POST /auth/refresh`, `/auth/logout` via bare axios | cookie `refresh_token`, path `/api/auth`, `SameSite` configurable | OK for same-site; cross-site prod needs `COOKIE_SAME_SITE=None` |
| 5 | `GET /donors/me` | `DonorProfileDto` (`available` via `isAvailable()`; `dateOfBirth` LocalDate) | Field names OK; date shape see F4 |
| 6 | `PUT /donors/me` | `UpdateDonorProfileDto` (quiet hours not sent) | OK |
| 7 | `PATCH /donors/me/availability {isAvailable}` | `AvailabilityDto.isAvailable` | OK |
| 8 | `GET /donors/me/matches` | `MatchDto.request` | OK; `status` may be `NO_RESPONSE` (no i18n key) |
| 9 | `POST /matches/{id}/respond?response=` | `@RequestParam MatchStatus` | OK |
| 10 | `POST /requests`, `POST /requests/ai-draft {text,locale}` | `CreateRequestDto`, `AiDraftRequestDto` | OK; `extractedSuccessfully` ignored |
| 11 | `GET /requests` (A) / `/requests/me` (R,H) | class `@PreAuthorize(REQUESTER,HOSPITAL,ADMIN)` | OK; blood-bank/donor correctly absent |
| 12 | `GET /requests/{id}` | `RequestService.getRequestById` (`:73-77`): **no ownership check**, any authenticated role | Integration gap / IDOR (F20) |
| 13 | `POST /requests/{id}/circle`, `GET .../circle/responses` | `CircleController` | OK |
| 14 | `GET /circle/{token}`, `POST /circle/{token}/respond` | `CircleInfoDto`, `SubmitCircleResponseDto` | `isExpired` vs JSON `expired` (benign); phone not normalised (F26) |
| 15 | `GET /admin/analytics` | `AdminService.getDistrictAnalytics` | Shape OK, **content fake/incorrect (F6)** |
| 16 | `GET /admin/reports` | `ReportDto` (`createdAt` is `Instant`) | Date shape (F4) |
| 17 | `GET /admin/analytics/district-summary`, `GET /analytics/district-summary` | `DistrictSummaryDto` (9 fields) | Field names match; values wrong (section 6) |
| 18 | `GET /admin/triage/queue`, `PUT /admin/triage/{id}/review {notes}` | `RequestResponseDto` incl. fraud fields; `TriageReviewDto` max 200 | OK; queue is every request (F15) |
| 19 | `GET /admin/verifications/pending`, `PUT .../approve|reject` | `AdminVerificationController` (bare list, empty body) | OK; no role/status guard on approve/reject |
| 20 | `GET /camps`, `/camps/recommended`, `/camps/my-camps`, `POST /camps` | `DonationCampDto`, `CreateCampDto` | OK (bare arrays); `/camps` returns only SCHEDULED |
| 21 | `GET/POST /camps/{id}/slots[/bulk]`, `/hospitals/{id}/slots[/bulk]` | `SlotBookingController`, `BulkCreateSlotDto` | OK (`hostId = user.id` for hospital) |
| 22 | `POST/DELETE /slots/{id}/book`, `POST /slots/{s}/check-in/{b}`, `GET /donors/me/bookings` | matches; QR payload `{s,b}` | OK; `bookedAt/checkedInAt` are `Instant` |
| 23 | `POST /faq/ask {question,locale}` | `FaqQuestionDto` (max 500) -> `FaqAnswerDto` | OK; client ignores `groundedInRules`; no length cap |
| 24 | `POST /contact` | `ContactRequestDto` | OK |
| 25 | `GET /inventory/stock-signals` | `BloodInventoryDto` | OK; `StockSignal` type omits `updatedAt` (harmless) |
| 26 | `fetchEventSource /notifications/stream` (`notifications/page.tsx:67`, `requests/[id]:137`) | `NotificationController` -> `SseEmitter`; event names from `message.getType()`; one emitter per user | OK protocol; multi-tab conflict |
| 27 | Server-side `fetch(API_BASE_URL/circle/{token})` (`share|circle page.tsx`, `opengraph-image.tsx:22`) | same | Uses the browser-facing base URL |

**Backend endpoints with no frontend caller:** `PUT /requests/{id}/cancel`, `POST /matches/{id}/donated`, `PUT /camps/{id}`, `PATCH /camps/{id}/status`, `POST /camps/{id}/register` (410, deprecated), `POST /admin/requesters/{id}/verify` (duplicate of approve), `/api/ivr/**`.

**Cross-cutting mismatches**
- **Validation errors:** backend returns `400 {message:"Validation failed", data:{field: msg}}` (`GlobalExceptionHandler.handleMethodArgumentNotValid`). The client only reads `message` (`lib/auth.ts:46-49`), so every form shows a generic English "Validation failed". This is the screenshot's generic "Validation Error" banner.
- **Jackson dates (F4):** `JacksonConfig` builds `new ObjectMapper()` + `JavaTimeModule` and never disables `WRITE_DATES_AS_TIMESTAMPS`; no `@JsonFormat` or `spring.jackson.*` exists (grep), and Boot's property-driven mapper backs off when a custom `ObjectMapper` bean exists. Expected wire format: `LocalDateTime` -> `[2026,10,27,14,30,0,...]`, `LocalDate` -> `[y,m,d]`, `LocalTime` -> `[h,m]`, `Instant` -> epoch seconds with fraction. The frontend types assume ISO strings (`types/index.ts:46,59`) and does `new Date(x)` on: `expiresAt`, `createdAt`, `respondedAt`, `scheduledDate`, `startTime/endTime`, `dateOfBirth`, `bookedAt`, report `createdAt`. **(verify with one `curl /api/requests/me`)**.

---

## 6. Maps

| Item | Finding |
|---|---|
| Tile provider | OpenStreetMap standard tiles `https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png` in `SriLankaHeatmap.tsx:53`, `AdminMap.tsx:54`, `RequestDistanceMap.tsx:54`. No key, no Carto. |
| Licence | `attributionControl: false` on all three maps (`SriLankaHeatmap.tsx:49`, `AdminMap.tsx:50`, `RequestDistanceMap.tsx:200`) and no custom credit: violates the ODbL/OSM attribution requirement. The OSM tile servers also disallow production/heavy use without a fallback and the `{s}` subdomain pattern is deprecated. Tiles are recoloured with an inverted CSS filter (`.dark-map-tiles`, `globals.css:502`) which has no light-theme counterpart. |
| Config | Tile URL and CSP are hard-coded (`next.config.ts` `img-src` lists `*.openstreetmap.org`); a keyed provider needs an env var for both. |
| Bounds / zoom | Heatmap: centre `[7.8731, 80.7718]`, `zoom: 7.5`, `minZoom 7`, `maxZoom 12`, no `maxBounds`. Leaflet 1.9.4 rounds the requested zoom with `zoomSnap` 1 (`_limitZoom`, `leaflet-src.js:4697-4704`), so the initial zoom is **8**, not 7.5. At zoom 8 and latitude 8 degrees one pixel is about 606 m, the island (about 435 km north-south) is about 720 px tall but the container is 420/520 px, so the island is cropped while the western side shows south India. Users can zoom out to 7 and pan anywhere. AdminMap: same centre, `minZoom 6` (even further out). No `fitBounds`, no `maxBounds`. |
| Marker encoding | `markerHtml` (`mapStyles.ts:35-45`): coloured ring + a mono number. The number is `activeRequests` if > 0, otherwise `donorCount` (`SriLankaHeatmap.tsx:83`), so "0" appears whenever both are zero and the same badge means two different things. No icon/shape per status; status text only in the popup. Colour is the only on-map status cue. Pulse ring only on CRITICAL/WARNING. |
| Legend | 3 entries (critical/warning/balanced) for 4 levels (SURPLUS drawn in bone, not listed). Wording: "Critical shortage (active > donors)" and "Warning (low donor ratio)" (`en.json pub.mapLegendCritical/Warning`) but the rule is `donors/active < 2` -> CRITICAL, `< 5` -> WARNING, `donors >= 10 && active == 0` -> SURPLUS (`AdminService.java:171-185`). The legend text is wrong for all of them. Popup pill/filters print raw `CRITICAL...`. |
| Popup closing | `SriLankaHeatmap` effect depends on `onSelectDistrict`; the analytics page passes a new inline arrow each render (`analytics/page.tsx:138`). Clicking a marker sets state -> re-render -> effect -> `clearLayers()` -> popup closes (F12). |
| Camps map | `/camps` returns SCHEDULED only, so the "cancelled/completed" grey state never occurs; the page header legend (blue "active camps", ember "critical shortages", `admin/camps/page.tsx:301-310`) contradicts the markers (ember pulsing for SCHEDULED) and the map's own internal legend. |
| Request map | Zoom 9/10/11 by radius, circle radius in km; fine. Marker has `title`/`alt`: none. |
| Dark-only | Popups, legend, container background (`#080605`) are hard-coded dark. |

### 6.1 Admin analytics: exactly where the wrong numbers come from

Backend, `AdminService.java`:
1. **Overview bars + "Blood Request Distribution" total + "District" count** use `getDistrictAnalytics()` (`:89-107`, `GET /admin/analytics`). If the table is empty it returns hard-coded `Colombo 12, Gampaha 8, Kandy 5, Galle 3` (`:93-98`); otherwise it counts every request ever created (any status, including CANCELLED/EXPIRED/FULFILLED), grouped by the raw district string (including `"Unknown"`), only for districts with at least one row. Frontend: total = sum of values, "District" tile = `Object.keys().length` (`admin/page.tsx:84,125`).
2. **Heatmap / telemetry table / KPI tiles** use `getDistrictSummaries()` (`:109-168`):
   - Requests and donors are grouped by `district.trim()` and looked up by exact, case-sensitive equality with 25 canonical names (`:116,123,131`). Anything free-typed (AI-draft district, legacy rows, other spelling) is silently dropped from the map.
   - `activeRequests` = OPEN + ESCALATING + MATCHED only (`:137-141`); `fulfilledRequests` = FULFILLED; `totalRequests` = all. So the overview (all statuses) can show requests in Hambantota/Batticaloa/Colombo/Ratnapura while the heatmap shows 0 active whenever those requests are EXPIRED/CANCELLED. This matches the screenshot pattern.
   - `donorCount` = number of `DonorProfile` rows in that district (`:147`), with no filter for `available`, deferral or eligibility, yet the UI labels it "Available donors" (map popup, table, heatmap tile) and "Registered Donors" (analytics tile). A district with no donor profiles in the exact spelling gives 0.
   - `fulfillmentRate = total > 0 ? fulfilled/total*100 : 100.0` (`:149-151`): a district with no requests is reported as **100%**. A district whose requests are all expired/cancelled is **0%** (EXPIRED/CANCELLED stay in the denominator).
   - `shortageLevel` (`:171-185`): BALANCED when there is no demand regardless of donor count.
3. Fulfilment can almost never become non-zero because nothing in the frontend calls `/matches/{id}/donated` (F9).

Frontend, `analytics/page.tsx` / strings:
4. **National fulfilment** = unweighted mean of the 25 district rates (`:62-64`), so the fake 100% districts dominate. 21 empty districts x 100 + 4 districts x 0 = 2100 / 25 = **84.0%**, exactly the "National fulfilment 84%" in the proposal screenshot (4 districts with data and none fulfilled: Colombo, Hambantota, Batticaloa, Ratnapura). Division by zero is guarded on the client (`districts.length > 0`) but the semantic error is on the server.
5. **"Registered Donors 0" beside "25 districts active"**: the hint is the literal `adminDashboard.districtsActive = "25 districts active"` (`en.json:532`), shown regardless of data.
6. Marker bubble "0": see Marker encoding. Fulfilment bar in the district panel and table draws `fulfillmentRate` (100% bar for empty districts).
7. Admin overview bars: `pct = count/max`, `scaleX(max(pct,3)/100)` - proportional to the largest district only; equal counts give all-full bars; no axis or scale.

Fix summary: return `null`/"no data" for empty denominators; weight the national rate by total requests; exclude EXPIRED/CANCELLED from the denominator or define the metric; normalise `district` on write (enum/lookup) and in aggregation; count only `available` (and optionally eligible) donors; remove demo data; compute the hint from data; add a legend that states the real thresholds.

---

## 7. Findings (ranked)

Severity: High / Medium / Low (no Critical in the frontend scope). IDs referenced above.

### High
- **F1 No theme system; dark palette hard-wired.** `theme.css:1-247` replaces Tailwind palettes (incl. `--color-white`), `globals.css:258-271` has dark-only hex tokens, no toggle/`prefers-color-scheme`/pre-hydration script. Fix: define semantic tokens (`--bg --surface --fg --muted --border --primary --brand --success --warning --error --info`) for light and dark under `:root` / `[data-theme]`; delete the palette remap; add `<script>` that sets `data-theme` before paint; map Leaflet/OG/poster to the same tokens.
- **F2 Ember (red-orange) is the primary accent everywhere.** 201 refs in 70 files; focus ring, links, nav active, switches, buttons, chatbot. Fix: introduce `primary` (blue/teal) for actions/links/focus/nav and reserve `brand` crimson for logo, blood-type tiles and the single urgent CTA.
- **F3 Primary button fails contrast (2.96:1).** `ui/Button.tsx:24`, `dash/ButtonLink.tsx:10`, `landing/shared.tsx:50`, `pub/linkButton.ts:8`, chat bubble/send (`DonorFaqChatbot.tsx:175,239`). Fix: darker fill or dark text on the new primary (>= 4.5:1) and collapse the four duplicate button implementations into `Button` + `buttonVariants()`.
- **F4 Date/time JSON shape likely not ISO (verify).** `JacksonConfig.java:14-19` builds a bare `ObjectMapper`. Impacted: request/match/circle/slot/camp/report/booking dates and donor `dateOfBirth` (the profile DOB input would come back empty and block re-saving). Fix (backend, one line): `mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)` (or `@JsonFormat`); add one contract test; keep a defensive `toDate()` helper in the client.
- **F5 Server validation details discarded; backend messages shown raw.** `lib/auth.ts:46-49`; used by login, register, donor Save, new request, circle, contact. Fix: `parseApiError(err)` returning `{message, fields}`, map `fields` to inputs, translate by field/code (backend should send codes), never print raw English.
- **F6 Admin overview shows invented numbers.** `AdminService.java:93-98` returns Colombo 12 / Gampaha 8 / Kandy 5 / Galle 3 when there are no requests; otherwise counts all-time requests of every status. Fix: delete demo branch; use the same aggregation as the summary; return `[]` and show the empty state.
- **F7 Wrong fulfilment and donor metrics.** `AdminService.java:149-151` (100% for 0 requests), `analytics/page.tsx:62-64` (unweighted mean -> 84%), `en.json:532` static "25 districts active", `donorCount` counts all profiles (`:147`). Fix as in 6.1.
- **F8 District matching is exact-string.** `AdminService.java:116,123,131`. Fix: validate district against the 25-name list on every write (requests, donors, camps), trim/case-fold in aggregation, add a data-fix migration.
- **F9 No way to cancel, close or mark a request/donation fulfilled in the UI.** `requests/[id]/page.tsx` has no actions; backend `PUT /requests/{id}/cancel` and `POST /matches/{id}/donated` unused. Consequence: FULFILLED never happens, fulfilment is always 0%. Fix: add Cancel / Mark fulfilled / Confirm donation controls with confirm dialogs.
- **F10 Map tiles: OSM direct use without attribution.** 3 files (section 6). Fix: configurable provider (`NEXT_PUBLIC_MAP_TILE_URL`, key, attribution string), show attribution control, update CSP `img-src`, add light/dark tile variants instead of a CSS invert filter.
- **F11 Map not fitted to Sri Lanka.** `SriLankaHeatmap.tsx:43-50`, `AdminMap.tsx:44-51`. Fix: `fitBounds([[5.85,79.5],[9.9,81.95]])`, `maxBounds` with `maxBoundsViscosity: 1`, `minZoom` from fitted zoom.
- **F12 Marker click closes the popup on the analytics page.** `SriLankaHeatmap.tsx:130` dependency on an unstable callback, `analytics/page.tsx:138`. Fix: keep the callback in a ref or `useCallback`, and update markers without `clearLayers()` when only selection changes.
- **F13 Low-Data Mode is a no-op.** Only `donor/page.tsx:291` reads it; localStorage only. Fix: a `LowDataProvider` consumed by maps (static image / no tiles), polling intervals (`matches` 30s, slots 15s, ticker 5 min), motion components, fonts/OG images; auto-enable via `navigator.connection.saveData`.
- **F14 Auth state not bootstrapped on public pages.** `camps/page.tsx:131-153`, `SlotPicker.tsx:60`; only `login` and `dashboard/layout.tsx` call `initialize()`. Fix: call it once in a root client component; also fixes header state for signed-in users on `/`.
- **F15 Chatbot trust and a11y.** "NBTS Grounded" badge static (`DonorFaqChatbot.tsx:139`) and backend always returns `groundedInRules=true` (`DonorFaqAiService.java:101,116`); banner claims "solely" NBTS sources (`en.json chat.banner`); no focus management/Esc; chips clipped; hard-coded name; overlaps the mobile Save bar. Fix: show the badge only when a retrieved source id is returned, render the source, move focus on open, wrap chips, offset the FAB (`bottom` above sticky bars), cap input at 500.
- **F16 Accessibility: modal focus management missing** on CreateCamp, Poster, QR scanner, chat, landing menu; **no skip link outside landing**. Fix: one `Dialog` component (focus trap, Esc, return focus, scroll lock, `inert` background) and a skip link in the root layout targeting `#main`.
- **F17 Contrast failures:** gray-600 text (4.44/4.23), placeholders (4.21), form-control borders 1.46:1, switch track. Fix: raise token values to >= 4.5:1 text / 3:1 boundaries in both themes; ban `text-gray-600` for text.

### Medium
- **F18 i18n runtime:** locale restored in `useEffect` (`i18n.tsx:47`), `<html lang="en">` static, English metadata/OG/global-error, custom `t()` without ICU. Fix: move to next-intl (cookie + server read, ICU plurals, localised metadata/`lang`), CI check for key parity (currently 0 missing, 11/17 extra keys).
- **F19 Plural and locale formatting** (section 3.4/3.5): "1 requests", "1 units", "1 camps"; dates use browser locale; `AnimatedNumber` `'en-US'`. Fix: ICU plural messages, `Intl.DateTimeFormat(locale)`/`NumberFormat(locale)` helpers.
- **F20 `GET /requests/{id}` has no ownership/role check** (`RequestService.java:73-77`): any signed-in user (including donors) can read any request and its coordinates. Fix on backend; frontend request-detail page should handle 403.
- **F21 Triage "queue" is every request.** `AdminService.java:187-188`; dismiss zeroes the score and the row stays (`triage/page.tsx:61-75`); hard-coded English note (`:66`); copy says "Machine learning" but the scorer is rule-based (`RequestTriageAiService.java`, additive rules). Fix: filter `score>0 && !reviewed && status active`, add `reviewedAt/By`, localise note on the server, correct copy.
- **F22 "Verify User" by raw ID and weak verification portal.** `admin/page.tsx:227-250`; `AdminVerificationController.java:37-55` approves any user (no role/PENDING check) and reject has no reason (`verifications/page.tsx:77-92`). Fix: remove the quick form or replace with a searchable pending list showing name/org/documents, confirm dialog, reject-with-reason, audit entry.
- **F23 Missing key `dash.matchStatus.NO_RESPONSE`** (`donor/matches/page.tsx:173`) and raw enums in analytics badges/filters/popups/camp status/roles (`analytics/page.tsx:77,150,265`, `SriLankaHeatmap.tsx:96`, `AdminMap.tsx:97`, `verifications/page.tsx:188`). Fix: add keys; map every enum through `t()`; fail CI on enum without key.
- **F24 Notifications are live-only and single-connection.** `notifications/page.tsx`, `SseNotificationService.java:20-26`. Fix: `GET /notifications` history + unread count; allow multiple emitters per user (list) or use one shared stream via a client-side store; nav badge.
- **F25 Marker/legend semantics.** Count ambiguity (`SriLankaHeatmap.tsx:83`), 3/4 levels, wrong thresholds text (`en.json`), colour-only encoding. Fix: shape/icon per level + text label; badge shows requests with an explicit "req"/"donors" aria-label; legend generated from the same constants as `calculateShortageLevel`.
- **F26 Circle response phone not normalised** (`CircleResponseClient.tsx:104-110`): spaces/dashes fail the backend regex with the generic error. Fix: reuse `normalizePhone/isValidPhone` and show a field error.
- **F27 Server-side fetch uses the public API URL** (`share/[token]/page.tsx:11`, `circle/[token]/page.tsx:11`, `opengraph-image.tsx:22`). Fix: `API_INTERNAL_URL` (server-only) for RSC and OG routes.
- **F28 Login autocomplete + browser-language validation.** `login/page.tsx:102` -> `autoComplete="username"`; `noValidate` + localised errors. Register NIC has no autocomplete.
- **F29 Titles/metadata:** dashboard pages have no title; doubled brand suffix; all English (`layout.tsx:35`, share/circle/privacy/terms). Fix: per-page localised `generateMetadata`, concise titles (<= 30 chars + template).
- **F30 Missing role features in UI:** blood bank has no inventory (only camps), no camp edit/cancel (`PUT/PATCH /camps` unused), admin has no moderation actions on flagged reports, no request filters for admin by status/district beyond chips. Fix per proposal section 7.
- **F31 Admin camps map legend contradicts markers** (`admin/camps/page.tsx:301-310` vs `AdminMap.tsx:75-77,134-146`). Fix: single legend component fed by the same status map; do not reuse the critical colour for scheduled camps.
- **F32 Poster generator:** English-only, preview unscaled (`CampPostGenerator.tsx:81` uses undefined `--preview-zoom`), dead `<style jsx>` (`:185-192`), hard-coded `bg-white/red-*` and `#ffffff`, no QR/link on the poster, `new Date(array)` risk. Fix per proposal 9.2 (template renderer, localised copy, real QR).
- **F33 Share card claims "Verified broadcast" unconditionally** (`ShareCardClient.tsx:191`) and metadata always says "URGENT" (`share/[token]/page.tsx:33`); time-left drops days (`:73`). Fix: use verification and urgency from the API; compute with `Intl.RelativeTimeFormat`/days.
- **F34 Touch targets under 44px** (language switcher 36, chat close 32, switch 24, chips/sm buttons ~30). Fix: `min-h-11 min-w-11` on interactive primitives (visual size can stay smaller via padding).
- **F35 Admin gets a dead "New request" button** (`requests/page.tsx:95` vs `layout.tsx:19`). Fix: hide for ADMIN or allow the route.

### Low
- **F36 Duplicate UI primitives:** 4 button implementations, `Alert` vs `Notice`, two `UrgencyBadge`s, local `DISTRICTS` copy in `CreateCampModal.tsx:30-35`. Consolidate.
- **F37 Donor profile copy:** "Update availability", "Reliability scoring", "Excellent Standing" (`en.json:82,132,224`) are unexplained; default score 50 is shown in the tone logic when no profile (`donor/page.tsx:239`). Rename to "Available to donate", explain scoring.
- **F38 Contact page looks like placeholder data** (`contact/page.tsx:44-51`); move to config and confirm real values.
- **F39 LanguageSwitcher:** English `aria-label`s and menu semantics without arrow keys (`LanguageSwitcher.tsx:52,63-90`).
- **F40 Misc:** film-grain overlay on every page (`globals.css:312`, z-100); `alert()` in `ShareCardClient.tsx:120`; unhandled clipboard rejection (`:99-103`); `Promise.all` in admin overview hides both blocks on one failure (`admin/page.tsx:49`); chatbot history not persisted; `Math.max(...)` over analytics values (`admin/page.tsx:69`).

---

## 8. Summary (10 lines)

1. The redesign made the UI consistent but locked it to a dark, red/orange palette: no theme mechanism, `theme.css` remaps every Tailwind family, and 201 `ember` refs in 70 files must become `primary` blue/teal vs sparing `brand` crimson.
2. Red (`red-*`/`rose-*`) is already almost entirely semantic (~36 of 55 uses); only the poster (18) is decorative.
3. Hard-coded colours are few (30 hex, 24 colour functions, 12 arbitrary classes), concentrated in `globals.css`, OG images, `global-error.tsx` and button shadows; 20 `white` / 6 `black` utilities block theming.
4. Contrast: the primary button is 2.96:1, `gray-600` text 4.0-4.4:1, form borders 1.46:1: new tokens must be computed, not reused.
5. The "known suspects": donor Save works but hides server field errors (and may lose DOB if dates are arrays); availability toggle is correct but silent; Low-Data Mode is a no-op; View My Matches, Back, Refresh, triage/verification/map links all work; Verify User is unsafe/raw-ID; chatbot chips work but clip; language switcher works but flashes English; Sign out works.
6. Biggest integration risks: Jackson serialises java.time as arrays (verify), the generic "Validation failed" banner (field map ignored), `GET /requests/{id}` has no ownership check, and no UI for cancel/fulfil, which explains permanent 0% fulfilment.
7. Admin metrics are wrong at the source: demo numbers when empty, 100% fulfilment for zero requests, unweighted national average (21x100 + 4x0)/25 = 84%, static "25 districts active", donor counts that ignore availability, exact-string district matching.
8. Maps use OSM tiles directly with attribution disabled, no bounds (zoom 7.5 snaps to 8, minZoom 6-7), colour-only markers whose number changes meaning, a legend that contradicts backend thresholds, and a popup that closes itself on click.
9. i18n keys are complete (0 missing), but there is no ICU, no server-side locale, English flash on load, "1 requests/units/camps", browser-locale dates, ~25 hard-coded strings and raw enum labels.
10. Accessibility gaps: no skip link outside landing, no focus management in 4 dialogs, `autocomplete="email"` on login, sub-44px targets; reduced motion and form labelling are in good shape. Highest-value next steps: F1-F3 (tokens + primary), F4/F5 (API contract), F6-F9 (metrics + request lifecycle), F10-F12 (maps).

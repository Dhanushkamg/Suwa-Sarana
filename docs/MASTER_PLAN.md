<USER_REQUEST>
Analyze this implementation plan and create your own improved implementation plan based on it. Also, provide your suggestions and recommendations.


Suwa Sarana — Master Implementation Plan
Written for execution by Google Antigravity
How to use this document with Antigravity

Antigravity works best from a scoped, verifiable task list rather than one giant open-ended prompt. Don't paste this whole file as a single instruction — instead:

Open the repo in Antigravity with this file saved at docs/MASTER_PLAN.md so the agent can reference it via codebase search at any point.
Work one milestone at a time. For each milestone, prompt Antigravity with something like:

"Read docs/MASTER_PLAN.md. Implement Milestone 3 only. Generate an implementation plan first and wait for my approval before writing code."

Review the implementation_plan.md artifact Antigravity produces against the milestone's Steps and Definition of Done below before approving — that's your checkpoint.
After execution, check the Verification section of each milestone against the walkthrough.md artifact (screenshots/test output) Antigravity generates — don't just trust the summary text.
Milestones are ordered by dependency. Don't skip ahead to AI features (7–9) before Milestone 1–3 are done — they assume a tested, fully-wired backend to hang new features off of.

Each milestone below is self-contained: goal, exact file paths, commands, and a checklist Antigravity (or you) can tick off.

Assumed current repo state (per project status report)
suwa-sarana/
  apps/
    frontend/   (Next.js 14, TS, Tailwind, Radix UI)
    backend/    (Spring Boot 3.2, Java 21, JPA, Hibernate, Flyway)
  packages/
    shared-types/
  docker-compose.yml

Database: PostgreSQL + cube/earthdistance, migrations V1__init_schema.sql, V2__add_trust_and_safety_tables.sql, V3__seed_hospital_and_admin_accounts.sql. If Antigravity's repo audit (Milestone 0) finds different actual paths/names, it should update this doc's references accordingly before proceeding — don't silently assume.

Milestone 0 — Repo audit (do this first, always)

Goal: confirm what's actually in the repo before changing anything; earlier status reports can drift from reality.

Steps:

 Run find apps -maxdepth 3 -type d and diff against the assumed structure above.
 List all Flyway migrations present in apps/backend/src/main/resources/db/migration/.
 Run existing test suites (./mvnw test in apps/backend, pnpm test in apps/frontend if configured) and record pass/fail counts as a baseline.
 Grep the frontend for mock/fallback data (grep -rn "mock" apps/frontend/src) and list every page still using mocked responses instead of live API calls.
 Produce a short docs/AUDIT_NOTES.md summarizing actual vs. assumed state.

Definition of Done: docs/AUDIT_NOTES.md exists and every subsequent milestone's file paths have been sanity-checked against it.

Milestone 1 — Backend test coverage (JUnit + Mockito)

Goal: close the most important gap — the core business logic currently has no confirmed tests.

Steps:

 In apps/backend, confirm spring-boot-starter-test (includes JUnit 5 + Mockito) is in pom.xml; add if missing.
 Create DeferralServiceTest.java under apps/backend/src/test/java/.../donor/:
Test age boundaries: 17 (fail), 18 (pass), 60 (pass), 61 (fail)
Test underweight rejection (< 50kg)
Test active deferral blocks eligibility; expired deferral does not
Test multiple overlapping deferrals resolve correctly
 Create MatchingEngineTest.java under .../matching/, with the repository mocked via Mockito:
Deferred donors excluded from results
Already-notified donors (for this request) excluded
Results ordered by distance ascending, then reliability score descending
Radius filtering respected
 Create EscalationSchedulerTest.java:
Radius widens after timeout with insufficient ACCEPTED responses
Radius does NOT widen when enough donors have accepted
Request marked EXPIRED after max lifetime with no matches
 Create ReliabilityScoreServiceTest.java:
Score increases on confirmed donation
Score decreases on NO_RESPONSE after ACCEPTED
Score unaffected by a clean DECLINED
 Create AuthServiceTest.java:
Password hash/verify round-trip
Refresh token rotation invalidates the previous token
Expired/invalid token rejected with correct exception
 Create RequestControllerIntegrationTest.java using MockMvc:
Unverified requester blocked from creating a CRITICAL request (expect 403)
Non-owning user cannot cancel another user's request (expect 403)

Verification:

 ./mvnw test passes with 0 failures.
 ./mvnw test jacoco:report (add the Jacoco plugin if not present) shows coverage on matching, donor, and security packages — target 70%+ on these three packages specifically, not the whole codebase.
Milestone 2 — Requester/hospital verification workflow

Goal: close the trust/safety gap — right now only seeded accounts exist; new signups need an actual admin approval path before they can post high-urgency requests.

Steps:

 Confirm/add a verification_status enum column on users (PENDING, VERIFIED, REJECTED) via a new Flyway migration V4__add_verification_status.sql if not already covering this (check is_verified boolean already present — if it exists, this migration instead adds verification_status and backfills from the boolean, or reuses the boolean if it's sufficient — Antigravity should check before assuming a new column is needed).
 Backend: add GET /api/admin/verifications/pending and POST /api/admin/verifications/{userId}/approve / /reject, admin-only (@PreAuthorize("hasRole('ADMIN')")).
 Backend: enforce in RequestService.createRequest() — reject (403, custom RequesterNotVerifiedException) any CRITICAL request from a non-VERIFIED requester; ROUTINE/URGENT remain allowed with a visible unverified flag returned in the response DTO.
 Frontend: add /dashboard/admin/verifications page listing pending requester/hospital accounts with Approve/Reject actions, following the existing Admin Moderation Portal's design patterns.
 Frontend: on the request-creation form, disable the CRITICAL option (with a tooltip explaining why) for unverified accounts.

Verification:

 Write AdminVerificationControllerTest.java (Mockito) covering: non-admin gets 403, approve/reject transitions the status correctly, a CRITICAL request from a PENDING requester is rejected end-to-end.
 Manual check via Antigravity's browser subagent: log in as an unverified requester, confirm the CRITICAL option is disabled; log in as admin, approve the account, confirm it becomes available.
Milestone 3 — Notification cascade completion (SMS fallback) + full frontend wiring

Goal: close the two gaps your own status report already flagged as "next steps" — these are foundational, not optional.

Steps — backend:

 Define NotificationService interface (if not already present) with a single method notify(DonorProfile donor, BloodRequest request).
 Implement SseNotificationService (wrap existing SSE logic behind this interface if not already structured this way).
 Implement SmsNotificationService using a stub/logging implementation first (LoggingSmsProvider) so the cascade logic can be built and tested without a live SMS account; wire the real provider (Dialog/Mobitel/Twilio — pick based on account availability) behind the same interface afterward.
 Implement NotificationCascadeService: try SSE (if donor has an active connection); if no acknowledgment within a configurable window (e.g. 3 minutes), fall back to SMS. Log every attempt to request_matches or a new notification_log table for auditability.
 Add SMS_PROVIDER_API_KEY to .env.example / application.yml as an environment-variable placeholder — never hardcoded.

Steps — frontend:

 Run the audit list from Milestone 0 (grep -rn "mock") and replace every mock/fallback response with a real call through apps/frontend/src/lib/api.ts to the live backend endpoint, one page at a time:
 /dashboard/donor — availability toggle, profile
 /dashboard/donor/matches — accept/decline actions
 /dashboard/hospital — request creation, matched-donor tracking
 /dashboard/requests* — status views
 /dashboard/admin — stats, report queue
 /dashboard/notifications — confirm the SSE stream is live, not simulated

Verification:

 NotificationCascadeServiceTest.java (Mockito, mocking both channel implementations): confirms SMS fires only after the SSE timeout with no ack, confirms it does NOT fire if SSE was acknowledged in time.
 Antigravity browser subagent: for each page above, capture a screenshot showing real data loaded from the backend (not the previous mock placeholder), and confirm the network tab / response shape matches the DTOs — no console errors.
Milestone 4 — Rate limiting & remaining security checklist

Goal: close the abuse-prevention gap.

Steps:

 Add bucket4j (or equivalent) to apps/backend.
 Rate-limit POST /api/auth/login and POST /api/auth/register (e.g. 10 requests/minute/IP).
 Rate-limit POST /api/requests per authenticated user (e.g. 5 CRITICAL requests/day) to prevent spam.
 Confirm CORS allowed-origin is read from CORS_ALLOWED_ORIGIN env var, not hardcoded or *.
 Confirm NIC numbers and phone numbers are excluded from list-view DTOs (only present in detail views for authorized roles) — grep for any endpoint returning full User/DonorProfile entities directly instead of a DTO.

Verification:

 RateLimitTest.java: 11th login attempt within a minute from the same IP returns 429.
 Confirm via curl -I that an OPTIONS preflight from a disallowed origin is rejected.
Milestone 5 — Advanced feature: Replacement-donor circles

Goal: the most locally-grounded new feature — let a patient/family privately broadcast to their own contacts before/alongside the public match.

Steps — backend:

 New Flyway migration V5__add_donor_circles.sql:
sql
  CREATE TABLE request_circles (
      id            BIGSERIAL PRIMARY KEY,
      request_id    BIGINT NOT NULL REFERENCES blood_requests(id) ON DELETE CASCADE,
      invite_token  VARCHAR(64) UNIQUE NOT NULL,
      created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
      expires_at    TIMESTAMPTZ NOT NULL
  );
  CREATE TABLE circle_responses (
      id            BIGSERIAL PRIMARY KEY,
      circle_id     BIGINT NOT NULL REFERENCES request_circles(id) ON DELETE CASCADE,
      responder_name VARCHAR(150),
      responder_phone VARCHAR(20),
      blood_type    VARCHAR(3),
      responded_at  TIMESTAMPTZ NOT NULL DEFAULT now()
  );
 POST /api/requests/{id}/circle — owning requester generates a shareable invite link (invite_token), no auth required to view/respond to the link itself (guest flow).
 GET /api/circle/{token} — public endpoint showing minimal, non-sensitive request info (blood type, hospital, district, urgency — never patient name/identity).
 POST /api/circle/{token}/respond — guest submits name/phone/blood type; this creates a circle_responses row and, separately, optionally prompts the guest to register as a full donor.
 Guard: circle responses feed into the requester's dashboard as a distinct "Your circle" list, separate from the public matching engine's request_matches — don't merge these two lists, they represent different trust levels.

Steps — frontend:

 Requester dashboard: "Invite your circle" button on a request detail page, generates and displays the shareable link (with a copy-to-clipboard and WhatsApp/Facebook share shortcut).
 New public page apps/frontend/src/app/circle/[token]/page.tsx — no auth required, trilingual, shows request info + a simple response form.

Verification:

 CircleServiceTest.java: invite token uniqueness, expiry enforcement, response recorded correctly.
 Browser subagent: generate a link as a logged-in requester, open it in an incognito/unauthenticated session, submit a response, confirm it appears on the requester's dashboard.
Milestone 6 — Advanced feature: Shareable public request cards

Goal: bridge the platform with the existing Facebook/WhatsApp-posting behavior instead of competing with it.

Steps:

 Reuse the request_circles pattern from Milestone 5, or add a simpler is_publicly_shareable flag directly on blood_requests if the two features should be distinct (Antigravity: default to reusing the circle/token mechanism to avoid duplicating public-link logic — only diverge if the requester explicitly wants a fully public, not circle-scoped, link).
 Generate an Open Graph–friendly public card (/share/[token]/page.tsx) with a clean preview image/title for when the link is pasted into Facebook/WhatsApp.
 Add share buttons (WhatsApp, Facebook, copy-link) on the requester's dashboard.

Verification:

 Confirm Open Graph meta tags render correctly (curl the page and check <meta property="og:..."> tags are present).
 Browser subagent: paste the link into a test context and confirm the preview card shows blood type/urgency/hospital, not patient identity.
Milestone 7 — District shortage heatmap + map integration

Goal: close two related, already-flagged gaps together (the map dependency is shared).

Steps:

 pnpm add leaflet react-leaflet in apps/frontend (open-source, no API key needed — simpler than Mapbox/Google Maps for an internship timeline).
 Backend: GET /api/admin/analytics/district-summary — aggregates donor count, active requests, and fulfillment rate per district.
 Frontend: /dashboard/admin/analytics — Leaflet map of Sri Lanka with a district-level choropleth or marker-cluster overlay showing donor density and shortage indicators (color-coded).
 Frontend: reuse the same Leaflet setup for the donor/requester "distance to match" visual on the request detail page — one map component, two use cases.

Verification:

 DistrictSummaryServiceTest.java: correct aggregation given seeded test data across 2–3 districts.
 Browser subagent: screenshot the analytics page showing the rendered map with at least one district visibly flagged as low-density.
Milestone 8 — AI feature: Conversational multilingual request intake

Goal: highest-impact AI feature — let a stressed requester describe the situation in plain language (EN/SI/TA) instead of filling a form.

Steps — backend:

 Add Groq API client to apps/backend (simple RestClient/WebClient call to https://api.groq.com/openai/v1/chat/completions — OpenAI-compatible schema, model e.g. llama-3.3-70b-versatile).
 GROQ_API_KEY added to env vars, never hardcoded, never logged.
 New module apps/backend/.../ai/RequestIntakeAiService.java:
Input: free-text string + target locale
Prompt: instruct the model to extract {bloodType, urgency, hospitalName, district, unitsNeeded} as strict JSON, and to leave a field null if not confidently present in the text (never guess a blood type that wasn't stated)
Output: parsed into CreateRequestDto and returned to the frontend as a draft, not auto-submitted
 POST /api/requests/ai-draft — accepts free text, returns the draft DTO; does NOT create a request. The existing POST /api/requests (with its full validation and eligibility checks) is still the only way an actual request gets created.
 Global exception handling: if the AI call fails or times out, return a clear error so the frontend falls back to the plain form — never block request creation on the AI being available.

Steps — frontend:

 On the "Create Request" page, add a toggle: "Describe it in your own words" vs. the existing structured form.
 Free-text mode: textarea + submit → calls /api/requests/ai-draft → pre-fills the structured form fields → requester reviews/edits → submits through the normal POST /api/requests flow, unchanged.

Verification:

 RequestIntakeAiServiceTest.java (Mockito, mock the Groq client): well-formed input produces correctly parsed DTO; ambiguous input leaves fields null rather than guessing; malformed AI response is handled gracefully (doesn't throw an unhandled exception).
 Browser subagent: type a sample sentence in the free-text box, screenshot the pre-filled form, confirm the requester can still edit every field before submitting.
Milestone 9 — AI feature: Deferral-rule-grounded FAQ chatbot

Goal: reduce support burden; keep answers consistent with the backend's actual deferral rules.

Steps — backend:

 apps/backend/.../ai/DonorFaqAiService.java: builds a system prompt that includes the actual current deferral rule set (age range, weight minimum, deferral reasons/durations) pulled from a small config object shared with DeferralService — not duplicated by hand, so they can't drift out of sync.
 System prompt explicitly instructs the model: answer only from the given rules and general, non-diagnostic donation guidance; for anything medical/uncertain, respond with "please check with clinical staff" rather than guessing.
 POST /api/faq/ask — public or donor-only (your call), rate-limited (reuse Milestone 4's rate limiter), returns the model's answer plus the locale it responded in.

Steps — frontend:

 Simple chat widget on the donor dashboard and the public landing page, trilingual input, using the existing messages/{en,si,ta}.json i18n setup for UI chrome (the chatbot's own replies come from the model, not the static translation files).

Verification:

 DonorFaqAiServiceTest.java: confirms the system prompt actually contains the live deferral rules (not a stale hardcoded copy) by asserting the prompt string includes values pulled from DeferralService's config.
 Browser subagent: ask "can I donate if I got a tattoo last month" in all three languages, confirm a relevant answer is returned in each.
Milestone 10 — AI feature: Fraud/duplicate triage assist for admin review

Goal: strengthen Milestone 2's verification workflow with AI-assisted prioritization — advisory only, never auto-rejects.

Steps:

 apps/backend/.../ai/RequestTriageAiService.java: runs asynchronously after a request is created (reuse the EscalationScheduler's async job pattern), calls the model with the request's free-text/context fields plus a short history of recent requests from the same requester, and writes back fraud_risk_score (0–100) + ai_flag_reason (short string) to the request row.
 Migration V6__add_ai_triage_fields.sql adding these two columns to blood_requests.
 Admin dashboard: sort/filter the moderation queue by fraud_risk_score; display ai_flag_reason as a hint, clearly labeled "AI-suggested — review required," never as a verdict.
 Explicitly do NOT auto-reject or auto-escalate based on this score — it only affects queue ordering/visibility for a human admin.

Verification:

 RequestTriageAiServiceTest.java: confirms a duplicate-looking request gets a higher score than a normal one (using fixed test inputs, mocked AI response); confirms the score never directly changes blood_requests.status.
 Browser subagent: confirm the admin queue visibly sorts by risk score and the "review required" labeling is present.
Milestone 11 — API docs, deployment, CI

Goal: close the remaining "expected deliverable" gaps.

Steps:

 Confirm springdoc-openapi is present and every controller has @Operation/@Schema annotations; verify /swagger-ui.html renders all endpoint groups (Auth, Donors, Requests, Admin, Circle, AI).
 Add a root GitHub Actions workflow: one job running ./mvnw test for the backend, one job running pnpm turbo run lint test build for the frontend + shared-types.
 docs/DEPLOYMENT.md: steps to deploy apps/backend (Docker → Render/Railway/Fly.io) and apps/frontend (Vercel, root directory set to apps/frontend), plus enabling cube/earthdistance on the managed Postgres instance.
 Confirm all secrets (JWT_SECRET, DB_PASSWORD, SMS_PROVIDER_API_KEY, GROQ_API_KEY) are documented in .env.example files with dummy values and are absent from git history (git log -p -- **/application*.yml should show nothing real).

Verification:

 CI workflow runs green on a test PR.
 /swagger-ui.html manually reviewed, screenshot captured.
Summary: what's in scope vs. deferred

In this plan (Milestones 1–11): test coverage, requester verification, full notification cascade, complete frontend wiring, rate limiting, replacement-donor circles, shareable public cards, district heatmap + maps, conversational AI intake, FAQ chatbot, AI-assisted fraud triage, docs/CI/deployment.

Deliberately deferred (mention as "future work," don't build now): donation camp scheduling, IVR/voice hotline, public blood-bank stock signals, obfuscated donor location, low-data mode, AI-suggested urgency classification (the one AI feature flagged earlier as higher-risk relative to its benefit). Revisit these only after Milestones 1–11 are stable and tested.

</USER_REQUEST>
<ADDITIONAL_METADATA>
The current local time is: 2026-09-10T16:12:08+05:30.

The user's current state is as follows:
Other open documents:
- d:\My new projects\Suwa sarana\apps\frontend\src\lib\apiClient.ts (LANGUAGE_TYPESCRIPT)
- d:\My new projects\Suwa sarana\apps\backend\src\main\java\com\suwasarana\api\notification\NotificationController.java (LANGUAGE_JAVA)
- d:\My new projects\Suwa sarana\apps\backend\src\main\java\com\suwasarana\api\donor\DonorDeferral.java (LANGUAGE_JAVA)
- d:\My new projects\Suwa sarana\apps\backend\src\main\java\com\suwasarana\api\config\CorsConfig.java (LANGUAGE_JAVA)
- d:\My new projects\Suwa sarana\apps\backend\src\main\resources\db\migration\V1__init_schema.sql (LANGUAGE_SQL)
</ADDITIONAL_METADATA>
<USER_SETTINGS_CHANGE>
The user changed setting `Model Selection` from Gemini 3.7 Flash (Medium) to Gemini 3.1 Pro (High). No need to comment on this change if the user doesn't ask about it. If reporting what model you are, please use a human readable name instead of the exact string.
</USER_SETTINGS_CHANGE>
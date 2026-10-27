# Suwa Sarana — Backend Audit (read-only, code-reading only)

Scope: `apps/backend` (sources `src/main/java/com/suwasarana/api`, migrations `V1..V21`, tests `src/test`).
Method: every file under controllers/services/repositories/config/migrations read; nothing executed (no Maven, no runtime). Items marked **[verify]** need a runtime check.
Facts about the baseline:
- `pom.xml` parent is **Spring Boot 4.1.1 / Java 17** (not 3.2). Servlet MVC (`spring-boot-starter-webmvc`), **not reactive** as `_status-report` claims.
- Branch `chore/completion` == `main` (`d32078b`); `git log main..chore/completion` is empty. The earlier fixes live in `3b1b1d5` / `a61a49f`. Everything below is verified against the current working tree.
- `git log --all -G` for `gsk_ / GOCSPX / AKIA / PRIVATE KEY` found only the placeholder in a docs table; no committed live API keys. The JWT dev secret **is** committed (F-01).

---

## 1. ENDPOINT INVENTORY

Legend — Auth: `SC` = SecurityConfig matcher, `PA` = `@PreAuthorize`, `code` = controller/service check. "Rate" = `RateLimitingFilter` (10 req/min/IP for the 7 auth paths, 5/min/IP for `POST /api/requests`; see F-04). Tests: "svc" = service-level unit test only (no HTTP / no role check).

### Auth (`/api/auth/**` — SC permitAll)
| Method | Path | Auth | Ownership/IDOR | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| POST | /api/auth/register | permitAll; role ADMIN rejected in `AuthService.validateNewAccount` (:82); HOSPITAL/BLOOD_BANK/REQUESTER/DONOR self-assignable | n.a. | `@Valid RegisterDto` (email, phone regex, pwd 8–128, names, role). `otpCode` required but **never checked**; only `isPhoneVerified` (:96). NIC not uniqueness-checked | yes | AuthServiceTest (svc), AuthErrorStatusTest.register_asAdmin (HTTP) |
| POST | /api/auth/login | permitAll | n.a. | `@Valid LoginDto` | yes | AuthServiceTest (svc) |
| POST | /api/auth/send-otp | permitAll | n.a. | `@Valid` phone regex | yes (+60 s/phone cooldown) | OtpServiceTest (svc) |
| POST | /api/auth/verify-otp | permitAll | n.a. | `@Valid`; phone has **no pattern** | yes | AuthErrorStatusTest.verifyOtp_wrongCode, OtpServiceTest |
| POST | /api/auth/reset-password | permitAll; needs phone verified by OTP | n.a. (acts on phone owner) | `@Valid` | yes | AuthServiceTest (svc) |
| POST | /api/auth/refresh | permitAll; refresh cookie | token hash lookup | cookie only | **no** | AuthErrorStatusTest (no cookie→401), AuthServiceTest |
| POST | /api/auth/logout | permitAll; revokes only if bearer present | self | none | no | none |
| POST | /api/auth/google/verify | permitAll; Google ID token verified (aud, email_verified) | n.a. | `@Valid` | yes | none |
| POST | /api/auth/google/register | permitAll | n.a. | `@Valid` (phone must be `+94…`, differs from password flow) | yes | none |

### Blood requests (class PA `REQUESTER|HOSPITAL_REQUESTER|ADMIN`)
| Method | Path | Auth | Ownership/IDOR | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| POST | /api/requests | class PA; CRITICAL needs VERIFIED (`RequestService:40`) | requester = caller | `@Valid CreateRequestDto` (type regex, units 1–10, lat/lon range; district free text) | yes | RequestControllerIntegrationTest |
| POST | /api/requests/ai-draft | class PA | n.a. | `@Valid` (≤2000 chars) | **no** | RequestIntakeAiServiceTest (svc) |
| GET | /api/requests | method PA `isAuthenticated()` (overrides class PA → **any role**) | none; non-admin sees OPEN/ESCALATING of everyone, with lat/lon | n.a. | no | none |
| GET | /api/requests/me | class PA | `findByRequesterId(caller)` — yes | n.a. | no | none |
| GET | /api/requests/{id} | method PA `isAuthenticated()` | **No ownership/status check** (F-18) | path | no | none |
| PUT | /api/requests/{id}/cancel | class PA | owner or ADMIN (`RequestService:80`) — yes; no status guard | path | no | RequestControllerIntegrationTest (non-owner 403) |

### Circles
| Method | Path | Auth | Ownership | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| POST | /api/requests/{id}/circle | PA isAuthenticated (any role) | owner only (`AccessDeniedException`) | path | no | CircleServiceTest (svc) |
| GET | /api/requests/{id}/circle/responses | PA isAuthenticated | owner only | path | no | CircleServiceTest (svc) |
| GET | /api/circle/{token} | SC permitAll | token = capability; expiry checked | path | no | CircleServiceTest (svc) |
| POST | /api/circle/{token}/respond | SC permitAll | token | `@Valid` (name≤150, phone regex, blood regex, notes≤500) | **no** | CircleServiceTest (svc) |

### Matches / donors
| Method | Path | Auth | Ownership | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| POST | /api/matches/{id}/respond?response= | PA DONOR | `match.donor.user.id == caller` | enum param | no | none (MatchService untested) |
| POST | /api/matches/{id}/donated?donated= | PA REQUESTER/HOSPITAL/ADMIN | request owner or ADMIN | bool param | no | none |
| GET | /api/donors/me | class PA DONOR | self | n.a. | no | none |
| PUT | /api/donors/me | DONOR | self | `@Valid` (DOB not `@Past`, weight min **30**, optional) | no | none |
| PATCH | /api/donors/me/availability | DONOR | self | `@Valid` | no | none |
| GET | /api/donors/me/matches | DONOR | self (N+1, unpaged) | n.a. | no | none |
| GET | /api/donors/me/bookings | PA DONOR | self | n.a. | no | SlotBookingServiceTest (svc partial) |

### Camps / slots
| Method | Path | Auth | Ownership | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| GET | /api/camps | SC permitAll | public | n.a. | no | DonationCampServiceTest (svc) |
| GET | /api/camps/recommended | SC+PA DONOR | self | n.a. | no | DonationCampServiceTest (svc) |
| GET | /api/camps/my-camps | SC+PA HOSPITAL/BLOOD_BANK | organizer | n.a. | no | none |
| POST | /api/camps | PA HOSPITAL/BLOOD_BANK (no VERIFIED check) | organizer = caller | `@Valid` (no `@Size`, no future date, no start<end) | no | DonationCampServiceTest (svc) |
| PUT | /api/camps/{id} | PA HOSPITAL/BLOOD_BANK | organizer email equals — yes | same | no | none |
| PATCH | /api/camps/{id}/status | PA HOSPITAL/BLOOD_BANK | organizer — yes | `Map<String,String>` body, manual check | no | DonationCampServiceTest (svc) |
| POST | /api/camps/{id}/register | PA DONOR, **410 Gone stub** | n.a. | – | no | (svc test of dead `registerForCamp`) |
| GET | /api/camps/{id}/slots | SC permitAll | public | n.a. | no | none |
| POST | /api/camps/{id}/slots, /slots/bulk | PA HOSP/BB/ADMIN | organizer or ADMIN (`loadManagedCamp`) — yes | `@Valid` (capacity ≤1000, bulk ≤200 slots; no past-time check) | no | SlotBookingServiceTest (svc) |
| GET | /api/hospitals/{id}/slots | SC permitAll | public | – | no | none |
| POST | /api/hospitals/{id}/slots, /slots/bulk | PA HOSP/BB/ADMIN | caller==id or ADMIN — yes | `@Valid` | no | SlotBookingServiceTest (svc) |
| POST | /api/slots/{id}/book | PA DONOR | self; pessimistic lock; eligibility at booking time | path | no | SlotBookingServiceTest (svc) |
| DELETE | /api/slots/{id}/book | PA DONOR | self | path | no | SlotBookingServiceTest (svc) |
| POST | /api/slots/{slotId}/check-in/{bookingId} | PA HOSP/BB/ADMIN | slot owner or ADMIN — yes | path | no | SlotBookingServiceTest (svc) |

### Admin / analytics
| Method | Path | Auth | Ownership | Validation | Rate | Test |
|---|---|---|---|---|---|---|
| POST | /api/admin/requesters/{id}/verify | class PA ADMIN | any user id (incl. ADMIN/DONOR) | path | no | none |
| GET | /api/admin/reports | ADMIN | n.a. (list unpaged) | – | no | none |
| GET | /api/admin/analytics | ADMIN | **returns hard-coded fake data when no requests** | – | no | none |
| GET | /api/admin/analytics/district-summary | ADMIN | – | – | no | DistrictAnalyticsTest (svc) |
| GET | /api/admin/triage/queue | ADMIN | – (all requests, unpaged) | – | no | none |
| PUT | /api/admin/triage/{id}/review | ADMIN | – | `@Valid` notes ≤200 | no | none |
| GET | /api/admin/verifications/pending | class PA ADMIN | returns **all PENDING users incl. donors**, raw list | – | no | AdminVerificationControllerTest (direct call, not HTTP) |
| PUT | /api/admin/verifications/{id}/approve, /reject | ADMIN | any user id | path | no | same |
| GET | /api/analytics/district-summary | SC authenticated (any role) | – | – | no | none |

### Public / misc
| Method | Path | Auth | Validation | Rate | Test |
|---|---|---|---|---|---|
| POST | /api/faq/ask | SC permitAll (calls Groq) | `@Valid` ≤500 chars | **no** | DonorFaqAiServiceTest (svc) |
| GET | /api/inventory/stock-signals | SC permitAll (read-only; no write API exists) | – | no | InventoryServiceTest (svc) |
| POST | /api/contact | SC permitAll | `@Valid` | **no** | none |
| POST/GET | /api/ivr/webhook | SC permitAll, **no Twilio signature check** | params unvalidated | no | IvrControllerTest (direct) |
| GET | /api/notifications/stream (SSE) | authenticated (anyRequest) | – | no | none (cascade logic only: NotificationCascadeServiceTest) |
| GET | /actuator/health | permitAll | – | – | ActuatorSecurityTest |
| * | /actuator/**, /swagger-ui/**, /v3/api-docs/** | ADMIN | – | – | ActuatorSecurityTest (asserts 4xx only) |

HTTP-level role tests that exist: unauthenticated→401, CRITICAL unverified→403, non-owner cancel→403, actuator/swagger gated, CORS preflight. **No allowed/forbidden-role matrix exists for any other endpoint.**

---

## 2. ROLE MATRIX (proposal §7 vs code)

Roles in code: `DONOR, REQUESTER, HOSPITAL_REQUESTER, BLOOD_BANK_REQUESTER, ADMIN` (`user/Role.java`; DB CHECK in V16). There is **no hospital/institution entity** (V14 comment: "no dedicated hospitals table") — a "hospital" is a single user.

| Capability (proposal) | Status | Evidence / class |
|---|---|---|
| Public info, eligibility FAQ/chatbot, campaigns (Guest) | Implemented (chatbot unthrottled, ungrounded — F-06/07) | `FaqController`, `DonationCampController GET /api/camps` |
| Register/login email + Google | Implemented, but no email verification (F-12); hospital/BB self-register | `AuthController` |
| Admin invite-only | Partial: ADMIN blocked at sign-up, but no invite/provisioning path; docs say promote via SQL; seeded admin remains (F-02) | `AuthService:82` |
| Manage own profile/blood type/district/availability | Implemented for DONOR only (`DonorController`). No profile/password-change/phone-change/delete for any role (no UserController) | – |
| Donation history, reliability score, next-donation date | Partial: reliability + totals returned; no history list, no eligibility/next-donation endpoint; the DTO has no deferral info; totals only change via `/donated` which no UI calls and requester cannot discover (F-10) | `DonorService.mapToDto`, `MatchService.markMatchDonated` |
| Receive/respond to match alerts | Partial: `POST /matches/{id}/respond` works; delivery depends on SSE or log-only SMS (F-09) | `MatchController`, `NotificationCascadeService` |
| Create blood request | Implemented (REQUESTER/HOSPITAL/ADMIN). BLOOD_BANK cannot | `RequestController` |
| Track/cancel/close/mark fulfilled | Partial: list-mine + cancel. **No close/fulfil endpoint; `FULFILLED` is never set** (F-08) | `RequestService` |
| Verified badge required for urgent/mass alerts | Partial: only CRITICAL gated; URGENT and camps/slots ungated; REJECTED not blocked; no "mass alert" | `RequestService:40` |
| Hospital: staff accounts, institution requests, confirm donations | **Missing** (staff accounts, institution scope). Confirm donation = request owner only; slot check-in doesn't record a donation | `SlotBookingService.checkIn` |
| Blood bank: inventory by type/component, expiry, thresholds, low-stock alerts, publish, record collections | **Missing**. Only a read-only 3-state status per (hospital, type) fed by seed SQL; no write API, no component/expiry/threshold/collection | `InventoryController/Service`, V9/V10 |
| Blood bank: organise camps | Implemented (camps, slots, check-in) | `DonationCampController`, `SlotBookingController` |
| Create/publish campaigns & AI posters | Camps: Implemented for HOSP/BB (ADMIN cannot create camps, can create slots). Posters: frontend-only, no backend | – |
| Verification queue (approve/reject with reason, document review) | Partial: pending list + approve/reject; **no reason, no documents/uploads (no multipart anywhere), no notification, queue includes donors, two duplicate API sets** | `AdminVerificationController`, `AdminController:30` |
| Moderation: flagged reports | **Stub**: list endpoint exists; **no endpoint creates a report** | `AdminService.getReports`, no POST |
| Suspend/ban/restore users | **Missing** (`UserDetailsImpl.isEnabled()` always true) | – |
| Audit log | Partial: SLF4J logger "AUDIT" only; not persisted/queryable; `logAdminAction` never called; admin verify/approve/reject unlogged | `AuditLogService` |
| Analytics heatmap/shortages/fulfilment | Partial: `/admin/analytics/district-summary` + `/analytics/district-summary` (any authenticated). Wrong metrics (F-14). No hospital/BB-scoped views | `AdminService` |
| AI triage queue | Partial: heuristic score, review zeroes score; no decision/reviewer recorded (F-19) | `RequestTriageAiService`, `AdminService:192` |
| System settings, announcement broadcast | **Missing** | – |
| Export CSV | **Missing** | – |
| Donor contact revealed to requester only after accept | **Missing as a feature** (privacy-safe only by omission): no requester-facing endpoint lists matches or accepted donors; `MatchDto` is donor-facing and has no requester/donor contact | `DonorService.mapToMatchDto` |

---

## 3. FINDINGS (ranked)

### Critical

**F-01 Known JWT secret ships as default; production guard is profile-gated and never activated** — `application.yml:32`, `.env.example`, `docker-compose.yml`, `ProductionSafetyCheck.java:13,27` (`@Profile("prod")`), `Dockerfile` (no profile).
Scenario: deploy using compose/.env.example or forget `JWT_SECRET` → anyone can sign HS384 JWT with `sub=admin@test.com`. `JwtAuthFilter:37` only loads the user by `sub`; V3 creates `admin@test.com` in every DB, and V20 (disabled password) is irrelevant to JWT forging → full ADMIN.
Fix: remove the default (fail startup when `jwt.secret` missing or equals the known value regardless of profile), rotate, generate per-env secret in Secrets Manager.

**F-02 Demo ADMIN / verified HOSPITAL accounts persist in every database and are recoverable through phone OTP** — `V3`, `V10`, `V20`, `AuthService.resetPassword:183`.
Scenario: `admin@test.com` (phone 0777654321) and `hospital@test.com` (0771234567) are VERIFIED rows in prod. V20 only sets `password_hash='!disabled-demo-account'`; `/auth/reset-password` re-enables any account to whoever receives an OTP at that number (or reads the logs, F-03). Also reachable via F-01 and via Google sign-in if that email is ever Google-verifiable.
Fix: new migration `V22` deleting these seeded rows (and V10 data) outside dev; move all demo data to `SEED_DATA_ENABLED` code path only; provision first admin by invite/CLI.

### High

**F-03 SMS is a log-only stub that returns `true`; OTP stored plaintext and written to logs** — `LoggingSmsProvider:22`, `OtpService:43-53` (`setOtpCode(otp)`), `SmsNotificationService:32`, `OtpVerification.otpCode`.
Scenario: in prod no SMS leaves the server, so **no user can complete registration or password reset** (both require a verified phone); OTP and phone appear in INFO logs; notification_logs record status `SENT` for messages never sent.
Fix: real provider (Notify.lk/Dialog/SNS) bean; return false on failure; hash OTP (HMAC) at rest; never log message bodies/phones.

**F-04 Rate limiting keyed on `getRemoteAddr()` is wrong behind a proxy, and the filter likely runs twice** — `RateLimitingFilter:23,59,89`, `application.yml:29`, `SecurityConfig:79`.
- Spring's `ForwardedHeaderFilter` (`forward-headers-strategy: framework`) rewrites scheme/host/port, **not** remote address (the code comment is incorrect) → behind ALB every client shares one bucket: 10 logins/min **globally**, and all audit IPs equal the ALB IP. **[verify]**
- `RateLimitingFilter` is a `@Component implements Filter` (no once-per-request guard) and is also added with `addFilterBefore` → Boot registers it a second time; auth endpoints probably allow ~5/min, `POST /api/requests` ~2/min. `RateLimitTest` calls the filter directly so it cannot see this. **[verify]**
- Bucket maps never evict (memory growth).
Fix: `server.forward-headers-strategy=native` with Tomcat RemoteIpValve + trusted proxy CIDRs; make the filter `OncePerRequestFilter` and `FilterRegistrationBean.setEnabled(false)`; use Caffeine/Redis-backed buckets.

**F-05 No account lockout/per-account throttling; weak password policy** — `AuthService:101-122`, `RegisterDto`/`ResetPasswordDto` (`@Size(8,128)` only).
Scenario: distributed credential stuffing against one account is limited only by IP; no CAPTCHA/Turnstile; 8-char passwords with no breach check accepted.
Fix: per-account failed-attempt counter + progressive delay/lock, Turnstile on login/register/OTP, min 10–12 chars + denylist (HIBP k-anonymity).

**F-06 AI endpoints have no auth/cost/abuse limits** — `SecurityConfig:58` (`/api/faq/**` permitAll), `DonorFaqAiService.callGroqFaq`, `RequestController:44`, `RateLimitingFilter` (neither covered).
Scenario: unauthenticated loop on `/api/faq/ask` sends the full ~10 KB KB + rules prompt to Groq each call → bill/quota exhaustion; each call holds a Tomcat thread up to 16 s (6 s connect + 10 s read) → thread starvation DoS. `/requests/ai-draft` same for any registered requester. No prompt-injection defence (user text is passed raw as the user turn; no output filter).
Fix: per-IP and per-user buckets + daily token budget, circuit breaker/bulkhead, response cache for repeated questions, injection-resistant prompt + output validation.

**F-07 Chatbot is not "grounded" and returns incomplete/incorrect health answers** — `DonorFaqAiService:132` (rule 5 "use your general knowledge"), `:83,97,112` (`groundedInRules` always `true`, even for the generic fallback), `:25` model id `qwen/qwen3.8-27b` (unverified; if invalid every call silently falls back; Qwen3-family may return `<think>` text) **[verify]**, `:155` logs full Groq body.
- Fallback matcher order: `q.contains("age")` (:197) fires before weight → "minimum age **and weight**" returns age only (the owner's observed bug is still present; `DonorFaqAiServiceTest` tests them separately so it never catches it). Substring traps: "age"→"dosage/package", "old"→"cold", "drink"→alcohol answer for "can I drink water", "mata"/"bara" in other words.
- Facts conflict across sources: IVR says "wait 3 to 4 months… surgeries 12 months" (`IvrController:90-91`) vs KB #26 "No… at least four months" and `DeferralRulesConfig` surgery 6 months; KB #23/#40 say first-time donors max 55 but `DeferralService:25` allows ≤60; DB profile weight minimum is 30 kg (`UpdateDonorProfileDto:22`) vs 50 kg rule.
Fix: retrieval-only answers with cited KB entry id + `lastReviewed`; set `grounded` only when a KB entry was used; refuse when none; multi-intent splitting; single source of truth for thresholds (`DeferralRulesConfig`) used by IVR, FAQ, eligibility.

**F-08 Request lifecycle never completes: `FULFILLED` is never set; `MATCHED` is terminal and stuck** — `MatchService:66`, no other writer of `FULFILLED` (grep), `EscalationProcessor` only handles OPEN/ESCALATING, `DataRetentionScheduler:37` only purges EXPIRED/CANCELLED.
Scenario: when accepted count ≥ units the request becomes MATCHED forever: never expires, never re-escalates if an accepted donor no-shows, never counted as fulfilled (district `fulfillmentRate` is always 0 % where total>0 — F-14), and its matches/notification logs/circle PII are retained indefinitely. No close/fulfil endpoint for the requester.
Fix: add `POST /requests/{id}/fulfil|close`, set FULFILLED when donations ≥ units, expire/re-open MATCHED on timeout, include MATCHED in escalation/retention logic.

**F-09 Notifications are effectively fire-and-forget and single-node** — `SseNotificationService:18,22` (in-memory `ConcurrentHashMap<userId,SseEmitter>`, one emitter per user, no heartbeat/event ids), `NotificationCascadeService:86-107`, no inbox/unread endpoint (`NotificationLog` has no controller).
Scenario: a donor not currently connected gets only the stub SMS (F-03) → never learns of the request; a second browser tab replaces the first; behind an ALB with ≥2 tasks, events are lost when the donor's stream is on another instance.
Fix: persist notifications (inbox + read state + replay by `Last-Event-ID`), real SMS/push, Redis/PG LISTEN pub-sub for fan-out.

**F-10 Requester cannot see matches/donors or confirm donations; reliability/history never updates in practice** — `MatchController:35` needs a match id; no endpoint lists matches by request (`findByRequestId` on `RequestMatchRepository` is unused); frontend never calls `/matches/*/donated` (grep).
Scenario: `markDonated` is unreachable → `totalDonations`, `RECENT_DONATION` deferral (the only deferral ever created, `MatchService:101`) and reliability are never written, so donors can be re-matched immediately after donating; the "donor contact only after accept" rule cannot be exercised.
Fix: `GET /requests/{id}/matches` (owner-scoped; donor first name + contact only when ACCEPTED), wire UI, also write deferral on slot check-in.

**F-11 Role self-assignment with no verification gate on privileged capabilities** — `RegisterDto.role`, `AuthService:82-99`, `DonationCampController` (no VERIFIED check), `SlotBookingService.checkIn`.
Scenario: anyone registers as `HOSPITAL_REQUESTER`/`BLOOD_BANK_REQUESTER` (PENDING) and immediately publishes camps/slots visible to all donors under their chosen name, creates URGENT requests, and can check-in/record bookings. VERIFIED only blocks CRITICAL requests; REJECTED users are not blocked.
Fix: require `VERIFIED` for camps/slots/urgent+ requests; restrict self-registration roles to DONOR/REQUESTER and make institutional roles invite/approval based.

**F-12 No email verification; Google sign-in silently links to an unverified local account (pre-hijack)** — `AuthService:241-252` (`verifyGoogleUser` logs into any existing user by email), `register:52` (no email proof).
Scenario: attacker registers `victim@gmail.com` with their own phone/password; the victim later uses Google → receives tokens for the attacker-created account; attacker still knows the password and sees data the victim adds.
Fix: email verification (SES) before activation; on first Google login to a LOCAL account, require verified email, clear `passwordHash` and revoke refresh tokens, record `GOOGLE` link explicitly.

**F-13 Demo/seed data in versioned migrations and runtime DDL in application code** — `V10` (fake users/requests/inventory/circles incl. public token `circle-demo-colombo-2026`, fake pledgers "Kasun Perera"), `V9` seed inventory, `ApiApplication.java:18-26` (`@PostConstruct` drops/re-adds `users_role_check` on every startup, swallows errors, `System.out`), `DataInitializer` (`ALTER TABLE`).
Scenario: production ticker/heatmap/escalation show and act on fake requests; requires DDL privileges at runtime; `application.yml` has `baseline-on-migrate: true` and `out-of-order: true` which can mask a missing V1 on a non-empty DB.
Fix: seed only via dev-only Flyway location (`classpath:db/dev`), delete `fixDbConstraints`, drop `baseline-on-migrate`/`out-of-order` outside dev.

### Medium

**F-14 Analytics are inaccurate/fake** — `AdminService:89-97` returns hard-coded `Colombo 12, Gampaha 8…` when no requests; `:111-116` district match is exact-string (free-text district in `CreateRequestDto`, so "colombo"/"Colombo District" fall off the map); `:144-151` fulfilment `100.0` when total=0 and 0 % otherwise (FULFILLED never set, F-08); `findAll()` on requests and donors each call (:90,110-111) — unbounded; `donorCount` counts unavailable/ineligible donors.
Fix: validate district against the 25-name enum, SQL `GROUP BY` aggregation, return `null`/"no data" instead of 100 %, remove fake branch.

**F-15 Spatial query cannot use its index; matching untested on real Postgres** — `MatchingEngineRepository:16-27` filters with `earth_distance(...)/1000 <= :r`; the GiST index `idx_donor_geo` (V1) is usable only with `earth_box(...) @> ll_to_earth(...)` prefilter → sequential scan with per-row trig (status claim "sub-millisecond index scans" is false). `EndToEndIntegrationTest` is `@Disabled` and stale (RegisterDto fields); CI uses H2 with Flyway disabled (`application-test.yml`), so migrations and this native query never run in CI.
Fix: add `earth_box` prefilter, run `EXPLAIN ANALYZE` at 100k donors, Testcontainers-Postgres tests with Flyway on.

**F-16 Matching rules incomplete** — `MatchingEngineRepository:16` `d.blood_type = :bloodType` (exact only: no ABO/Rh compatibility; O− donors never offered for A+), `ORDER BY reliability_score DESC, distance` (:~25: a distant high-score donor outranks a nearby one in an emergency), `RequestMatch.distanceKm` never computed (always 0), `MatchStatus.NO_RESPONSE` never set, matching and SMS/SSE sends occur inside the `createRequest` transaction (`RequestService:36`) so a later rollback leaves donors alerted to a non-existent request.
Fix: compatibility table, distance-first ordering for CRITICAL, compute distance, send notifications after commit (`@TransactionalEventListener(AFTER_COMMIT)`), response-timeout job.

**F-17 Request spam / alert fatigue** — `RequestService.createRequest` (no per-user daily cap, duplicate detection only adds to score), `EscalationProcessor` re-notifies up to 20 donors per tick, coordinates unrestricted (`CreateRequestDto` lat/lon only range-checked, can be 0,0).
Scenario: a PENDING REQUESTER posts URGENT requests every 12 s (5/min/IP) or from many IPs, each notifying 20 donors and escalating every 5–20 min.
Fix: per-user caps/duplicate suppression, verified-only for URGENT, geo-fence to Sri Lanka, admin kill-switch.

**F-18 Over-broad read of requests (IDOR-style)** — `RequestController:53,70`, `RequestService:73,96`.
Scenario: any authenticated user (e.g. a donor who never matched) enumerates `/api/requests/{id}` with sequential ids, including CANCELLED/EXPIRED requests, and gets hospital + exact lat/lon.
Fix: owner/admin or matched-donor only for `{id}`; for list return coarse location for non-matched donors; status filter.

**F-19 Verification/triage workflow lacks accountability** — `AdminController:30` vs `AdminVerificationController:37,47` (two duplicate APIs, either can change any user including ADMIN, REJECTED→VERIFIED, no reason, no notification, no audit call — `logAdminAction` has zero callers); `GET /pending` returns every PENDING user (all donors are PENDING by default, `User:44`); `AdminService:192-195` review sets score to 0, destroys the AI score and does not store the reviewing admin; triage is a plain heuristic labelled "AI".
Fix: single endpoint with `reason`, persisted `admin_audit` table (actor, target, action, reason, ts), restrict to institutional roles, keep original score + `reviewed_by/at`.

**F-20 Moderation incomplete** — no endpoint creates `RequestReport` (admin reports list is always empty in prod); no suspend/ban; `UserDetailsImpl:55-59` hard-codes enabled/non-locked.
Fix: `POST /requests/{id}/report` (rate limited), `users.status` + enforcement in `CustomUserDetailsService`.

**F-21 Unauthenticated write endpoints with no throttling/CAPTCHA** — `/api/contact` (`ContactController:31`, stores PII, **no endpoint to read messages**), `/api/circle/{token}/respond` (`CircleService:83`, spam + third-party phone numbers; response text "requester has been notified" but no notification code exists), `/auth/send-otp` (any number, 10/min/IP → SMS-pumping cost once F-03 is fixed).
Fix: per-IP+per-target limits, Turnstile, admin inbox for contact, real notification for pledges.

**F-22 IVR webhook is public and unsigned** — `IvrController:21-35`, `SecurityConfig:61`.
Scenario: anyone can GET `/api/ivr/webhook?Digits=1` and read hospital/district/blood type of active CRITICAL requests (otherwise login-gated); spoofed calls; `<Dial>+94112369931</Dial>` hard-coded (`:102`).
Fix: validate `X-Twilio-Signature`, move number to config, verify wording against KB.

**F-23 PDPA gaps: consent, rights, minimisation** — `RegisterDto` has no consent flag/timestamp/policy version; no export/delete/rectify endpoints (V1 comment claims erasure; `slot_bookings.donor_id` and `blood_requests.requester_id` FKs have no cascade, so deleting a user would fail); `users.nic_number` plaintext, validated but unused anywhere; exact home lat/lon stored (status "jitter at registration" is false — jitter exists only on non-owner read in `DonorService:87`, which no endpoint exposes); free-text patient details are sent to Groq (US) with no notice (`RequestIntakeAiService`, `DonorFaqAiService`).
Fix: consent record at sign-up, `GET/DELETE /users/me`, drop or encrypt NIC, store coarse location for matching or geohash, add cross-border notice/DPA, redact before LLM.

**F-24 PII/secrets in logs; log injection** — `OtpService:53,60,68,72,83,91` (phone), `SmsNotificationService:32` (phone), `AuditLogService:23,28,38` (emails; `LOGIN_FAILURE identifier=` logs whatever was typed, can be a password or contain CR/LF), `DonorFaqAiService:155` (full LLM response), `RequestIntakeAiService` parse-failure log prints payload, `JwtAuthFilter:44` logs full stack for every stale token.
Fix: mask (`07*****567`), sanitise CR/LF, drop body logging, log userId not email.

**F-25 Retention gaps** — `DataRetentionScheduler` purges only EXPIRED/CANCELLED requests (30 d) and OTPs (1 d). `refresh_tokens` expired rows never purged (only deleted when presented), `contact_messages`, `notification_logs.recipient/message_body`, `circle_responses` (guest names/phones), MATCHED requests (F-08), logs, `users`.
Fix: scheduled purge jobs per table + documented retention schedule.

**F-26 Time zone handling** — `LocalDateTime.now()/LocalTime.now()/LocalDate.now()` throughout (`NotificationCascadeService:117` quiet hours, `SlotReminderScheduler:43,55`, `DeferralService`, `RequestService`), no `Clock`/zone config, JDBC time zone unset, mixed `TIMESTAMP`/`TIMESTAMPTZ` columns (V7, V14, V18 vs others). Containers run UTC; Sri Lanka is UTC+5:30 → quiet hours shift 5.5 h (donors woken at night / silenced by day), "today" differs for 5.5 h daily.
Fix: inject a `Clock` for `Asia/Colombo`, store UTC (`hibernate.jdbc.time_zone=UTC`), unify column types.

**F-27 Scheduler correctness** — `SlotReminderScheduler:43,55` windows (2 h on an hourly cron; 30 min on a 15-min cron) overlap, so each slot is reminded **twice** per window; no sent flag; `EscalationScheduler`/reminders have no distributed lock (duplicate runs with ≥2 instances).
Fix: `reminder_24h_sent/2h_sent` flags (or non-overlapping windows) + ShedLock.

**F-28 Camp/slot rules and donation recording** — `CreateSlotDto`/`SlotBookingService.buildSlot:132` accept past times; `bookSlot:218` checks eligibility only for *today*, and allows booking past/ended slots and overlapping bookings; `CreateCampDto` has no future-date/start<end/`@Size`; `getUpcomingCamps` returns all SCHEDULED incl. past dates (date-filtered repo method unused); check-in never creates a donation/deferral (`NO_SHOW`/`COMPLETED` unused); organizer has no way to list bookings for a slot.
Fix: validations above, evaluate eligibility at slot date, bookings list endpoint, record donation on check-in.

**F-29 Validation holes causing 500s/inconsistent data** — `AuthService:82-99`/`:259` no NIC uniqueness check and `""` NIC stored (unique constraint V1 → second blank NIC = 500); concurrent duplicate registration = 500 not 409; `new RuntimeException("… not found")` in `DonationCampService:51,140,142,178-184` and `SlotBookingService:199,327` → generic 500 via `GlobalExceptionHandler:118`; camp name/location >255 chars → DB error 500; `UpdateDonorProfileDto` DOB not `@Past`, weight min 30 (rule is 50), blood type fine; phone formats differ (`+94…` required for Google, 10–15 digits for password, seeds use `07…`) → duplicates and lookups only try two forms (`AuthService:~190`).
Fix: normalise phone to E.164, `existsByNic`, map not-found to `ApiException.notFound`, add `@Size`/`@Past`.

**F-30 Token/session hardening** — `AuthService.refreshToken:157-176` not `@Transactional`: concurrent use of one refresh cookie yields two valid successors, no reuse detection/family revoke; access token (15 min) survives logout/password reset (no `jti`/denylist, `JwtTokenProvider`); CSRF disabled (`SecurityConfig:48`) while `/refresh` and `/logout` are cookie-driven — safe with `SameSite=Lax` default, but the deployment guide's `COOKIE_SAME_SITE=None` option makes forced-logout/refresh-rotation CSRF possible (no Origin check); status report says cookie is `SameSite=Strict`, code default is `Lax` (`application.yml:44`).
Fix: transactional rotate-with-reuse-detection, `tokenVersion` claim checked per request, Origin/`Sec-Fetch-Site` check on cookie endpoints.

**F-31 Config hygiene / hard-coded environment** — `application.yml:5` DB password default `postgres123`; compose publishes Postgres 5432 and backend 8080 with `postgres`/seed on and dev JWT; `.env.example` sets `SEED_DATA_ENABLED=true`; `app.frontend.url` is not defined in `application.yml` so `CircleService:27` defaults to `http://localhost:3000` (invite links wrong in prod); `SmsNotificationService:48` hard-codes `https://suwasarana.lk/...`; Google client id default (`application.yml:53`, a public id but binds prod to the dev OAuth client); `run.cmd` contains a DB password; `ProductionSafetyCheck` doesn't cover DB password or CORS localhost.
Fix: no defaults for secrets/URLs in yml, config properties validated at startup, separate `application-prod.yml`.

**F-32 Observability / ops** — no request-id/MDC filter, no JSON logging, no `management.*` config (liveness/readiness groups, metrics registry), no `server.shutdown=graceful` (SSE and in-flight bookings cut), no `spring.datasource.hikari.*` (status 6.2.2 still open; default pool 10 while `createRequest` holds a connection through matching + SMS), `spring.jpa.open-in-view` default true, `Dockerfile` has no `HEALTHCHECK`/JVM memory flags, `System.out` in `ApiApplication`.
Fix: add above; `maximum-pool-size`, `connection-timeout`, `server.shutdown=graceful`, `management.endpoint.health.probes.enabled=true`.

**F-33 Test coverage gaps** — 120 `@Test` in 25 files, but only 5 HTTP-level classes; no allowed/forbidden-role tests; untested: `MatchService`, `DonorService`, Google flow, `AdminController`, SSE, `logout`, contact, analytics; `AdminVerificationControllerTest` calls the controller directly so `@PreAuthorize` isn't exercised; H2 + `ddl-auto=create-drop` + Flyway off hides schema drift and native SQL.
Fix: MockMvc role matrix per endpoint, Testcontainers Postgres with Flyway, `JPA_DDL_AUTO=validate` in CI.

### Low

**F-34 Security headers** — only Spring defaults (nosniff, X-Frame-Options DENY, no-cache; HSTS only when request is secure); no CSP, Referrer-Policy, Permissions-Policy. Status claim "strict CSP" is not true for the backend. Fix: `http.headers(...)` explicit policy; HSTS preload at ALB.

**F-35 Dependencies** — `springdoc-openapi-starter-webmvc-ui 2.5.0` is a Boot 3.x-era line used on Boot 4.1.1/Spring 7 (compatibility risk **[verify]** at runtime: `/v3/api-docs`); `google-api-client 2.2.0` (2023, old transitive tree: guava, httpclient, gson); `bucket4j-core 8.10.1`; `jjwt 0.12.5` (acceptable); no Dependabot/OWASP dependency-check/Trivy in `ci.yml`; Docker tags float (`temurin:17-*-alpine`); CI JDK 21 vs target 17. Fix: bump + add scanners.

**F-36 API consistency** — camp, admin-verification and 429 responses don't use `ApiResponse` (`RateLimitingFilter:~100` emits `status/message/data`, others `success/message/data/timestamp`); `JacksonConfig` custom `ObjectMapper` without disabling `WRITE_DATES_AS_TIMESTAMPS` may serialise dates as arrays **[verify]**.

**F-37 Enumeration** — `/auth/register` returns distinct "Email is already in use!"/"Phone number is already in use!" (`AuthService:87-92`); `reset-password` reveals "no account" only after OTP (acceptable). Fix: uniform message + email notification.

**F-38 Circle tokens** — stored plaintext (`request_circles.invite_token`), valid until request expiry even after cancel/fulfil, error text echoes token (`CircleService:~85`), public info endpoint serves cancelled requests. Fix: hash token, bind validity to request status.

**F-39 Intake draft silently relocates** — `RequestIntakeAiService:237-246` unknown district → "Colombo" and coordinates are the district centroid, not the hospital, so matching radius is centred on the wrong place when the draft is submitted unchanged.

**F-40 Migration safety** — destructive edits are absent (good, `clean-disabled: true`); but FK coverage lacks indexes on `blood_requests(requester_id)`, `request_matches(donor_id)`, `donation_camps(organizer_id, status, scheduled_date)`, `users(verification_status)`, `refresh_tokens(expires_at)`; no `down` scripts; no pagination on any list endpoint (grep `Pageable` = none) while `getMyMatches` is N+1 (lazy `match.request`, `DonorService:109`).

### Section-8 checklist, item by item

| Item | Result |
|---|---|
| SQL injection | None found: JPQL/derived queries; one native query uses bound params (`MatchingEngineRepository`); `JdbcTemplate` only with static strings (`DataInitializer`, `ApiApplication`) |
| Command/XML injection | None; IVR XML values escaped (`IvrController:escapeXml`) |
| Broken access control | Ownership checks present on cancel/circle/match/camp/slot; gaps F-11, F-18, F-19; no role-matrix tests |
| Auth failures | F-05, F-12, F-30; bcrypt cost 12 OK; unknown-user vs bad password both `Invalid credentials` (OK) |
| Password policy | 8–128 chars only (F-05) |
| Reset tokens | OTP-based, 6 digits, 10 min, 5 attempts, cleared via `consume`; **plaintext at rest, logged** (F-03); no email path |
| Email verification | Missing (F-12) |
| Lockout / brute force | IP-only; per-OTP attempts OK (5), new OTP resets attempts every 60 s (≈0.5 %/day/phone) |
| CSRF with cookie refresh | Disabled; acceptable with Lax, see F-30 |
| JWT handling | HS384 from base64 secret, 15 min, subject=email, role re-read from DB each request (good), no jti/aud/iss, not revocable (F-30) |
| CORS | Explicit allow-list from `CORS_ALLOWED_ORIGIN` (comma split), credentials true, explicit headers/methods — OK; status claim "single static string" outdated |
| Security headers | F-34 |
| SSRF | No user-controlled outbound URL (Groq URL constant; Google certs fetch by library) |
| Logging of PII/secrets | F-24 |
| File upload for verification docs | **Does not exist** (no multipart/S3 code) |
| AI abuse / cost / prompt injection | F-06, F-07 |
| HTTPS only | No redirect/HSTS config in app; rely on ALB |

### Data layer / config summary
- Indexes: GiST + `idx_donor_matching` exist; query shape defeats GiST (F-15); missing FK/filter indexes (F-40).
- N+1: `DonorService.getMyMatches`. Unbounded `findAll()`: `AdminService:76,90,110-111,188`, `getAllRequests`.
- Seed vs prod: not separated (F-13). Hikari: not configured (F-32). Defaults/hard-coded: F-31.
- Vulnerable-looking versions: F-35.

---

## 4. UNVERIFIED CLAIMS in `_status-report` §3–5 (code verdicts)

| Claim | Verdict | Runtime test that would prove it |
|---|---|---|
| Auth & RBAC "Complete, production-ready (Google + JWT + refresh rotation)" | **Partial** — rotation and Google token verification exist; no lockout/email verification, pre-hijack, demo admin (F-02, F-05, F-12) | Script: 12 bad logins on one account from 12 IPs; register-as-victim then Google login; forge JWT with dev secret |
| Spatial matching "production-ready sub-millisecond index scans" | **False for index claim / partial** — query defeats GiST; exact blood-type only | Postgres with 100k donors: `EXPLAIN (ANALYZE)` of the native query; Testcontainers matching test |
| Escalation "5→15→25→50 km, Critical 5/Urgent 10/Routine 20 min" | **Verified-in-code (partial)** — steps are +10 (5,15,25,35,45,50), cron every 5 min quantises timings; MATCHED never escalates | Manipulate `lastEscalatedAt`, invoke `processEscalations`, assert radius/notifications; MATCHED+no-show case |
| Request intake (AI + deterministic fallback) | **Verified-in-code** (both paths exist); location is district centroid, unknown district → Colombo | Run 25 EN/SI/TA sentences with and without `GROQ_API_KEY`; assert coordinates |
| NBTS FAQ bot "grounded… Singlish heuristics" | **Partial/false on "grounded"** — prompt allows general knowledge, `grounded` always true, fallback keyword matcher, model id unverified | 100-question EN/SI/TA/Singlish set; "age and weight" multi-part; call with fake key to see fallback; check model id against Groq `/models` |
| AI fraud triage "complete" | **Verified-in-code as heuristic** (4 rules, not AI); review erases score | Create requests as unverified user, assert scores; review then read back |
| Private circles + crypto tokens + OG cards | **Partial** — tokens are two concatenated `UUID.randomUUID()` (SecureRandom; adequate); OG is frontend; "requester notified" is untrue; unthrottled public write | Submit 100 pledges unauthenticated; assert notification; expiry after cancel |
| Camps & slots "complete… QR check-in" | **Partial** — CRUD/booking/lock/check-in work; no VERIFIED gating, no past-time guard, check-in records no donation | Concurrent booking test (N threads, capacity k); book past slot; check-in then read donor totals |
| Leaflet maps / district choropleth "complete" | **Partial/false data** — exact-string districts, fake fallback, 100 %/0 % rates | Seed 25 districts of requests with casing variants; compare API vs SQL aggregates |
| Hospital & blood bank portals "complete" | **Partial** — no inventory write API, no staff accounts, no institution model | Attempt inventory update as BLOOD_BANK (no endpoint) |
| Admin console & verification "hospital documents, audit logs" | **False for documents/audit**, partial otherwise — no upload, audit is logger-only | Approve a user and look for a persisted audit row; upload attempt |
| IVR hotline "beta/functional" | **Verified-in-code**, but unsigned/public (F-22) | `curl` webhook without Twilio signature; Twilio console call |
| Notification cascade "SSE active; SMS stub" | **Verified, understated** — stub reports success and logs `SENT` | Offline donor + new request: check `notification_logs` and provider calls |
| Backend "22 suites / 63+ tests" | **Inaccurate** — 25 files, 120 `@Test`, 1 integration test `@Disabled`, no Postgres | `mvn test` + Surefire report; run with Testcontainers |
| JWT access 15 min + HttpOnly **SameSite=Strict** cookie | **Partial** — HttpOnly yes, hash stored yes, SameSite default **Lax** | Inspect `Set-Cookie` on `/auth/login` |
| Google OAuth "automatic provisioning" | **Partial** — new users need a second call with phone/role/OTP; links existing emails silently | Google ID token for unknown/known email |
| Bucket4j "20 req/min on registration and login" | **False** — 10/min/IP, covers 7 auth paths, probably double-counted, IP wrong behind proxy (F-04) | 12 logins via curl directly and via proxy with different `X-Forwarded-For` |
| Donor location jitter "at registration" | **False** — read-time jitter for non-owners in `DonorService:87`; stored exactly; no endpoint serves other donors' profiles | Inspect DB row after profile PUT |
| Hospital verification of "accreditation documents" | **False** — no document handling | – |
| Live public stock signals (mock) | **Verified (accurate)** — seed rows only, read-only | Update DB row; confirm endpoint reflects, no write API |
| Stack lines (§1–2): "reactive", "strict CSP", "Flyway V1–V16 / 18 migrations", "Spring Boot reactive/REST" | **False/outdated** — servlet MVC, no backend CSP, 21 migrations | – |
| §6.1 JWT secret in repo / Secure flag / CORS multi-origin / §6.2 Hikari | JWT secret **still true**; Secure now configurable (`COOKIE_SECURE`); CORS comma list **done**; Hikari **still not done** | Boot app with default env and inspect; `curl -H Origin` |

---

## 10-line summary
1. Dev JWT secret is committed and the prod guard is profile-gated and unused: forged ADMIN token is possible on a default deploy (F-01).
2. Seeded `admin@test.com`/`hospital@test.com` (VERIFIED) live in every DB and are resettable by OTP to fixed phone numbers (F-02, V3/V10/V20).
3. SMS is a log-only stub that reports success and OTPs are plaintext/logged: registration, reset and donor alerts cannot work in production (F-03, F-09).
4. Rate limiting is IP-only, mis-keyed behind a proxy and probably double-counted; no account lockout; AI and public write endpoints are unthrottled (F-04–F-06, F-21).
5. The chatbot is not grounded (`grounded=true` always, general knowledge allowed), still answers "age and weight" with age only, and IVR/KB/code disagree on thresholds (F-07).
6. Request lifecycle is broken: `FULFILLED` is never set, `MATCHED` is terminal, requesters cannot see matches, and `/donated` is unreachable, so history/deferral never update (F-08, F-10).
7. Hospital/blood-bank roles are self-assignable with no verification gate; no inventory write API, staff accounts, reasons/documents, suspend/ban, exports, announcements (§2, F-11, F-19, F-20).
8. Analytics return fake/incorrect numbers (hard-coded fallback, exact-string districts, 100 % for zero data), and the spatial query cannot use its GiST index (F-14, F-15).
9. PDPA gaps: no consent, export or delete; plaintext NIC; PII in logs; patient text sent to Groq; weak retention; wrong time zone logic (F-23–F-26).
10. CI never runs Flyway or the native SQL (H2, disabled E2E test), there is no role-matrix test, and ops basics (request IDs, graceful shutdown, Hikari, readiness) are missing (F-32, F-33).

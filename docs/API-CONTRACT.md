# Suwa Sarana - HTTP API contract

Derived from the controllers and DTOs in `apps/backend` (branch `chore/completion`) and checked against a running
instance (dev profile, demo data). If code and this file disagree, the code wins; please fix the file.

AI endpoints (chatbot, request intake, triage, poster copy) are specified in sections 11.1-11.3 and in more depth in [`AI.md`](AI.md).

Base URL: `http://localhost:8081` locally (see `docs/LOCAL-RUN.md`), every path below starts with `/api`.

## 0. Conventions

### 0.1 Envelope

Every JSON response except the "raw" ones listed in 0.7 is

```json
{ "success": true,  "message": "Success", "data": <payload or null>, "timestamp": "2026-10-31T08:47:50.712Z" }
{ "success": false, "message": "<English fallback>", "code": "<machine key>", "data": <details or null>,
  "timestamp": "2026-10-31T08:47:50.835Z", "requestId": "a86e0a75-9072-4890-849b-4e7104ab72d5" }
```

* `code` (errors only): machine-readable key `area.reason`. **The frontend translates the code**
  (`authFlow.errors.<code>` today, see section 12 for what must change). It is never renamed once released.
* `message`: human English fallback, safe to show. It is either the generic text for the code
  (`i18n/error_messages_en.properties`) or a more specific English sentence written by the service
  ("Only 40 usable unit(s) of O+ WHOLE_BLOOD in stock"). On success it is a short text such as `Success`; for the auth
  endpoints the success `message` is a code (`auth.otp.sent`, `auth.password.updated` ...), also translatable.
* `data` on errors: `null`, or a field map for validation failures, or `{ "retryAfterSeconds": n }` for 429s
  (the `Retry-After` header carries the same number).
  * `validation.failed` (400): `data = { "<field>": "<field message>" }`. For the auth / account DTOs the field message
    is itself a code (`auth.password.tooShort`, `auth.phone.invalid` ...); for all other DTOs it is an English sentence
    ("must not be blank", "Invalid blood type").
* `requestId` (errors only) equals the `X-Request-Id` response header; send your own `X-Request-Id` (8-64 chars of
  `A-Za-z0-9._-`) to correlate.
* A few legacy responses carry no `code` (generic not-found / conflict texts in camps and slots). Then rely on the status.

### 0.2 Time

* **Moments** (`createdAt`, `expiresAt`, `closedAt`, `respondedAt`, `bookedAt`, `asOf`, `timestamp` ...) are ISO-8601
  instants with a trailing `Z`: `2026-10-31T08:47:50.628Z`. Two exceptions carry an offset instead of `Z`
  (`StockSignal.updatedAt`: `2026-10-31T01:12:39+05:30`); parse with `new Date(...)` in both cases.
* **Wall-clock values read in Sri Lanka** (Asia/Colombo) are zone-less and must NOT be shifted: slot
  `startTime`/`endTime` (`2026-12-05T10:00:00`), camp `scheduledDate` (`2026-12-05`), camp `startTime`/`endTime`
  (`09:00:00`), donor `quietHoursStart/End` (`22:00:00`). Send them the same way.
* Dates (`dateOfBirth`, `donatedOn`, `collectedOn`, `expiresOn`) are `YYYY-MM-DD`.

### 0.3 Authentication

* `POST /api/auth/login` / `register` / `refresh` return `data.accessToken` (JWT, 15 min). Send it as
  `Authorization: Bearer <token>`.
* The refresh token is an `HttpOnly` cookie `refresh_token`, `Path=/api/auth`, `SameSite=Lax`, `Max-Age=604800`
  (`Secure` unless `COOKIE_SECURE=false`, which only the dev profile sets). It is never in a response body.
  Call `/api/auth/refresh` with `credentials: 'include'`; the cookie is rotated on every call; replaying an old one
  signs the user out everywhere. `/refresh` and `/logout` check the `Origin` header against `CORS_ALLOWED_ORIGIN`
  (403 `auth.origin.blocked`).
* 401 `auth.unauthorized` = missing/expired/invalid access token (refresh and retry). 403 `auth.forbidden` = signed in but
  the role may not do this. Suspended accounts get 401 on every call.
* Roles: `DONOR`, `REQUESTER`, `HOSPITAL_REQUESTER`, `BLOOD_BANK_REQUESTER`, `ADMIN`.
  "Institution" below = `HOSPITAL_REQUESTER` or `BLOOD_BANK_REQUESTER`.
* Verification (`verificationStatus`: `PENDING | VERIFIED | REJECTED`) is enforced server-side by `VerificationGuard`:
  institutions must be `VERIFIED` **and** have a confirmed email to publish camps/slots, check donors in, record stock and
  create URGENT/CRITICAL requests (403 `verification.required` / `email.verificationRequired`); `REJECTED` accounts are blocked
  from every write that reaches other people (403 `verification.rejected`); suspended accounts cannot sign in
  (`auth.account.suspended`). A plain `REQUESTER` needs `VERIFIED` only for CRITICAL requests (403
  `request.requester_not_verified`). `ADMIN` always passes.

### 0.4 Language

`Accept-Language: en | si | ta` picks the language of SMS/e-mail the server sends at registration; afterwards
`users.preferred_language` (see `PATCH /api/users/me`) decides. API error `message` is always English.

### 0.5 Rate limiting (429)

Per client IP (and per identifier for OTP / circle / login): login, register, send-otp, verify-otp, reset-password,
google, other auth POSTs, `/api/faq/**`, `POST /api/requests/ai-draft`, `POST /api/ai/poster-copy`, `POST /api/contact`, `POST /api/circle/*/respond`,
`POST /api/requests` (create), `POST /api/requests/*/report`, IVR. Answer:
`429 { code: "rate.limited", data: { retryAfterSeconds }, message: "Too many requests..." }` + `Retry-After` header.
The AI endpoints that cost LLM tokens also have per-user and per-day caps (checked after authentication, same `429 rate.limited`): `ai-draft` and `poster-copy` allow 20 requests/min/IP, 200/day/IP, 5/min/user and 50/day/user; `/api/faq/**` allows 10/min/IP and 300/day/IP. Every number can be overridden with `app.rate-limit.groups.<group>.*` (see `RateLimitProperties`).
Account lockout after repeated bad logins: 429 `auth.account.locked` with the same data/header. Behind a proxy the client IP is
only trusted when the proxy is in `TRUSTED_PROXY_REGEX`.

### 0.6 CORS

Allowed origins: `CORS_ALLOWED_ORIGIN` (comma list; dev: `http://localhost:3000`), credentials allowed, methods
`GET POST PUT PATCH DELETE OPTIONS`, request headers `Authorization, Content-Type, Accept, Accept-Language, Origin,
X-Requested-With, X-Request-Id, Last-Event-ID, Cache-Control` (anything else is refused), exposed `Authorization, X-Request-Id, Retry-After`, preflight cached 1 h.

### 0.7 Responses that are NOT wrapped in the envelope

| Endpoint | Shape |
|---|---|
| `GET /api/camps`, `/api/camps/recommended`, `/api/camps/my-camps` | bare JSON array of `Camp` |
| `POST /api/camps`, `PUT /api/camps/{id}`, `PATCH /api/camps/{id}/status` | bare `Camp` object |
| `GET /api/inventory/stock-signals` | bare array of `StockSignal` |
| `GET /api/users/me/export`, `GET /api/admin/users/{id}/export` | bare JSON document (download) |
| `GET /api/exports/*.csv` | `text/csv; charset=UTF-8` with BOM |
| `GET /api/notifications/stream` | `text/event-stream` |
| `/api/ivr/webhook` | TwiML XML |

Errors of all these endpoints still use the error envelope.

### 0.8 Pagination

* Admin lists (`PageDto`): query `page` (0-based, default 0) and `size` (default 50); response
  `data = { items: [], page, size, total, totalPages }`.
* Contact messages: `data = { items, page, size, totalElements, totalPages }` (note `totalElements`, not `total`).
* Notifications: keyset: `?cursor=<nextCursor>&limit=&unread=` -> `data = { items, nextCursor, unreadCount }`.
* Everything else returns the complete list (donor matches, my requests ...).

### 0.9 Enums

`BloodType` = `A+ A- B+ B- AB+ AB- O+ O-` - `Urgency` = `ROUTINE | URGENT | CRITICAL` -
`RequestStatus` = `OPEN | ESCALATING | MATCHED | FULFILLED | CANCELLED | EXPIRED` -
`MatchStatus` = `PENDING | ACCEPTED | DECLINED | NO_RESPONSE | ...` (donor answers `ACCEPTED` or `DECLINED`) -
`CloseReason` = `NO_LONGER_NEEDED | FOUND_DONOR_ELSEWHERE | OTHER` - `ReportReason` (request report) - see `ReportReason.java` -
`Component` (stock) = `WHOLE_BLOOD | RBC | PLATELETS | PLASMA` - `StockStatus` = `OK | LOW | CRITICAL | EXPIRING_SOON` -
`SlotStatus` = `OPEN | FULL | CANCELLED | ...` - `BookingStatus` = `BOOKED | CHECKED_IN | CANCELLED | ...` -
`ContactStatus` = `NEW | HANDLED` - `UserStatus` = `ACTIVE | SUSPENDED | DELETED` -
`DonationSource` = `MATCH | SLOT_CHECKIN | ...` - `ShortageLevel` = `CRITICAL | WARNING | BALANCED | SURPLUS | NO_DATA`.

---

## 1. Auth (`/api/auth/**`, public unless noted)

`AuthResponse` (`data` of register / login / refresh / accept-invite / google link & register / change-password):

```json
{ "accessToken": "eyJ...", "userId": 31, "role": "DONOR", "email": "donor1@demo.suwasarana.test",
  "phoneNumber": "+94771234567", "verificationStatus": "VERIFIED", "firstName": "Dinesh", "lastName": "Wickramasinghe",
  "emailVerified": true, "preferredLanguage": "en" }
```
plus `Set-Cookie: refresh_token=...`.

| Method + path | Request body | Response `data` | Errors |
|---|---|---|---|
| `POST /auth/register` | `{ email, phoneNumber, password(10-128), firstName, lastName, role, acceptTerms:true, nicNumber?, otpCode?(ignored), preferredLanguage? }`. `role` in `DONOR REQUESTER HOSPITAL_REQUESTER BLOOD_BANK_REQUESTER`. The phone must have been verified with `verify-otp` in the last 30 min. | `AuthResponse` | 400 `validation.failed` (field codes), 409 `auth.register.duplicate` (same for email or phone taken), 409 `auth.nic.inUse`, 429 |
| `POST /auth/login` | `{ username: <email or phone>, password }` (**field is `username`**) | `AuthResponse` | 400 `validation.failed`, 401 `auth.credentials.invalid`, 403 `auth.account.suspended`, 429 `auth.account.locked` / `rate.limited` |
| `POST /auth/send-otp` | `{ phoneNumber }` | `null`, message `auth.otp.sent` | 400 field `auth.phone.invalid`, 429 `auth.otp.cooldown` (60 s), 502 `auth.sms.failed` |
| `POST /auth/verify-otp` | `{ phoneNumber, otpCode }` | `null`, message `auth.otp.verified` | 400 `auth.otp.invalid` (wrong, expired or locked after 5 tries) |
| `POST /auth/reset-password` | `{ phoneNumber, newPassword }` (phone verified by OTP first) | `null`, message `auth.password.updated` | 400 `auth.reset.notAllowed`, field `auth.phone.notVerified`, `auth.password.common` ... |
| `POST /auth/forgot-password` | `{ email }` | `null`, message `auth.reset.emailRequested` (always 200) | 429 |
| `POST /auth/reset-password/token` | `{ token, newPassword }` | `null`, message `auth.password.updated` | 400 `auth.reset.tokenInvalid` |
| `POST /auth/verify-email` | `{ token }` | `null`, message `auth.emailVerification.verified` | 400 `auth.emailVerification.invalid` |
| `POST /auth/resend-verification` | none; **needs a signed-in user** | `null`, message `auth.emailVerification.sent` | 409 `auth.emailVerification.alreadyVerified`, 429 `auth.emailVerification.cooldown` |
| `POST /auth/accept-invite` | `{ token, firstName, lastName, phoneNumber, password, acceptTerms:true, preferredLanguage? }` | `AuthResponse` (role `ADMIN`) | 400 `auth.invite.invalid` |
| `POST /auth/refresh` | none, cookie only | `AuthResponse` + new cookie | 401 `auth.session.notSignedIn` / `auth.unauthorized`, 403 `auth.origin.blocked` |
| `POST /auth/logout` | none (cookie and/or Bearer) | `null`, message `auth.logout.ok`; clears the cookie | 403 `auth.origin.blocked` |
| `POST /auth/google/verify` | `{ idToken }` | `{ requiresRegistration, requiresLinking, email, authResponse? }` | 401 `auth.google.invalid`, 503 `auth.google.unavailable` |
| `POST /auth/google/link` | `{ idToken, password }` | `AuthResponse` | 401 `auth.google.invalid`, 409 `auth.google.accountMismatch` |
| `POST /auth/google/register` | `{ idToken, phoneNumber, firstName, lastName, role, acceptTerms:true, nicNumber?, otpCode?, preferredLanguage? }` | `AuthResponse` | as register |

## 2. Account (`/api/users/me`, any signed-in user)

`MeDto`: `{ id, email, emailVerified, phoneNumber, firstName, lastName, role, verificationStatus, verificationReason, status,
preferredLanguage, authProvider ("LOCAL"|"GOOGLE"), hasPassword, googleLinked, consentVersion, consentedAt, consentRequired, createdAt }`.

| Method + path | Body | Response | Notes |
|---|---|---|---|
| `GET /users/me` | - | `MeDto` | |
| `PATCH /users/me` | partial `{ firstName, lastName, preferredLanguage(en\|si\|ta), phoneNumber }` | `MeDto` | a new phone must be OTP-verified first (`user.phone.*`, `auth.phone.notVerified`) |
| `POST /users/me/change-password` | `{ currentPassword, newPassword }` | `AuthResponse` (new session) | 400 field `auth.password.wrong` |
| `POST /users/me/consent` | - | `MeDto` | records current terms version |
| `POST /users/me/send-otp` | - | `null` | OTP to the account's phone, for deletion |
| `GET /users/me/export` | - | raw JSON document of all personal data | audited |
| `DELETE /users/me` | `{ password }` or `{ otpCode }` | `null` | 400 `privacy.delete.confirmationRequired/Invalid`, 403 `privacy.delete.adminForbidden` |

## 3. Donor (`ROLE_DONOR`)

| Method + path | Body / query | Response `data` |
|---|---|---|
| `GET /donors/me` | - | `DonorProfile`: `{ bloodType, dateOfBirth, weightKg, district, latitude, longitude, available, reliabilityScore, quietHoursStart, quietHoursEnd, totalDonations, livesHelpedEstimate }` (404 "Donor profile not found" until the first PUT) |
| `PUT /donors/me` | `{ bloodType, dateOfBirth(past), weightKg(strictly above the knowledge-base minimum, 50; optional), district, latitude, longitude, quietHoursStart?, quietHoursEnd? }`; 400 `validation.failed` with `data.weightKg = "Weight must be above 50 kg to donate blood"` for 50 or less | `DonorProfile` |
| `PATCH /donors/me/availability` | `{ isAvailable: boolean }` (alias `available` accepted) | `DonorProfile` (field is `available`) |
| `GET /donors/me/matches` | - | `[{ id, status, createdAt, respondedAt, distanceKm, request: { id, patientBloodType, urgency, hospitalName, district, unitsNeeded, status, expiresAt, latitude, longitude, currentRadiusKm } }]` |
| `GET /donors/me/donations?limit=100` | - | `[{ id, donatedOn, source, hostName, district, bloodType, requestId }]`, newest first |
| `GET /donors/me/eligibility` | - | `{ eligible, nextEligibleDate, reasons:[ "AGE_OUT_OF_RANGE" \| "AGE_ABOVE_FIRST_TIME_LIMIT" \| "UNDERWEIGHT" \| "RECENT_DONATION" \| "ACTIVE_DEFERRAL" ], evaluatedOn, lastDonationDate, totalDonations, rules:{minAge,maxAge,maxAgeFirstTimeDonor,minWeightKgExclusive,donationIntervalMonths,minHaemoglobinGdLExclusive,source,ownerConfirmed}, rulesConfirmedByOwner }` |
| `GET /donors/me/bookings` | - | `[SlotBooking]` (section 7) |
| `POST /matches/{id}/respond?response=ACCEPTED\|DECLINED` | - | `null` (404 `match.not_found`, 409 `match.already_answered`) |

## 4. Blood requests

`Request` (`RequestResponseDto`): `{ id, patientBloodType, unitsNeeded, urgency, hospitalName, district, latitude, longitude,
status, currentRadiusKm, createdAt, expiresAt, fraudRiskScore, aiFlagReason, triagedAt, closedAt, closeReason, acceptedCount,
confirmedDonations, coarseLocation }`. `fraudRiskScore/aiFlagReason/triagedAt` are filled only for admins; for non-owners
`latitude/longitude` are rounded (~1 km, `coarseLocation: true`) and the counters are `null`.

| Method + path | Auth | Body | Response | Errors |
|---|---|---|---|---|
| `POST /requests` | REQUESTER, HOSPITAL, ADMIN | `{ patientBloodType, unitsNeeded(1-10), urgency, hospitalName(<=150), district(one of the 25), latitude, longitude }` (coordinates inside Sri Lanka) | `Request` | 400 `geo.unknown_district`, `geo.outside_sri_lanka`, 409 `request.duplicate`, 429 `request.limit_reached` (max 3 active) / `rate.limited`, 403 `request.requester_not_verified`, `verification.*` |
| `POST /requests/ai-draft` | same | `{ text(<=2000), locale? }` | see section 11.1 (every field can be `null`, nothing is saved) | 400, 429 |
| `GET /requests` | any signed-in | - | `[Request]` admins: all; others: only OPEN/ESCALATING, coarse location | |
| `GET /requests/me` | REQUESTER, HOSPITAL, ADMIN | - | `[Request]` | |
| `GET /requests/{id}` | any signed-in | - | `Request` (owner, admin or a matched donor; otherwise 404 `request.not_found`) | |
| `GET /requests/{id}/matches` | REQUESTER, HOSPITAL, ADMIN (owner or admin) | - | `{ requestId, unitsNeeded, acceptedCount, confirmedDonations, matches:[{ matchId, donorFirstName, bloodType, distanceKm, status, notifiedAt, respondedAt?, donated?, contact?:{ phoneNumber } }] }`. `contact` only while `status=ACCEPTED` and the outcome is not recorded | 403 `request.not_owned` |
| `POST /requests/{id}/fulfil` | same | - | `Request` (status `FULFILLED`) | 409 `request.already_closed` |
| `POST /requests/{id}/close` | same | `{ reason? }` optional | `Request` (status `CANCELLED`) | 409 `request.already_closed` |
| `PUT /requests/{id}/cancel` | same | - | `null` (same effect as close) | |
| `POST /requests/{id}/report` | any signed-in | `{ reason, details?(<=500) }` | `null` | 409 `request.report_already_filed`, 400 `request.cannot_report_own` |
| `POST /matches/{id}/donated?donated=true\|false` | REQUESTER, HOSPITAL, ADMIN | - | `null` (records the donation: history, deferral, reliability) | 409 `match.not_accepted`, `donation.already_recorded` |
| `POST /requests/{id}/circle` | any signed-in (owner) | - | `{ requestId, inviteToken, inviteUrl, createdAt, expiresAt }` | |
| `GET /requests/{id}/circle/responses` | any signed-in (owner) | - | `[{ id, responderName, responderPhone, bloodType, notes, respondedAt }]` | |

## 5. Circle (public guest pages)

| Method + path | Body | Response | Errors |
|---|---|---|---|
| `GET /circle/{token}` | - | `{ inviteToken, patientBloodType, unitsNeeded, urgency, hospitalName, district, expiresAt, expired }` | 404 `circle.not_found`, 410 `circle.expired` |
| `POST /circle/{token}/respond` | `{ responderName(2-150), responderPhone, bloodType, notes?(<=500) }` | `{ id, responderName, bloodType, notes, respondedAt, requesterNotified }` (`requesterNotified=false` = saved but no channel reached the requester) | 404/410 as above, 429 |

## 6. Matching alerts / notifications (any signed-in user)

| Method + path | Response |
|---|---|
| `GET /notifications?cursor=&unread=false&limit=` | `{ items:[{ id, type, titleKey, bodyKey, params:{...}, title, body, referenceId, createdAt, readAt }], nextCursor, unreadCount }` (`title/body` are English renderings of `titleKey/bodyKey`+`params`; translate the keys, e.g. `notifications.match.title`) |
| `GET /notifications/unread-count` | `{ count }` |
| `POST /notifications/{id}/read` | the `Notification` (404 `notification.not_found`) |
| `POST /notifications/read-all` | `{ updated }` |
| `GET /notifications/stream` (SSE; send `Last-Event-ID` to replay) | events with `id: <notification id>`, `data: <Notification JSON>`; a `:` comment heartbeat every 20 s |

Notification `type` values: `NEW_MATCH`, `MATCH_ACCEPTED`, `CIRCLE_PLEDGE`, `SLOT_CONFIRMED`, `SLOT_REMINDER`, `LOW_STOCK`,
`REQUEST_CLOSED`, `REQUEST_EXPIRED`, `REQUEST_FULFILLED`, `REQUEST_REOPENED`. Unknown types must be shown with `title`/`body`.

## 7. Camps and slots

`Camp`: `{ id, name, district, location, latitude, longitude, scheduledDate, startTime, endTime, status("SCHEDULED"|"COMPLETED"|"CANCELLED"), organizerName, requiredBloodGroups }`.
`Slot`: `{ id, hostName, startTime, endTime, capacity, bookedCount, spotsLeft, status }`.
`SlotBooking`: `{ id, slotId, donorId, status, bookedAt, checkedInAt, slot: Slot }`.

| Method + path | Auth | Body | Response |
|---|---|---|---|
| `GET /camps` | public | - | bare `[Camp]` upcoming |
| `GET /camps/recommended` | DONOR | - | bare `[Camp]` |
| `GET /camps/my-camps` | institutions | - | bare `[Camp]` |
| `POST /camps` | institutions (VERIFIED + email) | `{ name, district, location, latitude?, longitude?, scheduledDate(not past), startTime, endTime(after start), requiredBloodGroups?("O+,A-") }` | bare `Camp`; 400 `camp.in_past`, `time.range_invalid`, `geo.unknown_district`, `blood.group_invalid` |
| `PUT /camps/{id}` | organiser | same | bare `Camp` |
| `PATCH /camps/{id}/status` | organiser | `{ status }` | bare `Camp` (CANCELLED cancels slots + bookings) |
| `POST /camps/{id}/register` | DONOR | - | 410 (retired: use slots) |
| `GET /camps/{campId}/slots?openOnly=true` | public | - | `[Slot]` |
| `POST /camps/{campId}/slots` | institutions + ADMIN | `{ startTime, endTime, capacity(1-1000) }` | `Slot` (400 `slot.in_past`, `time.range_invalid`) |
| `POST /camps/{campId}/slots/bulk` | same | `{ overallStartTime, overallEndTime, intervalMinutes(5-1440), capacityPerSlot(1-1000) }` | `[Slot]` (max 200) |
| `GET /hospitals/{userId}/slots?openOnly=true` | public | - | `[Slot]` |
| `POST /hospitals/{userId}/slots` and `/slots/bulk` | institutions (own) + ADMIN | as camp slots | `Slot` / `[Slot]` |
| `POST /slots/{slotId}/book` | DONOR | - | `SlotBooking`; 404 `slot.not_found`, 409 `slot.full`, `slot.duplicate_booking`, `slot.in_past`, `slot.booking_overlap`, 422 `donor.ineligible` |
| `DELETE /slots/{slotId}/book` | DONOR | - | `SlotBooking` (CANCELLED) |
| `POST /slots/{slotId}/check-in/{bookingId}` | institutions + ADMIN (owner) | - | `SlotBooking` (CHECKED_IN, donation recorded) |
| `GET /slots/{slotId}/bookings` | institutions + ADMIN (owner) | - | `[{ bookingId, donorId, donorName("Nimal P."), bloodType, status, bookedAt, checkedInAt }]` |

## 8. Blood-bank stock (`ROLE_HOSPITAL_REQUESTER`, `ROLE_BLOOD_BANK_REQUESTER`; **not** ADMIN) and public signals

Writes need a VERIFIED institution with a confirmed email (403 `verification.required` / `email.verificationRequired`).

| Method + path | Body | Response `data` |
|---|---|---|
| `GET /blood-bank/stock` | - | `{ site: Site, asOf, expiringSoonDays, lines:[{ bloodType, component, units, expiringSoonUnits, nextExpiry, status, lowThreshold, criticalThreshold }], batches:[Batch] }` |
| `POST /blood-bank/stock` | `{ bloodType, component, units(1-100000), collectedOn?, expiresOn }` | `Batch` `{ id, bloodType, component, units, collectedOn, expiresOn, status("ACTIVE"\|"DEPLETED"\|"EXPIRED"), daysToExpiry }` |
| `PUT /blood-bank/stock/{id}` | `{ units(0-100000), expiresOn?, note? }` | `Batch` |
| `DELETE /blood-bank/stock/{id}` | - | `null` |
| `POST /blood-bank/stock/issue` | `{ bloodType, component, units, requestId?, note? }` | `[Movement]` (first-expiry-first-out); 409 `inventory.insufficient_stock` |
| `POST /blood-bank/stock/confirm` | - | `Site` (refreshes `confirmedAt`) |
| `GET /blood-bank/stock/movements?limit=100` | - | `[{ id, unitId, bloodType, component, delta, reason(COLLECTION\|ISSUE\|EXPIRY\|ADJUSTMENT\|DISCARD), requestId, note, createdAt }]` |
| `GET /blood-bank/thresholds` | - | `[{ id, bloodType, component, low, critical }]` |
| `PUT /blood-bank/thresholds` | `{ bloodType, component?(default WHOLE_BLOOD), low, critical(<=low) }` | `Threshold`; 400 `inventory.threshold_invalid` |
| `GET /blood-bank/site` | - | `Site` `{ siteName, district, latitude, longitude, publicSignals, confirmedAt }` |
| `PUT /blood-bank/site` | `{ siteName?, district?, latitude?, longitude?, publicSignals? }` | `Site` (400 `geo.unknown_district`) |
| `GET /inventory/stock-signals` (public) | - | **bare** `[{ hospitalName, district, bloodType, status("LOW"\|"CRITICAL"), updatedAt }]` (only stock confirmed in the last 48 h) |

Error codes: 400 `inventory.date_invalid`, 404 `inventory.stock_not_found`.

## 9. Analytics, meta, exports

| Method + path | Auth | Response `data` |
|---|---|---|
| `GET /analytics/summary` | any signed-in (**not public**) | `{ windowDays, generatedAt, activeRequests, awaitingRequests, fulfilledRequests, closedRequests, fulfillmentRate(null=no data), registeredDonors, donorsWithProfile, availableDonors, districtsWithData, districtsTotal }` |
| `GET /analytics/district-summary` | any signed-in | `[DistrictSummary]` always 25 rows: `{ district, latitude, longitude, donorCount, registeredDonors, availableDonors, activeRequests, awaitingRequests, fulfilledRequests, closedRequests, totalRequests, fulfillmentRate(null=no data), shortageLevel, hasData, windowDays }` |
| `GET /admin/analytics/summary` | ADMIN | summary + `expiredRequests, cancelledRequests, unitsOutstanding, unmappedRequests` |
| `GET /admin/analytics/district-summary` | ADMIN | district rows + `expiredRequests, cancelledRequests, unitsOutstanding` |
| `GET /admin/analytics` | ADMIN | legacy `{ "<District>": <active request count> }` (prefer district-summary) |
| `GET /meta/districts` | public | `{ districts:[{ name, latitude, longitude }] (25), bounds:{ minLat, maxLat, minLon, maxLon } }` |
| `GET /exports/requests.csv?from=&to=` | any signed-in; scope by role (admin all, donor matched-to, others own) | CSV: `request_id,created_at,status,urgency,blood_type,units_needed,hospital,district,closed_at,close_reason[,requester_id,fraud_risk_score]` |
| `GET /exports/donations.csv?from=&to=` | any signed-in; scoped | CSV: `donation_id,donated_on,source,blood_type,host,district,request_id[,donor_id,host_user_id]` |

## 10. Admin (`ROLE_ADMIN`)

`AdminUser`: `{ id, email, phoneNumber, firstName, lastName, role, verificationStatus, status, emailVerified, preferredLanguage,
authProvider, registeredAt, verificationReviewedBy, verificationReviewedAt, verificationReason }`.

| Method + path | Body / query | Response |
|---|---|---|
| `GET /admin/verifications/pending?role=&page=&size=` | institutions by default (`role=REQUESTER` for requesters), oldest first | `PageDto<AdminUser>` |
| `GET /admin/verifications?status=PENDING\|VERIFIED\|REJECTED&role=&page=&size=` | | `PageDto<AdminUser>` |
| `PUT /admin/verifications/{userId}/approve` | `{ reason(3-500) }` | `AdminUser` (user notified) |
| `PUT /admin/verifications/{userId}/reject` | `{ reason(3-500) }` | `AdminUser` |
| `GET /admin/users?q=&role=&status=&page=&size=` | | `PageDto<AdminUser>` |
| `POST /admin/users/{userId}/suspend` | `{ reason }` | `AdminUser` (403 `admin.target.protected` / `admin.target.self`) |
| `POST /admin/users/{userId}/restore` | `{ reason }` | `AdminUser` |
| `PUT /admin/users/{userId}/role` | `{ role, reason }` (never ADMIN) | `AdminUser` (400 `admin.role.invalid`) |
| `GET /admin/users/{userId}/export?reason=` | | raw JSON, audited |
| `POST /admin/invitations` | `{ email, firstName?, preferredLanguage? }` | `{ email, expiresAt }` (the token is only in the e-mail); 409 `admin.invite.userExists` |
| `GET /admin/audit?action=&targetUserId=&actorId=&page=&size=` | | `PageDto<{ id, actorId, actorRole, action, targetUserId, reason, details, ipAddress, createdAt }>` |
| `GET /admin/reports` | | `[{ id, requestId, reason, createdAt, reporterId }]` |
| `GET /admin/triage/queue` | | `[TriageItem]` = `Request` + `{ reasons:[{ code, weight }], engineVersion, humanReviewRecommended, reviewed, reviewedBy, reviewedAt, reviewDecision, reviewNote }`; ordered by `fraudRiskScore` desc, requests already reviewed after the pending ones. `reasons[].code` is one of `REQUESTER_HIGH_FREQUENCY`(45) `REQUESTER_REPEAT_24H`(20) `UNVERIFIED_HIGH_UNITS`(35) `UNVERIFIED_MODERATE_UNITS`(15) `SUSPICIOUS_HOSPITAL_NAME`(30) `UNVERIFIED_CRITICAL`(25); the frontend localises by code. The score is rule-based, not machine learning |
| `PUT /admin/triage/{id}/review` | `{ notes?(<=200), decision?("REVIEWED"\|"DISMISSED"\|"CONFIRMED", default REVIEWED) }` | `null`. The AI score is **not** changed; reviewer id, time, decision and note are stored beside it and an `admin_audit_log` row `TRIAGE_REVIEWED` is written (`GET /admin/audit?action=TRIAGE_REVIEWED`). 404 for an unknown request, 400 `validation.failed` for another decision |
| `GET /admin/contact-messages?status=&page=&size=` | | `{ items:[{ id, name, email, subject, message, status, createdAt, handledAt? }], page, size, totalElements, totalPages }` |
| `PATCH /admin/contact-messages/{id}` | `{ status: "HANDLED"\|"NEW" }` | the message |

## 11. Public misc

| Method + path | Body | Response |
|---|---|---|
| `POST /contact` | `{ name, email, subject(3-200), message(10-2000), website? }` (`website` is a honeypot: leave empty) | `data: "..."` text; 429 |
| `GET\|POST /ivr/webhook` | Twilio form, header `X-Twilio-Signature` | TwiML XML; 403 without a valid signature |
| `GET /actuator/health/liveness`, `/readiness` | public | `{ "status": "UP" }`; the aggregate `/actuator/health`, `/actuator/metrics`, swagger are ADMIN only |
| `POST /faq/ask` | public, see section 11.2 | | |
| `POST /ai/poster-copy` | authenticated, see section 11.3 | | |

### 11.1 `POST /api/requests/ai-draft` (REQUESTER, HOSPITAL_REQUESTER, ADMIN)

Request: `{ "text": "<=2000 chars, required", "locale": "en|si|ta (optional)" }`. Phone numbers and NIC numbers are removed before the text can reach a language model. Nothing is saved.

Response `data` (real output for "Need 2 units of O+ blood urgently at Karapitiya Teaching Hospital Galle"):

```json
{ "approximateCoordinates": true, "coordinateSource": "DISTRICT_CENTROID", "district": "Galle", "extractedSuccessfully": true,
  "hospitalName": "Karapitiya Teaching Hospital", "latitude": 6.0535, "longitude": 80.221,
  "message": "Draft ready. Please check every highlighted field before you submit; nothing has been sent.",
  "missingFields": [],
  "needsConfirmation": { "patientBloodType": false, "unitsNeeded": false, "urgency": false, "hospitalName": false, "district": false, "coordinates": true },
  "patientBloodType": "O+", "source": "rules", "unitsNeeded": 2, "urgency": "URGENT", "warnings": [] }
```

`patientBloodType, unitsNeeded, urgency, hospitalName, district, latitude, longitude` can each be `null` (an unknown district is `null`, never Colombo; no invented hospital; no default unit count). `missingFields` lists them. `source` is `llm` or `rules`. Coordinates only ever come from the 25 district centroids (`coordinateSource = "DISTRICT_CENTROID"`, `approximateCoordinates = true`). `urgency: "CRITICAL"` is always flagged in `needsConfirmation`. The frontend must show flagged fields as "please check".

### 11.2 `POST /api/faq/ask` (public)

Request (`FaqQuestionDto`):

```json
{ "question": "What is the minimum age and weight to donate blood?",
  "history": [ { "role": "user|assistant", "content": "<=2000 chars" } ],
  "language": "en|si|ta|auto", "replyStyle": "auto|script|romanised", "locale": "en|si|ta" }
```

`question` required, <= 500 chars; `history` optional, <= 20 messages (the server uses the last 6); `language`, `replyStyle` optional; `locale` is a deprecated hint. Violations: `400 validation.failed` with `data = { "question": "Question cannot be blank" }` (or `language`, `replyStyle`, `history`).

Response `data` (`FaqAnswerDto`, real output; `answer` shortened here):

```json
{ "answer": "• Donors must be between 18 and 60 years of age. ...",
  "disclaimer": "General information only, not medical advice or a diagnosis. The Medical Officer at the blood bank makes the final decision on whether you can donate.",
  "followUps": ["What haemoglobin level do I need to donate?", "How often can I donate blood?", "I am 57 and have never donated. Can I donate?"],
  "grounded": true, "groundedInRules": true, "inputStyle": "en", "kind": "ANSWER", "language": "en", "locale": "en",
  "needsConfirmation": true, "replyStyle": "script",
  "sources": [ { "id": "ELIG-AGE", "title": "Donate Blood - National Blood Transfusion Service - Sri Lanka", "url": "https://nbts.health.gov.lk/donate-blood/",
                 "lastReviewed": "2026-10-27", "status": "VERIFIED" } ],
  "translationDraft": false, "translationNote": null, "usedLlm": false }
```

`kind` = `ANSWER | NO_ANSWER | EMERGENCY | OFF_TOPIC | REFUSAL | GREETING | SMALL_TALK | PLATFORM | EMPTY`; `sources[].status` = `VERIFIED | CONFLICT | GAP | SECONDARY`; `inputStyle` = `en | si | ta | singlish | tanglish | unknown`. UI rule: show the green "grounded" badge only when `grounded && !needsConfirmation`; show an amber "confirm with NBTS" state when `needsConfirmation`; `translationNote` must be shown for si/ta drafts. `groundedInRules` and `locale` are deprecated aliases of `grounded` and `language`. Works without any LLM key (deterministic answers from the knowledge base). More: `docs/AI.md` sections 2-4.

### 11.3 `POST /api/ai/poster-copy` (any signed-in role)

Request (`PosterCopyRequestDto`):

```json
{ "template": "URGENT_REQUEST|CAMP|AWARENESS|THANK_YOU", "language": "en|si|ta",
  "facts": { "bloodType": "O+", "units": "2", "hospital": "Karapitiya Teaching Hospital", "district": "Galle", "contactPhone": "0771234567" } }
```

Allowed `facts` keys: `bloodType, units, hospital, district, date (yyyy-MM-dd), time (HH:mm), venue, contactPhone, campName, organiser`; at most 20 entries. Required: URGENT_REQUEST `bloodType, units, hospital, district, contactPhone`; CAMP `campName, date, venue, district`; AWARENESS and THANK_YOU none.

Response `data` (`PosterCopyResponseDto`, real output):

```json
{ "headline": "URGENT: O+ blood needed",
  "body": "2 unit(s) of O+ blood are needed at Karapitiya Teaching Hospital, Galle.\nContact: 0771234567",
  "hashtags": ["#BloodDonation", "#SriLanka", "#Galle", "#DonateBlood"],
  "caption": "Urgent: O+ blood needed at Karapitiya Teaching Hospital, Galle. Please share and donate if you can. Contact 0771234567.",
  "usedLlm": false }
```

Errors use the standard envelope with the code at the top level (`data` is `null`):

| HTTP | `code` | When |
|---|---|---|
| 401 | `auth.unauthorized` | no token |
| 400 | `validation.failed` | bad `template` / `language`, missing `facts`, more than 20 facts |
| 400 | `poster.missing_fact` | a required fact is absent (`"Missing facts: date, venue, district"`) |
| 400 | `poster.unknown_fact` | a key that is not in the allowed list |
| 400 | `poster.invalid_fact` | bad blood type, district not one of the 25, bad phone, `<`, `>` or `{{` in a value, > 120 chars, a phone-like number hidden in free text |
| 422 | `poster.unverified_requester` | URGENT_REQUEST by a requester whose `verificationStatus` is not `VERIFIED` (admins pass) |
| 422 | `poster.patient_name_not_allowed` | a patient / personal key such as `patientName`, `nic`, `photo` |
| 429 | `rate.limited` | see 0.5 |

Real example: `{"success":false,"message":"Only verified requesters can create an urgent request poster.","code":"poster.unverified_requester","data":null,"timestamp":"..."}`. There is no institution-role guard: any signed-in user may generate AWARENESS / CAMP / THANK_YOU copy.

---

## 12. Error codes

HTTP status is the usual one; "any" = depends on the endpoint. Codes of the same area share a prefix.

### Platform

| Code | HTTP | Meaning |
|---|---|---|
| `validation.failed` | 400 | Body/param validation. `data` = field map |
| `request.malformed` | 400 | JSON unreadable / missing parameter |
| `auth.unauthorized` | 401 | Not signed in / token expired or invalid |
| `auth.invalid_credentials` | 401 | Framework authentication failure |
| `auth.forbidden` | 403 | Signed in, wrong role |
| `data.conflict` | 409 | Unique/FK violation or concurrent update |
| `rate.limited` | 429 | Rate limit; `Retry-After` + `data.retryAfterSeconds` |
| `internal.error` | 500 | Unexpected; quote `requestId` |
| `http.not_found` / `http.method_not_allowed` / `http.not_acceptable` / `http.payload_too_large` / `http.unsupported_media_type` / `http.error` | 404 405 406 413 415 other | Framework-level errors |

### Authentication, account, admin, privacy (`AuthCodes`)

Field-level (appear in `data` of `validation.failed`, or as `code` for single-field failures):
`auth.email.required`, `auth.email.invalid`, `auth.phone.required`, `auth.phone.invalid`, `auth.phone.inUse` (409), `auth.phone.notVerified`,
`auth.password.required`, `auth.password.tooShort`, `auth.password.tooLong`, `auth.password.common`, `auth.password.sameAsIdentifier`,
`auth.password.wrong`, `auth.firstName.required`, `auth.lastName.required`, `auth.name.tooLong`, `auth.role.required`, `auth.role.notAllowed`,
`auth.nic.invalid`, `auth.nic.inUse` (409), `auth.consent.required`, `user.language.invalid`, `auth.otp.required`, `auth.token.required`,
`admin.reason.required`, `admin.reason.tooLong`.

| Code | HTTP | Meaning |
|---|---|---|
| `auth.register.duplicate` | 409 | Email or phone already registered (deliberately the same code for both) |
| `auth.credentials.invalid` | 401 | Wrong email/phone or password |
| `auth.account.locked` | 429 | Too many failed logins; `data.retryAfterSeconds` |
| `auth.account.suspended` | 403/401 | Suspended by an administrator |
| `auth.account.deleted` | 401 | Account erased |
| `auth.otp.invalid` | 400 | Code wrong, expired or locked |
| `auth.otp.cooldown` | 429 | Wait before asking for another code |
| `auth.sms.failed` | 502 | SMS provider failed |
| `auth.session.expired` | 401 | Refresh token expired/revoked |
| `auth.session.notSignedIn` | 401 | No refresh cookie / not signed in |
| `auth.origin.blocked` | 403 | `Origin` not allowed on cookie endpoints |
| `auth.emailVerification.invalid` / `.cooldown` / `.alreadyVerified` | 400 / 429 / 409 | Email verification link problems |
| `auth.reset.tokenInvalid` / `auth.reset.notAllowed` | 400 | Reset link invalid / reset not possible |
| `auth.google.invalid` / `.unavailable` / `.linkNeedsPassword` / `.accountMismatch` | 401 / 503 / 409 / 409 | Google sign-in |
| `auth.invite.invalid` | 400 | Admin invitation invalid/expired |
| `email.verificationRequired` | 403 | Institution must confirm its email first |
| `verification.required` | 403 | Institution not yet VERIFIED by an admin |
| `verification.rejected` | 403 | Verification was rejected |
| `admin.target.protected` / `admin.target.self` | 403 | Cannot change that account |
| `admin.verification.notApplicable` / `.alreadyDecided` | 400 / 409 | Verification workflow |
| `admin.status.unchanged` | 409 | Already in that status |
| `admin.role.invalid` | 400 | Role change not allowed |
| `admin.invite.userExists` | 409 | Invitee already has an account |
| `privacy.delete.confirmationRequired` / `.confirmationInvalid` / `.adminForbidden` | 400 / 400 / 403 | Account deletion |
| `user.phone.unchanged` | 400 | New phone equals the current one |
| `common.notFound` | 404 | Generic not found in account/admin APIs |
| Success message codes | 200 | `common.ok`, `auth.otp.sent`, `auth.otp.verified`, `auth.password.updated`, `auth.reset.emailRequested`, `auth.emailVerification.verified`, `auth.emailVerification.sent`, `auth.logout.ok` |

### Requests, matching, donors

| Code | HTTP | Meaning |
|---|---|---|
| `request.requester_not_verified` | 403 | Unverified requester tried a CRITICAL request |
| `request.not_found` | 404 | No such request (or not visible to you) |
| `request.not_owned` | 403 | Not your request |
| `request.not_active` | 409 | Request is no longer active |
| `request.already_closed` | 409 | Already fulfilled / cancelled / expired |
| `request.duplicate` | 409 | Same blood type + hospital already active |
| `request.limit_reached` | 429 | Too many active requests (3) |
| `request.report_already_filed` | 409 | Already reported by you |
| `request.cannot_report_own` | 400 | Cannot report own request |
| `geo.unknown_district` | 400 | Not one of the 25 districts |
| `geo.outside_sri_lanka` | 400 | Coordinates outside Sri Lanka |
| `match.not_found` | 404 | |
| `match.already_answered` | 409 | |
| `match.not_accepted` | 409 | Donor has not accepted |
| `donation.already_recorded` | 409 | |
| `donor.profile_not_found` | 404 | Create the profile first |
| `donor.ineligible` | 422 | Donor not eligible at that time |
| `circle.not_found` / `circle.expired` | 404 / 410 | Private circle invitation |

### AI

| Code | HTTP | Meaning |
|---|---|---|
| `poster.unverified_requester` | 422 | URGENT_REQUEST poster by a non-VERIFIED requester |
| `poster.patient_name_not_allowed` | 422 | Patient / personal field sent to poster-copy |
| `poster.unknown_fact` / `poster.invalid_fact` / `poster.missing_fact` | 400 | Poster facts problems |

### Camps, slots, stock, notifications

| Code | HTTP | Meaning |
|---|---|---|
| `camp.not_found` | 404 | |
| `camp.in_past` | 400 | Camp date in the past |
| `time.range_invalid` | 400 | End not after start |
| `blood.group_invalid` | 400 | Unknown blood group in `requiredBloodGroups` |
| `slot.not_found` | 404 | |
| `slot.full` | 409 | |
| `slot.duplicate_booking` | 409 | Already booked this slot |
| `slot.in_past` | 400/409 | Slot already started |
| `slot.not_bookable` | 409 | Slot cancelled/closed |
| `slot.booking_overlap` | 409 | Overlapping booking |
| `inventory.stock_not_found` | 404 | Batch not found/not yours |
| `inventory.insufficient_stock` | 409 | Not enough usable units |
| `inventory.threshold_invalid` | 400 | `critical` > `low` etc. |
| `inventory.date_invalid` | 400 | Expiry/collection date invalid |
| `notification.not_found` | 404 | |

### Mapping from the codes the frontend uses today (`authFlow.errors.*` in `apiErrors.ts`)

`parseApiError` looks up `authFlow.errors.<code>` with `code` taken from the body. The server sends dotted codes, so
**add the following translation keys (or call a small alias map before the lookup)**:

| Frontend key today | Server code to use |
|---|---|
| `INVALID_CREDENTIALS` | `auth.credentials.invalid` (keep `byStatus: {401: ...}` as the fallback) |
| `ACCOUNT_LOCKED` | `auth.account.locked` |
| `EMAIL_NOT_VERIFIED` | `email.verificationRequired` |
| `EMAIL_IN_USE`, `PHONE_IN_USE` | `auth.register.duplicate` (one code for both, so use one neutral text) and `auth.phone.inUse` |
| `VALIDATION_FAILED` | `validation.failed` |
| `OTP_INVALID` / `OTP_EXPIRED` / `OTP_LOCKED` | `auth.otp.invalid` (the server does not distinguish) ; `auth.otp.cooldown` |
| `TOO_MANY_REQUESTS` | `rate.limited` |
| `TERMS_NOT_ACCEPTED` | `auth.consent.required` |
| `TOKEN_INVALID` / `TOKEN_EXPIRED` | `auth.reset.tokenInvalid`, `auth.emailVerification.invalid`, `auth.invite.invalid` |
| `WEAK_PASSWORD` | field codes `auth.password.tooShort`, `auth.password.common`, `auth.password.sameAsIdentifier`, `auth.password.tooLong` |
| `NETWORK`, `SERVER_ERROR` | client-side only (no response / `internal.error`) |

Field values inside `data` are matched by the frontend with `/^[A-Z][A-Z0-9_]+$/`; the server's field codes are dotted
(`auth.password.tooShort`), so the regex must accept `[a-z][A-Za-z0-9_.]+` as well.

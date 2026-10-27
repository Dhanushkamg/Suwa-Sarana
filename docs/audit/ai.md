# AI, knowledge base, analytics and inventory audit

Scope: proposal sections 3.4, 3.7, 9, 10 (`docs/audit/_proposal.txt`). Read-only audit of branch `chore/completion` at commit `d32078b`. Paths are relative to the repo root. `BE` = `apps/backend/src/main/java/com/suwasarana/api`, `FE` = `apps/frontend/src`. Web facts accessed 2026-10-27.

Companion files in this folder: `kb-candidates.json` (proposed KB entries), `chatbot-eval-sample.json` (20 of the 100+ eval questions).

Method note: the NBTS pages were read with a summarising fetch tool, so wording is paraphrased. A human must re-open each source URL before anything is shipped. What I could not verify is listed as unverified, not guessed.

---

## 1. How the AI features actually work today

### 1.1 Provider, model, key

| Item | Fact | Where |
|---|---|---|
| Provider | Groq OpenAI-compatible endpoint `https://api.groq.com/openai/v1/chat/completions` | `BE/ai/DonorFaqAiService.java:24`, `RequestIntakeAiService.java:25` |
| FAQ model | `qwen/qwen3.8-27b`. Groq lists it as a **Preview** model (evaluation only, "may be discontinued at short notice"); 131k context, reasoning model, `reasoning_effort` and `reasoning_format` parameters exist (console.groq.com/docs/models and /docs/model/qwen/qwen3.8-27b, 2026-10-27). Listed price $0.80/M input, $4.00/M output tokens. | `DonorFaqAiService.java:25` |
| Intake model | `llama-3.3-70b-versatile` (Groq production model) | `RequestIntakeAiService.java:26` |
| Triage | **No LLM.** `RequestTriageAiService` is a deterministic score (duplicate frequency, unit volume by unverified user, short hospital name, CRITICAL by unverified). The "AI" label is a misnomer. | `BE/ai/RequestTriageAiService.java:27-86` |
| Key | `@Value("${groq.api.key:${GROQ_API_KEY:}}")`: Spring property `groq.api.key` or env `GROQ_API_KEY`, default empty. Not in `application.yml`. Passed through `docker-compose.yml:35` (`${GROQ_API_KEY:-}`). Sent only as a Bearer header; never logged. If blank, the LLM branch is skipped with no warning. | `DonorFaqAiService.java:33-34,149`; `RequestIntakeAiService.java:31-32` |
| Timeouts | connect 6 s, read 10 s, per call. **No retries, no circuit breaker, no `max_tokens`, no `reasoning_*` parameters**, `temperature` 0.2 (FAQ) / 0.1 (intake). | `DonorFaqAiService.java:37-39,49-51,138-140` |
| Rate limits | **None** on `/api/faq/ask` or `/api/requests/ai-draft`. `RateLimitingFilter` only covers auth endpoints and `POST /api/requests`. | `BE/config/RateLimitingFilter.java:25-27,59,68` |
| Exposure | `/api/faq/**` is `permitAll` (no login). `/api/requests/ai-draft` needs any valid JWT (any role). | `BE/config/SecurityConfig.java:58`; `RequestController.java:43-48` |

### 1.2 Donor FAQ chatbot: request path

1. UI (`FE/components/chat/DonorFaqChatbot.tsx`) is mounted globally (`FE/app/layout.tsx:59`), so it is public. It POSTs `{question, locale}` to `/faq/ask` (`:74-77`). `locale` is the **UI language** (`useI18n().locale`), not a language detected from the text. No history is sent: the DTO has only `question` and `locale` (`BE/ai/dto/FaqQuestionDto.java:9-13`). Input has no `maxLength`, backend caps at 500 chars (`FaqQuestionDto.java:9`), so a longer message gives a 400 and the generic "unable to reach the eligibility engine" text (`:89-96`).
2. `DonorFaqAiService.askQuestion` (`:78-119`): empty question returns a canned reply with `groundedInRules=false`. Otherwise, if a key exists, call Groq (`:93-95`). Any exception (timeout, 4xx/5xx, 429, JSON parse, empty `choices` via `.get(0)` at `:159`) is caught and only logged at WARN (`:105-107`), then the **rule fallback** runs (`:111-118`). The client cannot tell which path answered.
3. Both paths return `groundedInRules = true` (`:99-101`, `:112-117`) and the fixed English `CLINICAL_DISCLAIMER` (`:26`) in every language.

### 1.3 Prompt (LLM path), verbatim structure (`DonorFaqAiService.java:122-136`)

System message = persona + `VERIFIED KNOWLEDGE BASE (100 Q&A)` (whole `faq-knowledge.txt`, 10,384 bytes, about 2.5k tokens, no retrieval) + `DeferralRulesConfig.generateSystemPromptContext()` (`BE/donor/DeferralRulesConfig.java:38-51`) + 9 response rules. User message = raw question, no delimiter, no history. Notable rules:

- Rule 1: reply in the input language; Singlish input must be answered in Sinhala script.
- Rule 2: a 14-word Singlish glossary (`pachcha`, `wayasa`, `bara`, `bonna`, ...). No Tanglish handling at all.
- Rule 4: reject only clearly unrelated topics.
- **Rule 5: "If not in the knowledge base, use your general knowledge of Sri Lankan NBTS blood donation rules."** This permits ungrounded thresholds and contradicts both "Never invent thresholds" (proposal 9.1) and the UI banner "based solely on verified... NBTS sources" (`messages/en.json:717`).
- **Rule 6: act as platform technical support** with no platform documentation supplied, so answers are invented.
- Rule 7: "State exact deferral durations and thresholds", which conflicts with the "NEVER ... speculative medical clearance" mandate at `DeferralRulesConfig.java:50`.
- Rule 8: 2-4 sentences max. Nothing requires answering every part of a multi-part question.
- No rule on prompt-extraction, abuse, emergencies/symptoms, or "answer only from sources".

### 1.4 Rule-based fallback (`getRuleGroundedAnswer`, `:167-264`)

Ordered substring checks on `question.toLowerCase()`, **first match returns immediately**: tattoo/piercing (`:183`) -> age (`:197`) -> weight (`:209`) -> interval (`:220`) -> alcohol (`:234`) -> medical (`:246`) -> generic. Answers come from the constants in `DeferralRulesConfig`, not from `faq-knowledge.txt`. Language is the `locale` argument, except a Singlish keyword list (`:171-180`) forces `si`. Only EN/SI/TA text; no Tanglish.

### 1.5 Why "What is the minimum age and weight to donate blood?" answered only "18-60"

Root cause (code, deterministic): the question reaches the fallback, and `q.contains("age")` is true at `DonorFaqAiService.java:197`, so the age branch returns at `:203-204` ("must be between 18 and 60 years of age"). The weight branch (`:209`) is never evaluated. The observed text equals this template exactly.

What I can and cannot prove:
- Proven: the fallback is first-match-wins and cannot answer two topics. The 100-entry KB does contain weight (#24, #34) and `DeferralRulesConfig` contains 50 kg, so the data is not missing; the retrieval step (there is none) and the fallback design are the cause.
- Not provable from code: why the LLM branch was not used in that session. Candidates: `GROQ_API_KEY` blank (silent skip, `:93`), 10 s read timeout on a default-thinking reasoning model (no `reasoning_effort`/`max_tokens`), Groq 429/5xx, or the Preview model being unavailable. Check the backend log for `Groq FAQ call failed or timed out` (`:106`) or `Groq API Response Status` (`:154`).
- Contributing: no test covers a two-topic question (`DonorFaqAiServiceTest` only tests single topics, lines 45-83), and the UI shows a green "NBTS Grounded" badge regardless (1.7).

### 1.6 Language handling

| Input style | Behaviour |
|---|---|
| English | LLM answers in English; fallback uses UI locale. A Sinhala-UI user who types English gets a Sinhala fallback answer. |
| Sinhala / Tamil script | LLM: prompt rule 1. Fallback: matches a few script keywords (`වයස`, `බර`, `வயது`, `எடை`, ...) then first-match-wins. |
| Singlish | LLM: glossary + "reply in Sinhala script". Fallback: 19-word list forces `si` (`:171-180`). |
| Tanglish | Not handled anywhere. |

Fallback substring bugs (all in `DonorFaqAiService.java`), each a real wrong answer:
- `"age"` (`:197`) matches "language", "manage", "damage", "package". `"old"` matches "cold", "hold", "told": "Can I donate with a cold?" returns the age rule.
- `"passe"` (`:173`) matches "passed" ("I passed out after donating" switches to Sinhala and returns the interval answer). `"pressure"` (English word) and `"mata"` (inside "automatic", "tomato") also flip the language.
- `"drink"` (`:234`) matches "Can I drink water before donating?" and returns the alcohol rule. The glossary calls `bonna` "drink water" (`:129`) but the fallback maps `bonna` to alcohol (`:234`).
- Tattoo is checked first, so "I am 30 years old with a tattoo, can I donate?" returns only the tattoo rule.

### 1.7 Grounded badge logic

- Header badge: `<span>{t('chat.grounded')}</span>` is rendered unconditionally (`DonorFaqChatbot.tsx:139-141`; label "NBTS Grounded", `messages/en.json:715`). It is not tied to any response field.
- Backend field `groundedInRules` is `true` on every non-empty path (`DonorFaqAiService.java:99-101,112-117`), `false` only for an empty question (`:83-89`), and the UI never reads it (`DonorFaqChatbot.tsx:78` types only `answer` and `disclaimer`).
- Banner "based solely on verified ... NBTS sources" (`:158-161`) is not true under prompt rule 5/6 and when KB facts are unsourced (section 2).
- Net: the badge is a static claim, not a measurement. Proposal 9.1 requires it to show "only when a source was actually used".

### 1.8 Request intake AI (`/dashboard/requests/new`)

- UI: textarea + 3 sample prompts, POST `/requests/ai-draft {text, locale}`; result fills the form; on failure shows `dash.aiUnavailable` and switches to manual form (`FE/app/dashboard/requests/new/page.tsx:104-145`). CRITICAL is downgraded to URGENT for unverified users on the client (`:127`) and enforced server-side at `request/RequestService.java:40-42`.
- Backend: Groq `llama-3.3-70b-versatile`, `response_format: json_object`, strict schema prompt, 8 blood types, 25 districts (`RequestIntakeAiService.java:92-116`). Output is validated: blood type whitelist (`:141-146`), units clamped 1-10 (`:148-151`), urgency enum else URGENT (`:153-160`), district normalised else "Colombo" (`:254-261`). Good containment: model output cannot add fields.
- `locale` is accepted but unused in the prompt (`callGroqLlm(text, locale)` ignores it).
- Fallback regex parser (`:178-235`) has silent hazards:
  - units default to 1 when none found (`:203`);
  - **unknown/missing district becomes Colombo with Colombo coordinates** (`:237-246`), which can route matching to the wrong city;
  - hospital is **invented** as "`<District> General Hospital`" when no pattern matches (`:230-231`);
  - hospital regex only accepts Latin letters ending in "Hospital/Clinic" and the Tamil keyword contains Malayalam letters (`വൈத்தியசாலை`, `:226`), so Sinhala/Tamil hospital names never match.
  - Every path returns `extractedSuccessfully=true` with a "successfully" message, even for the invented defaults (`:84-88`).
- The prompt asks the model to classify urgency autonomously (`:105`), a medical-priority judgement with no human confirmation flag in the response.

### 1.9 Poster / share-card generation (proposal 3.7, 9.2)

There are two separate features, and **neither calls an LLM**:

**A. Camp poster** (`FE/components/camps/CampPostGenerator.tsx`, opened from `CampManagerDashboard.tsx:158`)
- Pipeline: React/Tailwind DOM node fixed to 1080x1080 -> `html-to-image` `toJpeg` quality 0.95 (`:36-46`) -> `<a download>` `blood_drive_<date>.jpg` (`:50-52`). One size, JPEG only.
- All copy is hard-coded English ("ONE DONATION MANY Heartbeats", "National Blood Drive", "BE A LIFESAVER", `:96-168`); only the surrounding modal uses i18n. Date is `toLocaleDateString('en-US')` (`:124`). Time is printed raw from the API (`:132`), likely `09:00:00` with seconds.
- Template facts come from the camp record (name not shown on the image, date, time, location, blood groups). Not LLM, so no hallucination, but the data handling is weak:
  - location is CSS-truncated with an ellipsis (`:140`), losing the address on the image;
  - only the first 4 blood groups are shown, plus "+N" (`:148-157`);
  - **organiser line prints `organizerName.split('@')[0]`** (`:100`), i.e. the email local-part;
  - the "National Blood Drive" label implies national endorsement.
- **No QR code** although `qrcode.react` is a dependency (`package.json:23`); a leftover `<style jsx>` references `#qr-reader` (`:185-192`).
- Fonts: Tailwind default sans plus `font-[cursive]` (`:110`), which resolves to different system fonts per OS. No Sinhala/Tamil web font is loaded or embedded, so Sinhala/Tamil shaping is untested and untestable because no translated copy exists.
- Preview: the 1080 px node sits inside an `overflow-hidden` square with `zoom: var(--preview-zoom, 1)` (`:81`), and `--preview-zoom` is never set anywhere (grep), so on screens narrower than 1080 px the preview is clipped, not scaled.
- Guardrails: none. No verified-organiser check (no "verif" in `CampManagerDashboard.tsx`), no unverified warning, no preview/edit step, no caption/hashtags, no clipboard copy.
- Colours are hard-coded red/amber/emerald, not brand tokens.

**B. Emergency share card** (`FE/app/share/[token]/ShareCardClient.tsx`, `opengraph-image.tsx`, `FE/components/og/emergencyOgImage.tsx`)
- `next/og` `ImageResponse`, 1200x1200 PNG, data fetched live from `/circle/{token}`; if any field is missing it falls back to a generic card (`opengraph-image.tsx:38-41`). Good: facts never invented.
- English strings only ("Urgent donation needed", "needed urgently", `:50-51`, `emergencyOgImage.tsx:75`). `ogParts.tsx:61` uses `fontFamily: 'sans-serif'` with no font data loaded. I believe Satori's bundled font is Latin-only, so a Sinhala/Tamil hospital name would render as boxes; **unverified, test it**.
- Share text is a fixed English string with an emoji (`ShareCardClient.tsx:95-97`). The card header says "verified broadcast" (`share.verifiedBroadcast`, `:191`) but `CircleInfo` carries no verification field (`:19-28`), so the label is not data-driven.
- No QR; the link is a text URL.

### 1.10 AI triage (admin)
Deterministic scoring, saved on the request (`RequestTriageAiService.java:79-85`); exceptions are swallowed silently (`request/RequestService.java:63-65`, empty catch). Admin "review" sets `fraudRiskScore = 0` (`admin/AdminService.java:195`), erasing the score and leaving only a text note, so the AI suggestion is not kept as an audit record. `getTriageQueue` returns every request of every status (`:187-189`) though the API summary says "active".

---

## 2. Knowledge-base fact check

### 2.1 Where each eligibility fact lives today

| Fact | `faq-knowledge.txt` | `DeferralRulesConfig` / prompt | `DeferralService` (matching) | IVR spoken (`IvrController.java:88-91`) | Frontend copy |
|---|---|---|---|---|---|
| Age | 18 min (#21), 60 max (#22), **first-time 55 (#23, #40)** | 18-60 (`:12-13`); no first-time rule | 18-60 (`DeferralService:25`); no first-time rule | 18-60 | "18 and 60" (`en.json:751-752`) |
| Weight | "More than 50 kg" (#24, #34) | "at least 50" (`:41`) | rejects `< 50`, so exactly 50 passes (`:30`) | "at least 50 kilograms" | "Minimum 50 kg" (`en.json:754`) |
| Haemoglobin | "above 12.5 g/dL" (#25, #35) | ">= 12.5" (`:49`) | not checked | not mentioned | - |
| Interval | "at least four months" (#26) | 120 days "(4 months)" (`:15,42`) | `plusDays(120)` (`MatchService:110`) | **"3 to 4 months"** | - |
| Tattoo / piercing | **absent** | 12 months (`:16,43`) | - | "recent tattoos ... 12 months" | - |
| Surgery | absent | 6 months (`:17`); prompt says "6 to 12 months" (`:44`) | - | **12 months** | - |
| Dental extraction | absent | 7 days (`:18,45`) | - | - | - |
| Antibiotics | absent | 7 days (`:19,46`) | - | - | - |
| Alcohol | "should be avoided" (#44) | **24 hours** (`:20,47`) | - | - | - |
| Pregnancy | "No." (#28) | pregnancy "and 12 months after delivery/breastfeeding" (`:21,48`) | - | - | - |
| Travel | malaria 3 years (#56), other 3 months (#57), country answers vague (#58-60) | not present | not checked | not mentioned | - |
| Meal / sleep | main meal within 4 h (#41), 6 h sleep (#45) | not present | - | - | - |
| Post-donation | rest 20 min, plaster 12 h, no lifting 24 h, fluids 4 h (#81-90) | - | - | - | - |
| Hours / contact | NBC 08:00-18:00 daily (#67), others 08:00-16:00 (#70), address (#80) | - | - | dials `+94112369931` | - |
| Booking | "Raktha app/website" (#78) | - | - | - | - |

### 2.2 Internal contradictions and mismatches

1. **Interval:** KB "at least 4 months" vs config 120 days vs IVR "3 to 4 months". A spoken "3 months" invites an early donor. 120 days is also not 4 calendar months.
2. **Surgery:** config 6 months, prompt "6 to 12", IVR 12 months. None sourced.
3. **Tattoo:** config/IVR 12 months, KB silent, official page silent; the NBTS Director is quoted in the press with "over two years" (see 2.3).
4. **Pregnancy:** KB "No", config adds "12 months after delivery/breastfeeding" (unsourced; official only says not pregnant).
5. **Alcohol:** KB "avoid", config "24 hours" (unsourced number).
6. **Weight/Hb strictness:** official "above 50 kg", "above 12.5"; config "at least 50", ">= 12.5"; matching code accepts exactly 50.
7. **First-time donor limit (55)** exists only in KB #23/#40. The matching engine, profile validation and IVR ignore it.
8. **The prompt calls the KB "VERIFIED"** (`:124`) though no entry carries a source or date.
9. IVR is English-only (`Polly.Amy`, `en-US`) in a trilingual country, and omits haemoglobin, first-time limit and travel.
10. `DeferralService` hard-codes 18/60/50 instead of reading `DeferralRulesConfig` (`:25,30`), so edits to the config would not change matching.

### 2.3 Official NBTS guidance found (accessed 2026-10-27)

Primary source: **"Donate Blood – National Blood Transfusion Service – Sri Lanka"**, https://nbts.health.gov.lk/donate-blood/ (English only; footer "Copyright 2025"; no last-updated date; malaria list labelled 2024).

| Fact | Official value | Source | Confidence |
|---|---|---|---|
| Age | 18-60; first-time donors up to 55 | donate-blood page | high |
| Weight | above 50 kg | donate-blood page | high (but see conflict A) |
| Haemoglobin | above 12.5 g/dL | donate-blood page; same value in The Morning, 20 Jun 2023, quoting NBTS Director | high |
| Interval | minimum 4 months, one value for all donors | donate-blood page; The Morning 2 Dec 2025 | high |
| Pregnancy / illness | must not be pregnant; free of serious medical illness | donate-blood page | high |
| Risk behaviours | must be free of them; term undefined | donate-blood page | high (wording sensitive) |
| Malaria-endemic travel | deferred 3 years from return; country list (2024) includes India and South Korea, not UAE | donate-blood page | medium (list read via summariser; verify) |
| Other foreign travel | deferred 3 months from return | donate-blood page | high |
| ID | NIC or valid ID | donate-blood page | high |
| Prep | main meal within 4 h; plenty of fluids; avoid alcohol (no hours); 6 h sleep the night before | donate-blood page | high |
| Contact / hours | National Blood Centre, No. 555/5D, Elvitigala Mawatha, Narahenpita, Colombo 05; daily 8.00 am-6.00 pm; +94 11 236 9931-4; info@nbts.health.gov.lk | https://nbts.health.gov.lk/contact-us/ | high (IVR `+94112369931` matches) |
| Network size | "more than 108 blood banks" | https://nbts.health.gov.lk/blood-bank-map/ | high |

Conflicts with a secondary source (do not resolve by guessing; ask NBTS):
- **A. First-time donor weight.** Official: above 50 kg. The Morning, "NBTS: More blood donations sought", 2 Dec 2025 (https://www.themorning.lk/articles/MgKluuOIWz1nloKkjn1c), quoting NBTS Director Dr Lakshman Edirisinghe: first-time donors must be 18-55 and **exceed 55 kg**.
- **B. Malaria look-back.** Official: 3 years from return. Same article: first-time donors must not have travelled to a country that has not eradicated malaria "within the two years prior".
- **C. Tattoo.** Official page silent. Same article: tattoo "over two years old" is acceptable. Config/IVR say 12 months.

Not found on any official page (so `kb-candidates.json` marks them `gap`, no number to be stated): tattoo/piercing, medications and antibiotics, surgery, dental extraction, breastfeeding/post-partum, vaccination, alcohol hours, post-donation care figures (#81-90), "Raktha" app (#78, no public evidence found), other blood-bank hours (#70; a 2020 WHO notice mentions 8 am-4 pm for pre-booked visits only), mobile-camp rules (NBTS links a Sinhala PDF manual, https://nbts.health.gov.lk/wp-content/uploads/2024/07/organize-mobile-blood-donation-campaign.pdf, not read). The NBTS "Guidlines" and "Educational" pages list no donor-selection document (checked).

### 2.4 Needed from the owner (blocking before the chatbot may state these)
1. NBTS's current written donor-selection guideline (or a Medical Officer's sign-off) for items marked `gap` and conflicts A-C. Contacts on the NBTS site: info@nbts.health.gov.lk, +94 11 236 9931-4.
2. Decision on wording for "risk behaviours" (the 2023 press quote on a specific group is sensitive; the chatbot must not improvise).
3. Confirmation that the 2024 malaria country list is current, and who maintains it.
4. Whether the "Raktha" system exists; otherwise remove KB #77-78.
5. Native Sinhala and Tamil reviewers for answers and for the eval-set wording.

Until then the safe behaviour for `gap` topics is: say no verified figure is held, tell the donor to ask the blood bank Medical Officer or call NBTS. Proposed entries: `docs/audit/kb-candidates.json`.

---

## 3. Analytics / metrics correctness

### 3.1 The two screens use different endpoints and different definitions

| Screen | Endpoint | Counts |
|---|---|---|
| Admin overview "Blood Request Distribution" | `GET /api/admin/analytics` -> `AdminService.getDistrictAnalytics()` (`FE/app/dashboard/admin/page.tsx:50`) | **Every** request row per district, any status, no time window (`AdminService.java:101-104`) |
| Heatmap / telemetry / National KPIs | `GET /api/admin/analytics/district-summary` and `/api/analytics/district-summary` -> `getDistrictSummaries()` (`FE/app/dashboard/admin/analytics/page.tsx:43`, `donor/heatmap/page.tsx:31`) | `activeRequests` = status OPEN, ESCALATING or MATCHED (`:137-141`) |

### 3.2 Root causes (file:line)

1. **Open in overview, 0 active in heatmap (Hambantota, Batticaloa, Colombo, Ratnapura).**
   - Overview counts all statuses (`AdminService.java:102-103`); heatmap counts only live statuses (`:137-141`).
   - Requests live 6 h: `expires_at = now + 6h` (`request/RequestService.java:56`); the 5-minute scheduler marks them EXPIRED (`matching/EscalationProcessor.java:43-48`, `EscalationScheduler.java:25`); rows are deleted only after 30 more days (`scheduler/DataRetentionScheduler.java:32-39`). So any DB whose requests are older than 6 h shows historical counts in the overview and 0 active in the heatmap. Consistent with the screenshots; confirm with the SQL below.
   - Third definition: `RequestRepository.findActiveRequests()` = OPEN, ESCALATING only (`request/RequestRepository.java:14`), used by IVR and the scheduler, so MATCHED is "active" in analytics but not in IVR.
2. **"Fulfilment rate 0%" for Colombo, Hambantota, Batticaloa and "100%" for empty districts.** `RequestStatus.FULFILLED` is **never written anywhere**: grep shows it only in the enum and in the analytics filter (`AdminService.java:144`). The only success transition sets `MATCHED` with the comment "Fully matched" (`matching/MatchService.java:61-66`); `markMatchDonated` records `donated` but does not change the request status (`:75-96`). So `fulfilledRequests` is always 0. Rate = `total > 0 ? fulfilled/total : 100.0` (`AdminService.java:149-151`): any district with requests is 0%, any empty district is a fake 100%.
3. **"National fulfilment 84%" with totals 0.** The KPI is the plain mean of the 25 district rates (`FE/app/dashboard/admin/analytics/page.tsx:62-64`). With 21 empty districts at 100 and 4 districts at 0: (21x100 + 4x0)/25 = **84.0**, exactly the screenshot value (four districts, matching the four in the overview). That is arithmetic consistency, not a DB read; confirm with the SQL below. It also averages rates instead of pooling counts.
4. **"Registered Donors 0" beside "25 districts active".**
   - The number is `COUNT(donor_profiles)` per district (`AdminService.java:111,120-125,147`), not users with role DONOR. A `donor_profiles` row exists only after the donor saves the profile (`donor/DonorService.java:41-52`). Donors who registered but never completed the profile are not counted.
   - Migration `V10` seeds 3 donor profiles (Colombo, Kandy, Galle) and `V20` only blanks their passwords, so a DB showing 0 either never applied V10 (baseline) or has had them removed. Needs a DB check.
   - "25 districts active" is a static i18n string (`messages/en.json:532`), independent of data.
   - `donorCount` ignores availability (`is_available`), deferral and age/weight eligibility, yet the UI labels it "Available donors" (`analytics/page.tsx:165,233`).
5. **Fake data in production.** If there are zero requests, `/api/admin/analytics` returns hard-coded Colombo 12, Gampaha 8, Kandy 5, Galle 3 (`AdminService.java:93-99`).
6. **Silent exclusions.** Requests with `district == null` are dropped from the heatmap (`:115`) but counted as "Unknown" in the overview (`:102`); matching is by exact-case trimmed string (`:116,122`), so "colombo" or "Colombo District" never matches. `district` is a free `VARCHAR(50)` (`V1__init_schema.sql:24,49`) with no constraint to the 25 names.
7. **Markers all "0".** Marker text is `activeRequests > 0 ? activeRequests : donorCount` (`FE/components/maps/SriLankaHeatmap.tsx:83`), mixing two meanings in one number.
8. **Shortage level** `calculateShortageLevel` returns BALANCED when donors and active requests are both 0 (`AdminService.java:171-185`) and compares donor *count* with request *count*, ignoring blood type compatibility and units needed.
9. Performance: both aggregations call `findAll()` on requests and donors and group in Java (`:90,110-111`).

### 3.3 Diagnostic SQL (run read-only on the affected DB)
```sql
SELECT district, status, count(*) FROM blood_requests GROUP BY 1,2 ORDER BY 1,2;
SELECT status, count(*) FROM blood_requests GROUP BY 1;                 -- expect no FULFILLED
SELECT count(*) AS donor_profiles FROM donor_profiles;
SELECT role, count(*) FROM users GROUP BY 1;                           -- DONOR users without profile
SELECT DISTINCT district FROM blood_requests
 WHERE district NOT IN ('Colombo','Gampaha','Kalutara','Kandy','Matale','Nuwara Eliya','Galle','Matara','Hambantota','Jaffna','Kilinochchi','Mannar','Vavuniya','Mullaitivu','Batticaloa','Ampara','Trincomalee','Kurunegala','Puttalam','Anuradhapura','Polonnaruwa','Badulla','Monaragala','Ratnapura','Kegalle');
```

### 3.4 Proposed metric definitions

Use one service and one set of constants; the overview, heatmap, IVR and scheduler all call it. Time basis: server time in `Asia/Colombo`; default window = last 30 days for rates, "now" for live counts.

| Metric | Definition |
|---|---|
| Active request | `status IN (OPEN, ESCALATING)` AND `expires_at > now()`. Report `awaitingDonation` separately = `status = MATCHED` AND `expires_at > now()`. |
| Closed request (in window) | `status IN (FULFILLED, EXPIRED, CANCELLED)` with a `closed_at` timestamp (add column; set on every transition). |
| Fulfilment rate | `fulfilled / (fulfilled + expired + cancelled)` over requests closed in the window. If the denominator is 0, return `null` and the UI shows "No data" (never 100%, never 0%). Optional stricter variant: exclude requester-cancelled from the denominator. |
| FULFILLED transition | Set when `donated = true` matches reach `unitsNeeded` (in `MatchService.markMatchDonated`), not on acceptance. Acceptance means MATCHED. |
| National fulfilment | Pooled: sum(fulfilled) / sum(fulfilled + expired + cancelled) across districts; `null` if 0. Do not average district rates. |
| Registered donors | `COUNT(users WHERE role = DONOR)`; show `withProfile` (rows in `donor_profiles`) alongside. |
| Available donors | profile exists AND `is_available` AND age 18-60 AND weight above 50 kg (when set) AND no active deferral. Reuse `DeferralService.checkEligibility`, which must read `DeferralRulesConfig`. |
| Units outstanding | `SUM(units_needed) - accepted/donated matches` over active requests. |
| Shortage level | Per district (later per blood type): `NO_DATA` if no donors and no requests ever; else compare compatible available donors with units outstanding using thresholds held in config (values to be set by the owner/NBTS, not hard-coded here). |
| Districts with data | count of districts with at least one request in window or one donor; replaces the static "25 districts active". |

Query shape (single grouped query, no `findAll`):
```java
@Query("""
 SELECT r.district AS district,
   SUM(CASE WHEN r.status IN ('OPEN','ESCALATING') AND r.expiresAt > :now THEN 1 ELSE 0 END) AS active,
   SUM(CASE WHEN r.status = 'MATCHED' AND r.expiresAt > :now THEN 1 ELSE 0 END) AS awaiting,
   SUM(CASE WHEN r.status = 'FULFILLED' AND r.closedAt >= :from THEN 1 ELSE 0 END) AS fulfilled,
   SUM(CASE WHEN r.status = 'EXPIRED'   AND r.closedAt >= :from THEN 1 ELSE 0 END) AS expired,
   SUM(CASE WHEN r.status = 'CANCELLED' AND r.closedAt >= :from THEN 1 ELSE 0 END) AS cancelled
 FROM BloodRequest r GROUP BY r.district""")
```
Merge with a donor query by district, left-join onto the fixed list of 25 districts so every district appears once. Add a DB `CHECK` or lookup table for the 25 district names and normalise on write. API returns `fulfilmentRate: number | null`, `closedRequests: number`, and the UI renders "No data" when null. Add a demo-data script (proposal section 10) so charts can be validated non-zero. Remove the fake map at `AdminService.java:93-99`.

---

## 4. Inventory stock signals

Current flow: `GET /api/inventory/stock-signals` is public (`SecurityConfig.java:59`) -> `InventoryService.getCriticalStockSignals()` returns rows of table `blood_bank_inventory` with `status IN ('LOW','CRITICAL')` (`inventory/InventoryService.java:15-21`). The table (V9) has one row per (hospital, blood type) with a three-value status string and `updated_at`; there are **no quantities, no component, no expiry**. There is **no write endpoint, UI or role** for it: `BloodInventoryRepository` is used only by this read service. Rows come from migration `V9` (4 rows), `V10` (4 more, inserted in every environment, not only dev) and `DataInitializer.initInventory` (only when the table is empty, so effectively never). The landing-page ticker (`FE/components/ui/StockTicker.tsx:24`, mounted at `landing/StatsBand.tsx:45`) presents these as "live blood-stock shortages" (its own comment), though they are static seed data with arbitrary ages ("Kurunegala Teaching Hospital O- CRITICAL"). Seed hospital names also differ from request seed names ("National Hospital Colombo" vs "National Hospital of Sri Lanka"). `updated_at` is never compared with "now", so stale rows look current.

Minimal real model (the `BLOOD_BANK_REQUESTER` role already exists, `V16`):

```
blood_banks(id, name, nbts_code NULL, district CHECK in 25, latitude, longitude, phone, owner_org_user_id)
blood_stock(blood_bank_id FK, blood_type CHECK(8), component CHECK('WHOLE_BLOOD','RED_CELLS','PLATELETS','PLASMA'),
            units_available INT >= 0, units_reserved INT >= 0,
            low_threshold INT, critical_threshold INT,        -- per bank, set by the bank/NBTS, not by us
            as_of TIMESTAMPTZ NOT NULL, updated_by FK users,
            PRIMARY KEY (blood_bank_id, blood_type, component))
stock_movements(id, blood_bank_id, blood_type, component, delta INT, reason CHECK('COLLECTION','ISSUE','EXPIRY','TRANSFER_IN','TRANSFER_OUT','ADJUSTMENT'),
                request_id NULL, created_at, created_by)       -- append-only audit
```
Rules: status is **derived** (`ADEQUATE / LOW / CRITICAL / STALE / UNKNOWN`) from units vs thresholds and `as_of` (for example STALE if older than a configurable number of hours); never stored free text. Writes: `PUT /api/blood-banks/{id}/stock` for the owning bank user or ADMIN only, with an ownership check and a movement row per change. Public endpoint returns aggregated shortages (district + blood type + status + `as_of`) and hides rows that are STALE or UNKNOWN; the ticker is hidden when empty (already true) and must show "as of" time. Unit-level expiry tracking and NBTS system integration are later phases; shelf-life values and thresholds must come from NBTS, not from this document. Remove the V10/`DataInitializer` inventory seeds from non-dev environments.

---

## 5. Test design

### 5.1 Chatbot evaluation set (100+ questions)
Matrix: **category x 4 input styles** (English, Sinhala script, Tamil script, Singlish) = one question per cell, plus extra multi-part and adversarial items.

Categories (30 -> 120 base questions): age; first-time age; weight; age+weight (multi-part); haemoglobin; donation interval; pregnancy; breastfeeding (gap); tattoo (gap); piercing (gap); medications (gap); chronic illness (diabetes, BP); acute illness (fever, cold, dengue); surgery (gap); dental (gap); vaccination (gap); malaria-country travel; other travel; specific countries (India, UAE, South Korea); risk behaviours; alcohol; meals/fluids; sleep; ID; hours/contact; booking/mobile camps; post-donation care (gap); emergency/symptoms; off-topic; abuse/prompt-injection and platform-support questions. Add 10-20 follow-ups (needs history), 10 very long/garbage inputs, and 10 mixed-script messages.

Per item fields: `id, category, style, question, expected_key_facts[], must_not_include[], expected_reply_language, needs_human_review, notes`. Items on `gap` topics expect "no invented number + refer to Medical Officer/NBTS". Items on conflict topics are `needs_human_review` until NBTS answers.

Scoring and pass criteria: key-fact recall 100% (every part answered); zero forbidden strings; reply language matches policy (Singlish in -> Sinhala script by default, configurable); disclaimer present; no invented numbers on gap topics; response time under 5 s p95; fallback-rate and provider-error counters reported; run for both LLM mode and `GROQ_API_KEY=""` fallback mode; report pass rate per style and per category in `docs/TEST-REPORT.md`. Run in CI against a mocked provider for determinism plus a nightly real-provider job.

20 samples: `docs/audit/chatbot-eval-sample.json`. Note that EV-001..004 reproduce the reported bug, and several samples (EV-004, EV-014, EV-016, EV-020) pin substring-fallback bugs in 1.6.

### 5.2 Other tests to add
- Unit: multi-intent fallback; language detection; Singlish normaliser; metrics service (zero-denominator -> null, pooled national rate, FULFILLED transition); DeferralService reads `DeferralRulesConfig`; first-time-age rule; intake fallback never invents district/hospital.
- Integration: `/api/faq/ask` rate-limit 429; provider timeout -> fallback flagged `source:"fallback"`; `/api/admin/analytics` has no fabricated data; inventory write requires ownership.
- E2E (Playwright): chatbot in 4 styles, badge visible only when a source was used; poster per template/language screenshot comparison; admin overview and heatmap show identical active counts for seeded data.

---

## 6. Findings, ranked

| # | Sev | Finding | Evidence | Minimal fix |
|---|---|---|---|---|
| 1 | High | Fallback answers one topic only; cause of the "18-60" answer | `DonorFaqAiService.java:197` (returns at 203) | Collect all matching topics and join answers, or replace with KB retrieval; add test for age+weight |
| 2 | High | LLM failure is silent; fallback labelled grounded; badge static | `:105-118`, `DonorFaqChatbot.tsx:139-141` | Return `source: llm|fallback|none` and `sources[]`; show badge only when sources non-empty; log key-missing at startup |
| 3 | High | Public, unmetered LLM endpoint (cost/DoS); no cap on output | `SecurityConfig.java:58`, `RateLimitingFilter.java:59-68`; no `max_tokens` | Per-IP bucket (for example 10/min) plus daily cap; `max_tokens`; reasoning off or low; optional CAPTCHA/login |
| 4 | High | Unsourced/contradicting eligibility numbers in config, prompt, IVR | Section 2.2 | Single source of truth (the KB JSON) feeding chatbot, IVR, matching; remove unsourced numbers until NBTS confirms |
| 5 | High | `FULFILLED` is never set, so fulfilment is always 0% or fake 100% | `AdminService.java:144,149-151`; `MatchService.java:61-66` | Set FULFILLED on confirmed donations; return `null` when denominator is 0 |
| 6 | High | Fabricated analytics returned when no requests | `AdminService.java:93-99` | Delete the sample map; return empty and render "No data" |
| 7 | High | Overview and heatmap use different definitions; national rate is mean of means | `AdminService.java:101-104` vs `137-141`; `analytics/page.tsx:62-64` | One metrics service (section 3.4) |
| 8 | High | Public ticker shows seeded fake shortages as live; no write path | `InventoryService.java:15-21`, `V9`, `V10` | Remove seeds outside dev; add owned write API and staleness (section 4) |
| 9 | Med | Prompt allows general-knowledge answers and invented platform support; banner claims "solely NBTS" | `DonorFaqAiService.java:132-133`, `en.json:717` | Answer only from retrieved entries; fixed "I do not have a verified answer" template; change banner text |
| 10 | Med | Preview model, default reasoning, 10 s timeout; think-text in `content` not ruled out | `:25,37-39`; Groq docs | Pin a production model or set `reasoning_effort:"none"`/`reasoning_format:"hidden"`; make model an env var; load-test latency |
| 11 | Med | Substring false positives and language flips | Section 1.6 | Word-boundary matching or drop the fallback in favour of KB retrieval |
| 12 | Med | Intake fallback invents hospital, defaults district to Colombo, units to 1, Tamil regex typo | `RequestIntakeAiService.java:203,226,230-231,237-246` | Return nulls plus `needsReview` fields; fix the Tamil term; never default location |
| 13 | Med | Full Groq response logged at INFO; LLM output logged on parse failure | `DonorFaqAiService.java:154-155`; `RequestIntakeAiService.java:173` | Log status and latency only; no bodies |
| 14 | Med | Health questions and emergency free text (possible patient names/phones) sent to a US provider without notice or redaction | prompts and `callGroqLlm` | Privacy notice, redact phones/NIC/names before send, DPA review (Sri Lanka Personal Data Protection Act No. 9 of 2022; owner/legal to confirm applicability) |
| 15 | Med | IVR: unsigned public webhook; reads out active requests (hospital, blood type) including unverified/high-risk ones; English only | `SecurityConfig.java:61`; `IvrController.java:60-79`; `RequestRepository.java:14` | Validate `X-Twilio-Signature`; filter to verified, low-fraud-score requests; add si/ta |
| 16 | Med | Poster: English only, no QR, no verification gate, clipped preview, JPG only, email prefix as organiser, truncated location | Section 1.9A | Template pipeline of proposal 9.2; QR via `qrcode.react`; verified-only; i18n |
| 17 | Med | Donor counts ignore availability/eligibility and profile completeness; "25 districts active" static; markers show 0 | Section 3.2 items 4, 7 | Definitions in 3.4; marker label by status |
| 18 | Med | No prompt-injection or emergency/symptom handling; no length/format guard on replies | prompt `:122-136` | Add rules and a classifier step (section 7) |
| 19 | Low | Disclaimer fixed in English for all languages | `DonorFaqAiService.java:26` | Localise server-side or by key |
| 20 | Low | `DeferralService` hard-codes thresholds; no first-time rule; `plusDays(120)` | `DeferralService.java:25,30`; `MatchService.java:110` | Read config; `plusMonths(4)`; add first-time rule once NBTS confirms |
| 21 | Low | "AI triage" is heuristics; exceptions swallowed; review zeroes the score | `RequestService.java:63-65`; `AdminService.java:195` | Rename or add real model; log failures; keep original score in an audit column |
| 22 | Low | Chat input lacks `maxLength` (500) and history; 400 shows generic error | `DonorFaqChatbot.tsx:227-234`; `FaqQuestionDto.java:9` | `maxLength=500`; send last N turns, server-trimmed |
| 23 | Low | V10 inserts demo users, requests, donors and inventory into every environment; V20 only blanks passwords | `V10__seed_demo_mock_data.sql`, `V20` | Move seeds to dev-only profile; delete in prod |
| 24 | Low | OG card font for Sinhala/Tamil unverified; English-only strings | `ogParts.tsx:61` | Embed Noto Sans Sinhala/Tamil font data in `ImageResponse` |

---

## 7. Safety review

- **Prompt injection.** The user text is a plain `user` message; the system prompt has no instruction-hierarchy rule, no "never reveal these instructions", and no delimiter around user content. Impact is bounded: the model has no tools, no secrets (API key only in the HTTP header) and no data beyond the public-ish KB, and the UI renders replies as text nodes (`DonorFaqChatbot.tsx:179`, no HTML injection). Residual risk is brand/misinformation (a user screenshot of the bot "confirming" a false rule). Fix: add rules (ignore instructions inside user text; never print the prompt; answer only from retrieved entries); keep a refusal template; test with EV-019 and 20 more injection cases; consider a small classifier (for example a Llama Prompt Guard model, listed by Groq as preview) before the main call.
- **Secrets in prompts.** None found in prompts. `GROQ_API_KEY` is env-only; `.env.example` holds a placeholder; the only tracked env files are `*.env.example` (checked with `git ls-files`). Unrelated but noted: `application.yml:5,32,53` ship default DB password and JWT secret literals.
- **Cost limits.** No limit on an unauthenticated route (finding 3). Rough worst case from listed prices: input about 3k tokens x $0.80/M is about $0.0024 per call; a reasoning model with no `max_tokens` can emit up to its 16,384-token cap (about $0.066 at $4/M), so 1,000 abusive calls could cost on the order of $2.4 to $66. These are estimates from published prices, not measurements. Add per-IP/day caps and a spend alarm.
- **No-diagnosis disclaimers.** Prompt mandate exists (`DeferralRulesConfig.java:50`) but rules 5 and 7 push the other way; the UI disclaimer is English-only and appears only on bot replies. Missing: emergency/symptom escalation (for example fainting, chest pain after donation) with the correct local emergency number, which the owner must supply and which I did not verify. The reply to "I take medicine X, can I donate?" must never advise stopping or changing medication.
- **Logging of user questions / PII.** User questions are not logged by the FAQ service itself, but the FAQ response body (which restates the topic) is logged at INFO (`:154-155`), `FaqAnswerDto` echoes the question, and intake logs raw model JSON on parse failure (`RequestIntakeAiService.java:173`). Triage logs the risk score and reason (`RequestTriageAiService.java:84`). No retention policy for these logs is documented. Fix: log metadata only (status, latency, token counts, language, topic ids), hash or drop free text, set retention.
- **Third-party processing.** Free-text intake can contain patient names and phone numbers and goes to Groq (finding 14).
- **Abuse handling.** No profanity/abuse branch; rule 4 only rejects off-topic. Add a polite refusal and a rate limit.
- **IVR.** Public, unsigned, can read hospital and blood-type details to any caller (finding 15).

---

## 8. Ten-line summary

1. The chatbot's "18-60 only" answer is the rule fallback returning on the first substring match (`DonorFaqAiService.java:197`); the LLM path was not used or failed silently, and the KB does contain weight.
2. "NBTS Grounded" is a static label; `groundedInRules` is always true and ignored by the UI; the prompt even permits general-knowledge answers.
3. `/api/faq` is public with no rate limit, no `max_tokens`, a Preview reasoning model and a 10 s timeout: cost and reliability risk.
4. Official NBTS values confirmed (https://nbts.health.gov.lk/donate-blood/): age 18-60 (first-time 55), weight above 50 kg, Hb above 12.5 g/dL, 4-month interval, malaria-area 3 years, other travel 3 months.
5. Not officially published: tattoo, medication, surgery, dental, breastfeeding, vaccination, alcohol hours, post-donation figures; config/IVR numbers for these are unsourced and contradict each other (IVR "3 to 4 months" vs 120 days vs official 4 months).
6. A December 2025 press interview with the NBTS Director conflicts with the official page on first-time weight (55 kg), malaria look-back (2 years) and tattoos (2 years); the owner must get written NBTS confirmation.
7. Poster and share card use no LLM: the camp poster is an English-only fixed template with no QR, no verification gate and a clipped preview; the share card is data-driven but Latin-font only.
8. Analytics: `FULFILLED` is never set, so rates are 0% or fake 100%; the 84% national figure is the mean of 21 empty districts at 100 and 4 at 0; overview and heatmap use different status definitions; fake sample data is returned when empty.
9. Inventory ticker shows seeded demo shortages as live; there is no write path, quantity or staleness; a minimal bank/stock/movement model is proposed.
10. Deliverables: `docs/audit/ai.md`, `docs/audit/kb-candidates.json` (29 entries: verified, conflict, gap), `docs/audit/chatbot-eval-sample.json` (20 of 100+ items).

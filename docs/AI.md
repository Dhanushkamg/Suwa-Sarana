# AI features: chatbot, request intake, triage, poster copy

Scope: decisions D6/D7/D8 in `PLAN.md`, findings in `audit/ai.md`. Code: `apps/backend/src/main/java/com/suwasarana/api/ai/**`. Resources: `src/main/resources/kb/**`, `src/main/resources/ai/**`. Migration: `V50__ai_triage_records.sql`.

## 1. What changed and why

| Before (audit) | Now |
|---|---|
| Whole `faq-knowledge.txt` pasted into a prompt; rule fallback with first-match-wins substring checks (`"age"` in `dosage`, `"old"` in `cold`, `"drink"` for water) | Curated JSON knowledge base, BM25 retrieval on whole words, multi-intent, confidence threshold, deterministic answer text |
| Prompt allowed "general knowledge" and invented platform support; `groundedInRules` always true | The model never invents: it may only rephrase VERIFIED entries, output is validated; `grounded` is true only when a KB entry was used |
| Preview model `qwen/qwen3.8-27b`, no `max_tokens`, no retry, key-missing silent | Production model `llama-3.3-70b-versatile` (configurable), `max_tokens`, timeouts, 1 retry, circuit breaker, daily token budget, key-missing warning at start-up |
| Intake defaulted unknown district to Colombo, invented a hospital, defaulted 1 unit | Nulls plus per-field `needsConfirmation`; official 25 districts only; approximate coordinates flagged |
| Review zeroed the AI score | `ai_triage_records` keeps the AI score and ordered reasons; review stored beside it |
| Poster: no copy generation | `POST /api/ai/poster-copy`, facts substituted not recalled, validated |

## 2. Chatbot architecture

```
POST /api/faq/ask
  sanitise (cap 500 chars, strip control chars) -> detect input style (en | si | ta | singlish | tanglish)
  -> emergency symptoms?  yes: short safety reply (kind EMERGENCY), no retrieval, no LLM
  -> retrieval (injection text is stripped first and the threshold is x1.5; with a hit the answer is KB-only, without one: refusal)
  -> hits?  no: greeting / thanks / abuse / account-support redirect / "no confirmed answer" / off-topic
  -> compose: curated answer of each entry in the reply language (+ companions, + "confirm with NBTS" footer)
  -> optional LLM rephrase, only if every used entry is VERIFIED; validated (numbers, links, script); else the composed text
```

Packages: `ai/kb` (KnowledgeBase, EligibilityFacts, KbMessages), `ai/retrieval` (TextUtil, Lexicon, TextNormalizer, Bm25Index, RetrievalEngine, LanguageDetector, SafetyClassifier), `ai/reply` (AnswerComposer, SinhalaRomaniser), `ai/llm` (LlmClient, GroqLlmClient, CircuitBreaker, TokenBudget, OutputGuard), `ai/geo` (GeoData), `ai/poster`.

### Retrieval

- **Tokenisation**: Unicode runs per script; zero-width joiners removed (Sinhala conjuncts are typed both ways). Matching is by whole token or knowledge-base stem prefix (Sinhala/Tamil suffixes such as `වයසට`, `எடையும்`), never by substring in the middle of a word.
- **Singlish / Tanglish**: `kb/translit-lexicon.json` maps 912 romanised forms (100 groups) to canonical tokens (donate, age, weight, tattoo, diabetes, malaria, ...); lookup is exact, then on a phonetic skeleton (`th/dh->t/d`, `w->v`, doubled letters collapsed) so `puluwanda`/`puluvanda`, `dhenna`/`denna` meet. Phrase forms (`kiri denawa`, `rata giya`) are supported. Romanised marker words decide Singlish (reply in Sinhala) vs Tanglish (reply in Tamil).
- **Scoring**: BM25 over unigrams + bigrams. Keyword phrases weigh 3, example questions and topic 1. Domain words (`blood`, `donate`, `after`, `before`, Sinhala/Tamil equivalents) do not score alone but form bigrams (`after donating`). Unit words (`month`, `year` ...) can never be the only reason for a hit.
- **Multi-intent**: sentence split, then greedy residual covering: take the best entry, remove the query terms it explains, retrieve again with the rest (max 3 per sentence, 4 per question). "minimum age and weight", "age saha weight" and the Tamil "வயதும் எடையும்" (no conjunction word at all) return both entries.
- **Confidence**: a hit needs score >= `min-score` (1.4; 1.9 for extra intents) and a matching rare term. Without any blood/donation word, at least 30 % of the question's content words must be known to the KB (`Who won the cricket match yesterday?` finds nothing and gets the off-topic reply). Below threshold the bot says it has no confirmed answer.
- **History**: the last <= 6 turns, each capped at 500 chars; only the previous *user* turn is used, and only when the question alone finds nothing ("What about piercings?").

### Reply language

`language` = `en|si|ta` forces it. `auto` (default): English -> en, Sinhala -> si, Tamil -> ta, **Singlish -> Sinhala script**, Tanglish -> Tamil script. `replyStyle=romanised` (or `app.ai.faq.singlish-reply=romanised`) answers a Singlish question in rule-based romanised Sinhala (`rudhira daanaya`); Tamil is never romanised. The deprecated `locale` is used only when the language of the message cannot be detected (digits only).

### Safety

- **Emergency / symptoms** (chest pain, heavy bleeding, fainting, breathing trouble ... in en/si/ta/romanised; dizziness etc. only when the user speaks about themselves): fixed short reply with the configured ambulance number (default `1990`, Suwa Seriya). **The owner must confirm this number**; if `app.ai.faq.emergency-number` is blank the reply says "your local emergency services".
- **Prompt injection**: pattern-detected (en/si/ta/romanised, tag injection). An injected question is never sent to the LLM; the answer, if any, comes from the KB only; otherwise a refusal. The user text goes to the LLM only inside `<user_question>` after `<`/`>` are removed, the system prompt calls it untrusted data, and the output is validated.
- **No diagnosis / no medicine advice**: GAP answers tell the user to inform screening staff; the medication entry says not to stop or change medicine because of the chat. Every reply carries a localised disclaimer that the Medical Officer decides.
- **Off-topic / abuse / account-support**: polite redirect, no LLM.
- **Logging**: length, language/style, entry ids, kind, LLM yes/no, latency. Never the question, the answer or the model body.

### LLM polish and cost controls

Only VERIFIED answers are rephrased (never GAP, CONFLICT, safety or injection). `OutputGuard` accepts the rewrite only if: no `<`/`>`, every number and link of the source is present and nothing new is added, length <= max(700, 2x source), and the script matches the reply language. Anything else falls back to the composed text, so the user never sees an error page. `GroqLlmClient`: connect 3 s / read 8 s, `max_tokens` (default 400, polish uses 320), temperature 0.2, `<think>` blocks stripped, 1 retry with backoff on I/O, 429 or 5xx (4xx not retried), circuit breaker after 5 consecutive failures for 60 s (one half-open probe), daily token budget per instance (300 000 tokens, resets at midnight Asia/Colombo; the estimate is reserved before the call and replaced by the real usage). Rate limiting per IP is in the servlet filter (engineer D).

## 3. Knowledge base

File `kb/nbts-eligibility.v1.json`, schema version 1.

```jsonc
{
  "schemaVersion": 1, "kbVersion": "nbts-eligibility.v1", "lastUpdated": "2026-10-28",
  "reviewStatus": "DRAFT ...",
  "facts": { "minAge": 18, "maxAge": 60, "firstTimeMaxAge": 55, "minWeightKgAbove": 50, "haemoglobinAboveGdl": "12.5",
             "donationIntervalMonths": 4, "malariaDeferralYears": 3, "otherTravelDeferralMonths": 3, "mealWithinHours": 4,
             "sleepHours": 6, "...PressReport": "unresolved conflicting values, display only", "urls and hours": "..." },
  "entries": [{
    "id": "ELIG-AGE", "topic": "...", "status": "VERIFIED | CONFLICT | GAP", "category": "age",
    "keywords": { "en": [], "si": [], "ta": [], "singlish": [] },          // retrieval vocabulary
    "question_examples": { "en": [], "si": [], "ta": [], "singlish": [] }, // vocabulary + follow-up suggestions
    "answer_en": "...{{minAge}}...", "answer_si": "...", "answer_ta": "...", // {{placeholders}} from facts + config contacts
    "source": { "title": "...", "url": "..." }, "secondarySource": { "title": "...", "url": "..." },
    "lastReviewed": "2026-10-27", "confidence": "high|medium|low|none",
    "needsHumanReview": true, "reviewReason": "...",
    "needsHumanReview_si": true, "needsHumanReview_ta": true,               // every drafted translation
    "companions": ["CONFLICT-FIRST-TIME-WEIGHT"],                          // always appended when this entry is used
    "related": ["ELIG-HAEMOGLOBIN"]                                        // follow-up suggestions
  }]
}
```

Statuses: 19 VERIFIED, 2 CONFLICT, 11 GAP (32 entries). **GAP** answers say there is no confirmed official figure and point to the Medical Officer / NBTS contact (taken from configuration, never hard-coded). **CONFLICT** answers state the official-website value and that it must be confirmed with NBTS (first-time donor weight 50 vs 55 kg; malaria look-back 3 vs 2 years). Tattoos are GAP because the official page gives no value at all (the unofficial figures conflict, so none is shown). `ELIG-WEIGHT` and the three malaria/country entries carry the conflict entry as a companion, so a conflicting number is never presented as settled. The candidate `ELIG-AGE-WEIGHT-COMBINED` was dropped: multi-intent retrieval answers both parts from the two single entries. The invented "Raktha" app and the old unsourced numbers (tattoo 12 months, surgery 6 months, dental 7 days, alcohol 24 h, post-donation figures) are not carried over (a test asserts it). The old `faq-knowledge.txt` was deleted.

### Update procedure

1. Get the NBTS written Donor Selection Guideline (or a Medical Officer's sign-off) and re-open the source URLs; the 2026-10-27 audit read them through a summarising tool.
2. Edit the entry in the JSON (change `status` GAP -> VERIFIED only with a source URL), put numbers in `facts` and refer to them with `{{...}}` so there is one place per number, set `lastReviewed` to the review date, clear `needsHumanReview` only after the owner/NBTS agrees.
3. A native speaker reviews `answer_si` / `answer_ta`; then set `needsHumanReview_si/ta` to false and remove the lines from `docs/i18n-review.md`.
4. Add 2+ example questions per style to `src/test/resources/chatbot-eval.json`; run `./mvnw test -Dtest=ChatbotEvalTest` and read `target/chatbot-eval-report.md`.
5. Bump `kbVersion` (new file `nbts-eligibility.v2.json`, change `app.ai.kb.path`). Reviewer of record: the owner, with the NBTS Medical Officer for clinical entries.

### EligibilityFacts (single source of truth for thresholds)

`com.suwasarana.api.ai.kb.EligibilityFacts` is a bean built from the `facts` block: `getMinAge()`, `getMaxAge()`, `getFirstTimeMaxAge()`, `getWeightKgAbove()` (strictly above), `getHaemoglobinGdlAbove()`, `getDonationIntervalMonths()`, `isAgeEligible(age, firstTime)`, `isWeightEligible(kg)`, `isHaemoglobinEligible(gdl)`, `nextEligibleDonationDate(last)` (calendar months, not 120 days). Engineer C (`DeferralRulesConfig` / `DeferralService`, which today accept exactly 50 kg and use `plusDays(120)`) and engineer D (`IvrFacts`, which says "3 to 4 months") can inject it. Tattoo, surgery, medicine and alcohol-hour figures are deliberately not exposed. The two unresolved conflict values are exposed as `*PressReport` for display only.

## 4. API contracts

All responses use the existing envelope `{success, message, data, timestamp}`.

### `POST /api/faq/ask` (public)

```jsonc
// request
{ "question": "string (<= 500)", "history": [{"role": "user|assistant", "content": "..."}],   // optional, server uses the last 6
  "language": "en|si|ta|auto", "replyStyle": "auto|script|romanised",                          // optional
  "locale": "en|si|ta" }                                                                       // DEPRECATED hint, still accepted
// response data
{ "answer": "...", "language": "en|si|ta", "locale": "= language (deprecated)",
  "inputStyle": "en|si|ta|singlish|tanglish|unknown", "replyStyle": "script|romanised",
  "kind": "ANSWER|NO_ANSWER|EMERGENCY|OFF_TOPIC|REFUSAL|GREETING|SMALL_TALK|PLATFORM|EMPTY",
  "grounded": true,            // true only when >= 1 KB entry was used
  "groundedInRules": true,     // DEPRECATED, equals grounded
  "sources": [{ "id": "ELIG-AGE", "title": "...", "url": "https://...", "lastReviewed": "2026-10-27", "status": "VERIFIED|CONFLICT|GAP|SECONDARY" }],
  "needsConfirmation": false,  // true if any used entry is CONFLICT/GAP or flagged for human review
  "disclaimer": "...", "usedLlm": false, "followUps": ["..."],
  "translationDraft": false, "translationNote": null }   // true + localised note when si/ta text is an unreviewed draft
```
Validation: blank question, > 500 chars, bad `language`/`replyStyle`/`role`, > 20 history messages -> 400. UI guidance: show the green "grounded" badge only when `grounded && !needsConfirmation`; show an amber "confirm with NBTS" state when `needsConfirmation`; `kind` other than ANSWER has no sources; show `sources` behind a "show source" link; show `translationNote` for si/ta.

### `POST /api/ai/poster-copy` (authenticated)

```jsonc
{ "template": "URGENT_REQUEST|CAMP|AWARENESS|THANK_YOU", "language": "en|si|ta",
  "facts": { "bloodType": "O+", "units": "3", "hospital": "...", "district": "Galle", "date": "2026-11-02", "time": "09:30",
             "venue": "...", "contactPhone": "0771234567", "campName": "...", "organiser": "..." } }
-> data: { "headline": "...", "body": "... (detail lines for date/time/contact appended)", "hashtags": ["#..."], "caption": "...", "usedLlm": false }
```
Required facts: URGENT_REQUEST bloodType, units, hospital, district, contactPhone; CAMP campName, date, venue, district; the others none. Errors: `422 poster.unverified_requester` (URGENT_REQUEST by a requester whose `verificationStatus` is not VERIFIED; admins pass), `422 poster.patient_name_not_allowed` (any patient/personal field such as `patientName`, `nic`, `photo`), `400 poster.unknown_fact`, `400 poster.invalid_fact` (bad blood type, district not in the official 25, bad phone, a phone-like number hidden in free text), `400 poster.missing_fact`. Error body: `{success:false, message, code, data:{code}}`. Guard note: there is no institution-role guard today (any authenticated user may generate AWARENESS/CAMP/THANK_YOU copy); add `hasAnyRole` in `PosterCopyController` if camps are restricted to institutions.
The model sees no facts at all, only placeholder names, and is told to write around them. Validation of the model's copy: placeholders must be known and required ones present in headline+body; no digits, links, phone numbers, district or hospital names outside placeholders; no scare words (die, death, tragic, desperate ... in en/si/ta); right script; headline <= 80, body <= 300, caption <= 320 characters. Any failure or an unavailable model returns the template copy from `ai/poster-copy_{en,si,ta}.properties` (Sinhala/Tamil marked `# REVIEW`). Hashtags are always from the templates.

### `POST /api/requests/ai-draft` (any requester role; shape kept, new fields)

Response `data` keeps `patientBloodType, unitsNeeded, urgency, hospitalName, district, latitude, longitude, extractedSuccessfully, message` and adds:
`needsConfirmation` (object field -> boolean for `patientBloodType, unitsNeeded, urgency, hospitalName, district, coordinates`), `approximateCoordinates` (true whenever coordinates exist), `coordinateSource` (`DISTRICT_CENTROID` or null), `missingFields` (list), `source` (`llm|rules`), `warnings`. **Every field can be null** (unknown district is null, never Colombo; no invented hospital; no default 1 unit; no guessed urgency). `urgency: CRITICAL` is always flagged, and a model-only CRITICAL is downgraded to URGENT. The frontend must show the flagged fields as "please check" and let the user pick a district/point on the map. Input is capped at 1000 characters, phone numbers and NICs are removed before the text goes to the model, and the text is sent only inside `<request_text>` as data.

### Triage transparency (service API for engineer B / frontend)

`RequestTriageAiService.evaluate(request)` returns `TriageResult{score, rawScore, reasons[{code, weight, detail, params}] ordered heaviest first, engineVersion "rules-v2", evaluatedAt, humanReviewRecommended (score >= 40)}`. `triageRequest` stores it in `ai_triage_records` (append-only) and still fills the legacy `fraud_risk_score / ai_flag_reason / triaged_at` columns. `latestResult(requestId)` re-reads the reasons; `recordReview(requestId, reviewerUserId, decision, note)` stores who/when/decision next to the untouched AI score. Codes: `REQUESTER_HIGH_FREQUENCY` 45, `REQUESTER_REPEAT_24H` 20, `UNVERIFIED_HIGH_UNITS` 35, `UNVERIFIED_MODERATE_UNITS` 15, `SUSPICIOUS_HOSPITAL_NAME` 30, `UNVERIFIED_CRITICAL` 25. It is rule-based, not machine learning, and never changes a request's status. Integration needed (not done here, files belong to others): B replaces `request.setFraudRiskScore(0)` in `AdminService.reviewTriageRequest` by `triageService.recordReview(...)` and adds `reasons` to the triage DTO via `latestResult`; C logs (instead of swallowing) a triage exception in `RequestService.createRequest`.

## 5. Configuration

All keys are optional (`@ConfigurationProperties("app.ai")`, class `AiProperties`); nothing was added to `application.yml`.

| Property | Env var (relaxed binding) | Default |
|---|---|---|
| `app.ai.groq.api-key` (also `groq.api.key`) | `GROQ_API_KEY` (or `APP_AI_GROQ_API_KEY`) | empty = deterministic mode |
| `app.ai.groq.enabled` | `APP_AI_GROQ_ENABLED` | true |
| `app.ai.groq.model` | `APP_AI_GROQ_MODEL` | `llama-3.3-70b-versatile` |
| `app.ai.groq.intake-model` | `APP_AI_GROQ_INTAKE_MODEL` | same as model |
| `app.ai.groq.url` | `APP_AI_GROQ_URL` | `https://api.groq.com/openai/v1/chat/completions` |
| `app.ai.groq.max-tokens` / `temperature` | `APP_AI_GROQ_MAX_TOKENS` / `..._TEMPERATURE` | 400 / 0.2 |
| `app.ai.groq.connect-timeout-ms` / `read-timeout-ms` | `APP_AI_GROQ_CONNECT_TIMEOUT_MS` / `..._READ_TIMEOUT_MS` | 3000 / 8000 |
| `app.ai.groq.retries` / `retry-backoff-ms` | `APP_AI_GROQ_RETRIES` / `..._RETRY_BACKOFF_MS` | 1 / 300 |
| `app.ai.groq.breaker-failure-threshold` / `breaker-open-seconds` | `APP_AI_GROQ_BREAKER_FAILURE_THRESHOLD` / `..._BREAKER_OPEN_SECONDS` | 5 / 60 |
| `app.ai.groq.daily-token-budget` | `APP_AI_GROQ_DAILY_TOKEN_BUDGET` | 300000 (0 = unlimited) |
| `app.ai.groq.reasoning-effort` | `APP_AI_GROQ_REASONING_EFFORT` | empty (only sent for reasoning models) |
| `app.ai.faq.singlish-reply` | `APP_AI_FAQ_SINGLISH_REPLY` | `script` (`romanised`, `english`) |
| `app.ai.faq.llm-polish` | `APP_AI_FAQ_LLM_POLISH` | true |
| `app.ai.faq.emergency-number` | `APP_AI_FAQ_EMERGENCY_NUMBER` | `1990` (owner to confirm) |
| `app.ai.faq.min-score` / `min-score-extra` | `APP_AI_FAQ_MIN_SCORE` / `..._MIN_SCORE_EXTRA` | 1.4 / 1.9 |
| `app.ai.contacts.nbts-phone` / `nbts-email` / `nbts-address` / `nbts-website` | `APP_AI_CONTACTS_NBTS_PHONE` ... | NBTS contact page values |
| `app.ai.kb.path` | `APP_AI_KB_PATH` | `kb/nbts-eligibility.v1.json` |

`docker-compose.yml` already passes `GROQ_API_KEY`. No new Maven dependency was needed (JDK `HttpClient`, Jackson 2 already on the classpath, `spring-boot-starter-validation` already present).

## 6. Evaluation (proposal 9.1 / 10)

`ChatbotEvalTest` runs `src/test/resources/chatbot-eval.json` (200 questions) through the deterministic pipeline, prints the report and writes `apps/backend/target/chatbot-eval-report.md`. Items: English 58+, Sinhala 36, Tamil 36, Singlish 38 retrieval items, plus multi-intent (16), mixed-script, follow-ups with history, emergency (6), prompt injection (8), off-topic/abuse/platform/greeting/no-answer (20), garbage and over-long input. Fields per item: `expected_entry_ids`, `expected_key_facts`, `must_not_include`, `expected_reply_language`, `expected_kind`, `needs_human_review`.

Assertions: retrieval hit rate >= 90 % overall and >= 85 % per style; 100 % of multi-intent items answer every part; 100 % of GAP topics contain no number besides the NBTS contact; 100 % of injection and emergency items pass (no leak of prompt, KB ids, API key or injected facts); `grounded` equals "has sources".

| Input style | Retrieval hits | Items | Rate |
|---|---|---|---|
| English | 58 | 58 | 100.0 % |
| Sinhala script | 36 | 36 | 100.0 % |
| Tamil script | 36 | 36 | 100.0 % |
| Singlish | 38 | 38 | 100.0 % |
| **overall** | 168 | 168 | **100.0 %** (full pass incl. kind, facts, language: 200 / 200) |

**Read these numbers with care.** The set was written by the same engineer who built the engine and the vocabulary was tuned until it passed (two rounds: 173/200 -> 200/200), so 100 % is an upper bound, not a forecast. To estimate generalisation a second file `chatbot-eval-heldout.json` (51 questions) was written afterwards and run once before any fix: 43/45 retrieval hits (95.6 %), 45/51 full pass (88 %); after fixing the two real vocabulary gaps (colloquial "how many times a year", spurious extra intents) it reads 44/45 and 46/51. The residual failures are real: a question with only a number and a relative ("my father is 61, is he still allowed to donate?") finds no topic, and some questions pull in one extra, harmless entry (for a GAP topic that also trips the "no number" check). Sinhala and Tamil wording is AI-drafted and must be replaced or confirmed by native speakers before the set is used as a gold standard; real user questions from the pilot should be added. The assertion for the held-out set is >= 85 %.

Regression tests (`FaqRegressionTest`) pin the audit bugs: "minimum age and weight" answers both (18-60 and above 50 kg, plus the unresolved 55 kg note); `dosage`/`package`/`cold` do not trigger the age rule; "can I drink water" does not trigger alcohol; "pressure" and "passed" do not flip the language; tattoo gives no number. Other test classes: `KnowledgeBaseTest` (schema, no GAP numbers, lexicon >= 300 forms with no conflicts, language detection, `EligibilityFacts` boundaries), `LlmTest` (accepted/rejected rewrites, delimiter, retry, breaker, budget, never-called cases), `RequestIntakeAiServiceTest`, `RequestTriageAiServiceTest`, `PosterCopyServiceTest`, `AiControllersIntegrationTest` (HTTP, 401/422/400).
Not measured here: p95 latency with the real provider, a real-provider (LLM mode) run of the evaluation set, and Playwright tests of the widget.

## 7. Known limits

- Retrieval is lexical: a question with no topic word ("is that okay?", only a number) finds nothing; the bot then says it has no confirmed answer. No numbers from the question are evaluated ("I weigh 49 kg" gets the rule, not a verdict).
- Sinhala/Tamil handling relies on stem prefixes from the KB keyword lists; a spelling or dialect form not in the lists is missed. Tamil stems ending in a virama get an automatic alias, other morphology (sandhi) does not.
- Singlish vs Tanglish detection depends on 131 marker words; a short romanised message with none is treated as English (retrieval still uses the lexicon, but the reply is English).
- The token budget and circuit breaker are per application instance (in memory), not shared across instances.
- Safety word lists (emergency, abuse, injection) are seed lists; false negatives are possible. The chatbot can never replace the Medical Officer, and the emergency reply is generic.
- `usedLlm` answers were tested with a scripted fake model only; no live Groq call was made (no key in this environment). Check latency and output quality with the real key before enabling `llm-polish` in production.
- The geo table has the 25 district centroids and 44 well-known hospitals mapped to a district; hospital coordinates are not stored (not verified).
- Triage is a rule engine and says so; the old name "AI triage" remains in class names.

## 8. What the owner must provide or confirm

1. **NBTS written donor-selection guideline** (or a Medical Officer's sign-off) for every GAP entry: tattoos/piercings, medicines and antibiotics, chronic conditions, acute illness, surgery/dental, breastfeeding/after delivery, vaccination, smoking, post-donation care, booking/other blood-bank hours, mobile-camp rules (the Sinhala PDF manual was not read).
2. **Rulings on the two conflicts**: first-time donor weight (50 vs 55 kg) and malaria look-back (3 vs 2 years). Also confirm the 2024 malaria country list is current (India, South Korea; UAE inferred as not listed) and who maintains it.
3. **Wording for "risk behaviours"** (sensitive; the bot does not define it).
4. **Native Sinhala and Tamil reviewers** for `answer_si/ta`, `kb/messages.json`, the poster properties and the safety word lists (see `i18n-review.md`).
5. **Emergency number** (`app.ai.faq.emergency-number`, default `1990` unverified) and whether NBTS contact details in config are still correct.
6. **Groq API key and budget**: `GROQ_API_KEY`, a daily token budget (`app.ai.groq.daily-token-budget`, per instance), a provider spend alarm, and a decision on sending free-text health/emergency descriptions to a US provider (privacy notice; legal review under the Personal Data Protection Act No. 9 of 2022). Phone numbers and NICs are removed before intake text is sent, names are not.
7. Whether camps/requests are limited to institution roles (to add a role guard to poster copy), and confirmation that the platform may display the "NBTS-sourced" badge only when `grounded && !needsConfirmation` (decision D6).

## 9. Integration notes for other engineers

- **C**: adopt `EligibilityFacts` in `DeferralRulesConfig`/`DeferralService` (strictly above 50 kg, `plusMonths(4)`, first-time age 55, remove the unsourced tattoo/surgery/dental/antibiotics/alcohol/pregnancy constants; the old prompt context `generateSystemPromptContext()` is no longer used by the chatbot); stop swallowing triage exceptions in `RequestService.createRequest`.
- **B**: use `recordReview` and `latestResult` in `AdminService` (section 4); the triage queue should show `reasons[].code`.
- **D**: `IvrFacts` should read `EligibilityFacts` ("3 to 4 months" is wrong, official is 4 months); rate limits for `/api/faq/**`, `/api/requests/ai-draft` and now `/api/ai/poster-copy` (also costs tokens).
- **A**: nothing required; optionally document the `APP_AI_*` variables in `.env.example`/`DEPLOYMENT.md`.

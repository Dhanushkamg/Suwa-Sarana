
## FE2 (auth flow, public pages, maps, posters) - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `pub.level.CRITICAL/WARNING/BALANCED/SURPLUS` | Critical / Low / Balanced / Surplus | si: අතිශය හිඟය / අඩුයි / සමතුලිතයි / අතිරික්තයි; ta: கடும் பற்றாக்குறை / குறைவு / சமநிலை / மிகுதி | Short map-marker labels; the glossary has no "shortage level" terms |
| `pub.levelDesc.*` | "Fewer than {critical} available donors for each active request" ... | ratio sentences | Ratio wording may read awkwardly; thresholds come from the same constants as the backend rule |
| `pub.coordsOutOfRange` | Enter coordinates inside Sri Lanka | si: ඛණ්ඩාංක; ta: ஆயத்தொலைவுகள் | "coordinates" term |
| `pub.posterHeadline` / `pub.posterHeadlineAccent` | One donation, / many heartbeats. | si: එක් රුධිර දානයක්, / බොහෝ හද ගැස්ම.; ta: ஒரு இரத்த தானம், / பல இதயத் துடிப்புகள். | Marketing tagline translated freely |
| `pub.campStatus.*` | Scheduled / Completed / Cancelled | si: සැලසුම් කළ / අවසන් / අවලංගු කළ | Glossary term for "donation camp" (කඳවුර) still marked unsure |
| `authFlow.errors.ACCOUNT_LOCKED`, `OTP_LOCKED` | temporarily locked / too many wrong codes | lock-out wording | Tone for security messages |
| `authFlow.consent` | I accept the {terms} and the {privacy}. | si: මම {terms} සහ {privacy} පිළිගනිමි.; ta: நான் {terms} மற்றும் {privacy} ஆகியவற்றை ஏற்றுக்கொள்கிறேன். | Link text comes from `auth.termsLink` / `auth.privacyLink` ("පෞද්ගලිකත්ව ප්‍රතිපත්තිය" there vs "රහස්‍යතා ප්‍රතිපත්තිය" in the glossary) |
| `pub.timeLeft*` | 2 days 3 hours left | Sinhala uses `other`-only plurals (no singular/plural split) | Check natural phrasing |

## FE3 (dashboard: donor, requester, hospital, blood bank, admin) - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `dashUi.donor.reliability*`, `dashUi.donor.tier.*` | Reliability score, "Excellent standing (80 or more)", "Building trust" | si: විශ්වසනීයත්ව ලකුණු, විශිෂ්ට තත්ත්වය, විශ්වාසය ගොඩනගමින්; ta: நம்பகத்தன்மை மதிப்பெண், சிறந்த நிலை, நம்பிக்கையை வளர்க்கும் நிலை | Tier names are new; the score rules (+10 per donation, -20 per no-show, start 50) are quoted from the backend |
| `dashUi.donor.availableToDonate` | Available to donate | si: රුධිර දානයට සූදානම්; ta: இரத்தம் வழங்கத் தயார் | Glossary says "availability" = ලබා දිය හැකි බව; this is a switch label so a short verb phrase was used |
| `dashUi.donor.livesHelped*` | Lives helped (estimate) / one donation can help up to three patients | si: උදව් කළ ජීවිත (ඇස්තමේන්තුව); ta: உதவிய உயிர்கள் (மதிப்பீடு) | "estimate" wording; the "three patients" figure is a commonly quoted rule of thumb, confirm with NBTS |
| `dashUi.shortage.*` | Critical shortage / Warning / Balanced / Surplus | si: බරපතළ හිඟය / අවවාදය / සමතුලිතයි / අතිරික්තය; ta: கடும் பற்றாக்குறை / எச்சரிக்கை / சமநிலை / உபரி | Differs slightly from FE2's short map labels (`pub.level.*`): align the two sets |
| `dashUi.admin.dismissNote` | Flag dismissed by an administrator from the triage queue. | si: ස්වයංක්‍රීය පෙරහන් පෝලිම; ta: தானியங்கி முன்னுரிமை வரிசை | "triage" has no glossary entry; stored as the note on the request |
| `dashUi.inv.component.*` | Whole blood / Red cells / Platelets / Plasma | si: සම්පූර්ණ රුධිරය / රතු රුධිර සෛල / පට්ටිකා / ප්ලාස්මා; ta: முழு இரத்தம் / சிவப்பணுக்கள் / இரத்தத் தட்டுக்கள் / பிளாஸ்மா | Clinical terms: use the NBTS spelling |
| `dashUi.inv.*` (stock, minimum level) | Blood stock, Minimum level | si: රුධිර තොග, අවම මට්ටම; ta: இரத்த இருப்பு, குறைந்தபட்ச அளவு | "stock" / "threshold" not in glossary |
| `dashUi.admin.approveTitle`, `rejectTitle` | Approve {name}? / Reject {name}? | si: {name} අනුමත කරන්නද?; ta: {name} ஐ அங்கீகரிக்கவா? | Name particle after a Latin-script organisation name |
| `dashUi.units`, `dashUi.requestsCount` | {count} units / requests | Sinhala uses the same word for singular and plural (`one` and `other` identical) | Word order "ඒකක 3" vs "ඒකක 3ක්" |
| `dashUi.matchStatus.*` | Waiting for reply / Accepted / Declined / No response / Donated | si: පිළිතුරු බලාපොරොත්තුවෙන් / පිළිගත්තා / ප්‍රතික්ෂේප කළා / පිළිතුරක් නැත / රුධිර දානය කළා | Past-tense verbs for status chips |
| Glossary | "donation camp" | si: රුධිර දන් දීමේ කඳවුරු | Used as in the glossary; still marked unsure there |

## FE-AI (chatbot, poster generator) - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `chatUi.grounded` | Verified source | si: සත්‍යාපිත මූලාශ්‍රය; ta: சரிபார்க்கப்பட்ட ஆதாரம் | Shown only when the answer cites a stored source |
| `chatUi.sourceStatus.CONFLICT / GAP` | Sources disagree / No verified answer yet | si: මූලාශ්‍ර එකඟ නොවේ / තවම සත්‍යාපිත පිළිතුරක් නැත; ta: ஆதாரங்கள் முரண்படுகின்றன / சரிபார்க்கப்பட்ட பதில் இன்னும் இல்லை | Wording of the knowledge-base status labels |
| `chatUi.replyStyle.*` | Match my message / Sinhala letters / Singlish (English letters) | si: මගේ පණිවිඩයට ගැළපෙන ලෙස / සිංහල අකුරින් / සිංග්ලිෂ් (ඉංග්‍රීසි අකුරින්); ta: என் செய்திக்கு ஏற்ப / சிங்கள எழுத்துகளில் / சிங்கிளிஷ் (ஆங்கில எழுத்துகளில்) | The term "Singlish" in Sinhala and Tamil script |
| `chatUi.err.rateLimit` | You are asking questions too quickly. Please wait {seconds}... | si/ta plain `{seconds}` without plural forms | Natural phrasing of the wait message |
| `poster.copy.*.headline/body/caption` | Standard poster wording (urgent request, camp, awareness, thank you) | si and ta drafts written for this change | All 16 strings: tone (calm, no scare language), verbs for "come forward / share", the em dash layout of the camp body |
| `poster.cta.*` | Scan to volunteer or share / Scan for camp details / ... | si: ස්කෑන් කරන්න; ta: ஸ்கேன் செய்யவும் | Loan word "scan" vs a native verb |
| `poster.unverifiedBadge` / `poster.unverifiedStamp` | Unverified / UNVERIFIED | si: සත්‍යාපනය කර නැත / සත්‍යාපිත නොවේ; ta: சரிபார்க்கப்படவில்லை / சரிபார்க்கப்படாதது | Short mark printed on the image; glossary has only "verified" |
| `poster.size.*` | Square / Portrait / Link preview / Story | si: සමචතුරස්‍ර / සිරස් / සබැඳි පෙරදසුන / ස්ටෝරි; ta: சதுரம் / செங்குத்து / இணைப்பு முன்னோட்டம் / ஸ்டோரி | Social-media terms |
| `poster.copySource.ai` | Wording suggested by AI. Read it and correct anything that is wrong... | si/ta drafts | Must read as a clear instruction to check the text |
| `poster.blocked.desc` | Urgent request posters can only be made by verified requesters... | si/ta drafts | Policy message, check tone |
## AI backend (chatbot knowledge base, safety messages, poster copy) - AI-drafted, needs native-speaker review

Every `answer_si` / `answer_ta` in `apps/backend/src/main/resources/kb/nbts-eligibility.v1.json` is flagged `needsHumanReview_si` / `needsHumanReview_ta: true`; the API returns `translationDraft: true` for them. Fixed replies live in `kb/messages.json`, poster copy in `src/main/resources/ai/poster-copy_{si,ta}.properties` (marked `# REVIEW`). Uncertain terms, one line each:

| Term / sentence | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| Donor | donor | si: රුධිර දායකයා; ta: இரத்த கொடையாளர் | Glossary uses "donor" in places; NBTS may prefer another word |
| Medical Officer | Medical Officer (blood bank) | si: වෛද්‍ය නිලධාරියා; ta: மருத்துவ அதிகாரி | Official post title in NBTS Sinhala/Tamil documents |
| Deferred / waiting period | deferred | si: කල් දැමිය යුතුය / රැඳී සිටිය යුතු කාලය; ta: தடை / காத்திருக்க வேண்டிய காலம் | No glossary entry for medical "deferral" |
| Haemoglobin | haemoglobin | si: හීමොග්ලොබින්; ta: ஹீමோகுளோபின் | Spelling varies (හිමො-/හීමො-; ஹீமோ-/ஹெமோ-) |
| Risk behaviours | risk behaviours | si: අවදානම් හැසිරීම්; ta: ஆபத்தான நடத்தைகள் | Sensitive; NBTS has not defined the English term either |
| Malaria-endemic list | malaria-endemic country list | si: මැලේරියා පැතිරී ඇති රටවල ලැයිස්තුව; ta: மலேரியா பரவியுள்ள நாடுகளின் பட்டியல் | Long phrase, check it reads naturally |
| Look-back / press report | a recent news report quoting an NBTS official | si: NBTS නිලධාරියෙකු උපුටා දක්වමින් පළ වූ මෑත පුවත්පත් වාර්තාවක්; ta: NBTS அதிகாரி ஒருவரை மேற்கோள் காட்டிய சமீபத்திய செய்தி அறிக்கை | Must stay neutral: it states a source conflict, not a ruling |
| "must be confirmed with NBTS" | must be confirmed with NBTS | si: NBTS සමඟ තහවුරු කර ගත යුතුය; ta: NBTS உடன் உறுதிப்படுத்த வேண்டும் | Tone of the unresolved-conflict wording |
| "I do not have a confirmed figure" | I do not have a confirmed official figure | si: තහවුරු කළ නිල ... මා සතුව නැත; ta: உறுதிப்படுத்தப்பட்ட அதிகாரப்பூர்வ ... என்னிடம் இல்லை | First person for a bot in a formal register; consider an impersonal form |
| Pregnant (formal) | pregnant | si: ගර්භණී; colloquial ගැබිනි / ගැබ්; ta: கர்ப்பிணி | Retrieval accepts both; the answer uses the formal word |
| Tattoo | tattoo | si: පච්ච; ta: பச்சை குத்துதல் | Common spoken forms differ by region |
| Body piercing | body piercing | si: ශරීරය විදීම; ta: உடல் துளையிடுதல் | Not a standard term |
| Plaster / dressing | plaster | si: ප්ලාස්ටරය; ta: பிளாஸ்திரி | Only in retrieval keywords (answer text avoids it) |
| Emergency ambulance service | emergency ambulance service | si: හදිසි ගිලන්රථ සේවාව; ta: அவசர ஆம்புலன்ஸ் சேவை | The number (default 1990) must be confirmed by the owner |
| Safety reply | "This assistant cannot assess symptoms" | si: මෙම සහායකයාට රෝග ලක්ෂණ තක්සේරු කළ නොහැක; ta: இந்த உதவியாளரால் அறிகுறிகளை மதிப்பிட முடியாது | "assistant" word choice |
| Disclaimer | General information only, not medical advice or a diagnosis | si: රෝග විනිශ්චයක් නොවේ; ta: நோயறிதலோ அல்ல | "diagnosis" term |
| Refusal to share instructions | I cannot share or change my instructions | si: මගේ උපදෙස් බෙදා ගැනීමට හෝ වෙනස් කිරීමට මට නොහැක; ta: எனது அறிவுறுத்தல்களைப் பகிரவோ மாற்றவோ என்னால் இயலாது | Natural phrasing for a chatbot |
| Poster: urgent headline | URGENT: {{bloodType}} blood needed | si: හදිසි: ... රුධිරය අවශ්‍යයි; ta: அவசரம்: ... இரத்தம் தேவை | "URGENT" must not read as a scare tactic |
| Poster: blood units | {{units}} unit(s) | si: රුධිර ඒකක {{units}}ක්; ta: {{units}} யூனிட் | "unit" word (ඒකක / யூனிட்) |
| Poster: camp welcome | Every eligible donor is welcome | si: සුදුසුකම් ඇති සියලු දායකයන්ට සාදරයෙන් පිළිගනිමු; ta: தகுதியுள்ள அனைத்து கொடையாளர்களுக்கும் வரவேற்பு | Register of an invitation |
| Poster: awareness line | Give blood. Give hope. | si: රුධිරය දෙන්න. බලාපොරොත්තුව දෙන්න.; ta: இரத்தம் கொடுங்கள். நம்பிக்கை கொடுங்கள். | Free translation of a slogan |
| Intake messages | Draft ready. Please check every highlighted field ... | si/ta in `kb/messages.json` keys `intakeOk`, `intakeNone`, `intakeEmpty` | "highlighted field" wording |
| Safety word lists | abuse / emergency / injection patterns | `kb/safety-patterns.json` (Sinhala, Tamil and romanised seed lists) | Seed lists written by an AI; a native speaker must extend them with real spoken/written variants |
| Singlish / Tanglish lexicon | 900+ romanised forms | `kb/translit-lexicon.json` | Spelling variants and the si/ta marker words used to detect Singlish vs Tanglish need native review |

## FE integration (server error codes, account recovery, invitation) - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `authFlow.errors.<code>` (about 115 server codes, see `docs/API-CONTRACT.md` section 12) | e.g. "The email or password is incorrect." | plain formal register, re-using the glossary terms (මුරපදය / கடவுச்சொல், සත්‍යාපන කේතය / சரிபார்ப்புக் குறியீடு) | Whole set is AI-drafted; check tone of the security messages (`auth.account.locked`, `auth.account.suspended`, `auth.register.duplicate`) |
| `authFlow.errors.auth.register.duplicate` | An account with these details already exists. Try signing in instead. | si: ... දැනටමත් ඇත. ඒ වෙනුවට පිවිසීමට උත්සාහ කරන්න.; ta: ... ஏற்கெனவே உள்ளது. அதற்குப் பதிலாக உள்நுழைய முயற்சிக்கவும். | Deliberately one text for "email taken" and "phone taken" (no account enumeration) |
| `authFlow.errors.retryInSeconds` / `retryInMinutes` | Try again in 45 seconds / 3 minutes. | si: තත්පර {seconds}කින් නැවත උත්සාහ කරන්න.; ta: {seconds} வினாடிகளுக்குப் பிறகு மீண்டும் முயற்சிக்கவும். | Sinhala/Tamil use one plural form here |
| `authFlow.errors.slot.*`, `request.*`, `inventory.*` | "This time slot is fully booked." ... | "වේලාව" for slot (as in the backend SMS texts), "අලුත් අඩු/අවදානම් මට්ටම" for low / critical stock level | "slot" and "stock level" terms are not in the glossary |
| `authFlow.errors.poster.*` | "Posters must not contain a patient's name ..." | si/ta drafts | Policy wording |
| `authFlow.googleLink*` | Link your Google account / An account for {email} already exists ... | si: ඔබේ Google ගිණුම සම්බන්ධ කරන්න; ta: உங்கள் Google கணக்கை இணைக்கவும் | "link accounts" wording |
| `authFlow.verifyEmailIdle`, `resendVerificationHelp`, `verificationSent`, `signInToResend`, `emailAlreadyVerified` | Sign in and we can send a new one ... | si/ta drafts | Reworded because resending a verification link now needs a signed-in user |
| `authFlow.invite.*`, `pub.meta.acceptInvite`, `pub.eyebrowInvite` | Accept your invitation ... administrator | si: ඔබේ ආරාධනය පිළිගන්න; ta: உங்கள் அழைப்பை ஏற்கவும் | "administrator" = පරිපාලක / நிர்வாகி per glossary |

## FE integration: admin queue, analytics, triage - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `dashUi.kpi.*`, `dashUi.admin.metricsDefinition` | Matched, awaiting donation / Last {days} days / Fulfilment rate = fulfilled ÷ (fulfilled + expired + cancelled) | si: ගැළපී ඇත, රුධිර දානය බලාපොරොත්තුවෙන්; ta: பொருந்தியது, இரத்த தானத்துக்காகக் காத்திருக்கிறது | New definition of the rate (30-day window); "expired" / "cancelled" request terms |
| `dashUi.shortage.NO_DATA`, `pub.level.NO_DATA`, `pub.levelDesc.*` | No data; "... available donors for each unit of blood still needed" | si: දත්ත නැත; ta: தரவு இல்லை | The shortage rule is now per unit of blood still needed (was per request) |
| `dashUi.reportReason.*` | Fake or misleading / Duplicate request / Spam / Abusive content / Other reason | si/ta drafts | "Spam" loan word |
| `dashUi.triage.*` | Why this request was flagged / The score comes from fixed rules, not machine learning / Flag dismissed | si/ta drafts for the six reason codes | Tone: calm, no accusation of the requester |
| `dashUi.admin.queue*`, `emailUnverified`, `dashUi.reasonTooShort` | Hospitals and blood banks / Patients and families | si: රෝහල් සහ රුධිර බැංකු; ta: மருத்துவமனைகள் மற்றும் இரத்த வங்கிகள் | |

## FE integration: blood stock - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `dashUi.inv.status.*` | In stock / Low / Critical / Expiring soon | si: තොගයේ ඇත / අඩුයි / අවදානම් මට්ටමේ / ඉක්මනින් කල් ඉකුත් වේ; ta: இருப்பில் உள்ளது / குறைவு / அபாய நிலை / விரைவில் காலாவதியாகும் | "Critical" for stock level vs. the shortage level wording elsewhere |
| `dashUi.inv.component.RBC` | Red cells | si: රතු රුධිර සෛල; ta: சிவப்பணுக்கள் | Replaces RED_CELLS (the server enum is RBC); use the NBTS spelling |
| `dashUi.inv.lowLevel*`, `criticalLevel*`, `setThresholdDesc` | Low level / Critical level (at or below) | "අඩු මට්ටම / අවදානම් මට්ටම"; "குறைந்த அளவு / அபாய அளவு" | "threshold" terms |
| `dashUi.inv.issue*`, `discard*`, `reason.*` | Issue units / Discard / Collection, Issued, Expired, Correction, Discarded | si/ta drafts | Blood-bank vocabulary ("issue" = hand out to a patient) |
| `dashUi.inv.tickerOn/Off`, `publicSignals*` | Low and critical stock is shown on the public home page ... | si/ta drafts | Privacy wording (no donor details are shown) |

## FE integration: request matches and closing - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `dashUi.markDonated`, `markNotDonated`, `recordDonatedDesc`, `recordNotDonatedDesc`, `matchStatus.NO_SHOW` | Donated / Did not donate / "The donor's reliability score goes down ..." | si: රුධිර දානය කළා / රුධිර දානය කළේ නැත; ta: இரத்த தானம் செய்தார் / இரத்த தானம் செய்யவில்லை | Consequences wording (waiting period, reliability score) must be gentle and exact |
| `dashUi.matchesSummary`, `req.progress`, `req.progressShort` | "{accepted} accepted, {confirmed} donations confirmed, {needed} units needed" | si/ta drafts | Counter wording |
| `dashUi.req.closeReason.*`, `closeReasonLegend` | The blood is no longer needed / I found a donor elsewhere / Another reason | si/ta drafts | |
| `dashUi.req.closedOn*`, `coarseLocation` | Closed on {date}. Reason: {reason}. / The location is shown only approximately | si/ta drafts | |

## FE integration: donor eligibility and history - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `dashUi.donor.reason.*` (AGE_OUT_OF_RANGE, AGE_ABOVE_FIRST_TIME_LIMIT, UNDERWEIGHT, RECENT_DONATION, ACTIVE_DEFERRAL, UNKNOWN) | "You donated recently. Wait for the minimum time between donations." | si/ta drafts | Sensitive: a donor is told why they cannot give; keep neutral and kind |
| `dashUi.donor.rules*`, `rule*` | What are the rules? / Age 18 to 60 / Weight above 50 kg / These figures come from the national blood service website and are still being confirmed | si/ta drafts | The 50 / 55 kg and 3 / 2 year rulings are still open with NBTS (docs/AI.md section 8) |
| `dashUi.donor.source.*`, `weightTooLow`, `dash.weightHint` | Blood request / Booked time slot / Weight must be above {kg} kg | si/ta drafts | "above 50" is a strict limit now (was "minimum 50") |

## FE integration: notifications inbox - needs native-speaker review

`notifications.<type>.title/body` (match, accepted, fulfilled, closed, expired, reopened, lowStock, pledge, generic, slot.confirmed) are rendered from the inbox `titleKey`/`bodyKey` + `params`. `match` and `slot.confirmed` re-use the backend's own si/ta wording (`NotificationTemplates.java`); the others (accepted, fulfilled, closed, expired, reopened, lowStock, pledge) are new si/ta drafts here and the backend has no si/ta text for them yet (it falls back to English for SMS/e-mail). Also `dashUi.notif.loadMore` ("Show older notifications").

## FE integration: AI request intake and chatbot notes - needs native-speaker review

| Key | English | Wording used (si / ta) | Why unsure |
|---|---|---|---|
| `aiRequest.pleaseCheck`, `thisIsCorrect`, `checkFirst` | Please check this. It was filled in from your text and may be wrong. / This is correct | si: කරුණාකර මෙය පරීක්ෂා කරන්න ... / මෙය නිවැරදියි; ta: இதைச் சரிபார்க்கவும் ... / இது சரியானது | Safety-relevant: the person must confirm AI-filled values before a request is broadcast |
| `aiRequest.notFound`, `field.*`, `draftReady`, `draftNone`, `verifyEditAi`, `aiPrivacy` | We could not find these in your text ... / Phone numbers and ID numbers are removed before the text is analysed | si/ta drafts | Privacy sentence must match the real behaviour (docs/AI.md section 4: phone numbers and NICs removed, names are not) |
| `aiRequest.choose`, `chooseBloodType`, `unitsInvalid`, `chooseUrgency`, `hospitalRequired`, `chooseDistrict`, `locationNeedsDistrict` | Choose... / Choose the hospital's district. | si/ta drafts | |
| `chatUi.translationDraft`, `chatUi.sourceStatus.SECONDARY`, `chatUi.needsConfirmation` | Draft translation, still waiting for review. / Secondary source (not official) / ... confirm with the National Blood Transfusion Service | si/ta drafts | NBTS official Sinhala/Tamil name is still marked unsure in the glossary |

## FE integration: account area (/dashboard/account) - needs native-speaker review

All `account.*` strings (about 60) and `dashUi.nav.account` are si/ta drafts. Please look at: `account.deleteWarning`, `deleteDialogDesc`, `deleteDesc` (irreversible erasure, tone must be calm and exact; the Personal Data Protection Act No. 9 of 2022 wording should come from the privacy notice), `account.consentRequired` / `consentedOn` (terms and privacy policy names must match `auth.termsLink` / `auth.privacyLink`), `account.emailUnconfirmedHelp` and `emailBannerDesc` (what stays locked until the e-mail is confirmed), `account.language` / `languageHelp` ("language for SMS and e-mail" vs the website language), `account.confirmSmsOption`.

## FE integration: misc

`authFlow.errors.retryInSeconds` / `retryInMinutes` are appended to 429 and lock-out messages. `common.edit` ("Edit"). The unit tests in `apps/frontend/src/lib/__tests__/apiError.test.ts` and `.../api/__tests__/notifications.test.ts` fail when a server error code or notification key has no en/si/ta text, so new backend codes show up there first.


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

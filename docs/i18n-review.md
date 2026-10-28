
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

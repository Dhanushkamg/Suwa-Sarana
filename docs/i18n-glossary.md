# i18n glossary (en / සිංහල / தமிழ்)

One rendering per term, used everywhere (UI, emails, SMS, chatbot, posters). Register: formal but plain, as used in Sri Lankan
government and health communication — not literal machine translation. **All Sinhala/Tamil entries below are drafts that
need review by a native speaker**; rows marked ⚠ are the ones we are least sure about. Reviewers: edit this table, and the
code follows it (`messages/*.json`, backend `i18n/*.properties`). `docs/i18n-review.md` lists strings that were added after
this glossary and still wait for review.

| English | සිංහල | தமிழ் | Notes |
|---|---|---|---|
| blood donor | රුධිර දායකයා | இரத்த தானம் செய்பவர் | person who donates |
| blood donation | රුධිර දානය | இரத்த தானம் | |
| requester | ඉල්ලුම්කරු | கோரிக்கையாளர் | patient or family |
| patient | රෝගියා | நோயாளி | |
| hospital | රෝහල | மருத்துவமனை | |
| blood bank | රුධිර බැංකුව | இரத்த வங்கி | |
| donation camp | රුධිර දන් දීමේ කඳවුර ⚠ | இரத்த தான முகாம் | Sinhala: confirm "කඳවුර" vs "වැඩසටහන" |
| National Blood Transfusion Service (NBTS) | ජාතික රුධිර පාරවිලයන සේවය ⚠ | தேசிய இரத்த மாற்று சேவை ⚠ | use the official Sinhala/Tamil name from NBTS |
| Medical Officer | වෛද්‍ය නිලධාරී | மருத்துவ அதிகாரி | final eligibility decision |
| verified | සත්‍යාපිත | சரிபார்க்கப்பட்டது | |
| pending verification | සත්‍යාපනය බලාපොරොත්තුවෙන් | சரிபார்ப்பு நிலுவையில் | |
| eligibility | සුදුසුකම | தகுதி | |
| eligible to donate | රුධිර දානයට සුදුසුයි | இரத்த தானம் செய்யத் தகுதியுடையவர் | |
| deferral | තාවකාලික වැළැක්වීම ⚠ | தற்காலிகத் தள்ளிவைப்பு ⚠ | temporary "not now" decision; confirm NBTS wording |
| next eligible date | ඊළඟට රුධිර දිය හැකි දිනය | அடுத்து இரத்தம் வழங்கத் தகுதியான தேதி | |
| district | දිස්ත්‍රික්කය | மாவட்டம் | 25 districts |
| urgency | හදිසි බව | அவசரநிலை | |
| critical / urgent / routine | අතිශය හදිසි / හදිසි / සාමාන්‍ය | மிக அவசரம் / அவசரம் / வழக்கமானது | |
| blood type / group | රුධිර වර්ගය | இரத்த வகை | A+, O− … never translated |
| units (of blood) | ඒකක | அலகுகள் | |
| match (donor ↔ request) | ගැළපීම | பொருத்தம் | |
| accept / decline | පිළිගන්න / ප්‍රතික්ෂේප කරන්න | ஏற்கவும் / மறுக்கவும் | |
| fulfilled | සම්පූර්ණ කළා | நிறைவேற்றப்பட்டது | |
| availability | ලබා දිය හැකි බව | கிடைக்கும் தன்மை | "available to donate" |
| reliability score | විශ්වසනීයත්ව ලකුණු ⚠ | நம்பகத்தன்மை மதிப்பெண் ⚠ | explain how computed in help text |
| notification | දැනුම්දීම | அறிவிப்பு | |
| verification code (OTP) | සත්‍යාපන කේතය | சரிபார்ப்புக் குறியீடு | |
| privacy policy / terms | රහස්‍යතා ප්‍රතිපත්තිය / නියම සහ කොන්දේසි | தனியுரிமைக் கொள்கை / விதிமுறைகள் | |
| sign in / sign out / create account | පිවිසෙන්න / ඉවත් වන්න / ගිණුමක් සාදන්න | உள்நுழைக / வெளியேறுக / கணக்கை உருவாக்குக | |
| admin | පරිපාලක | நிர்வாகி | |

Style rules: numbers and dates are formatted by `Intl` for the active locale (never concatenated); plural forms use ICU
`{count, plural, one {…} other {…}}`; blood types, units (mg/dL, kg) and brand names stay in Latin script; do not translate
the product name "Suwa Sarana".

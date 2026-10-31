package com.suwasarana.api.ai.reply;

import java.util.HashMap;
import java.util.Map;

/**
 * Rule-based Sinhala-to-Latin transliteration, used to answer a Singlish question in "Singlish" (romanised Sinhala)
 * when the caller asks for {@code replyStyle=romanised}. It follows the informal spelling Sri Lankans use on phones
 * (rudhira daanaya, wayasa) rather than a scholarly scheme. Latin letters, digits and punctuation pass through
 * unchanged. Tamil text is not transliterated (a Tanglish question is answered in Tamil script).
 */
public final class SinhalaRomaniser {

    private static final Map<Integer, String> INDEPENDENT = new HashMap<>();
    private static final Map<Integer, String> CONSONANT = new HashMap<>();
    private static final Map<Integer, String> SIGN = new HashMap<>();

    static {
        String[] ind = {"අ:a", "ආ:aa", "ඇ:ae", "ඈ:aae", "ඉ:i", "ඊ:ii", "උ:u", "ඌ:uu", "ඍ:ru", "එ:e", "ඒ:ee", "ඓ:ai", "ඔ:o", "ඕ:oo", "ඖ:au"};
        for (String s : ind) INDEPENDENT.put(s.codePointAt(0), s.substring(s.indexOf(':') + 1));
        String[] con = {"ක:k", "ඛ:kh", "ග:g", "ඝ:gh", "ඞ:ng", "ඟ:ng", "ච:ch", "ඡ:chh", "ජ:j", "ඣ:jh", "ඤ:ny", "ඦ:nj", "ට:t", "ඨ:th", "ඩ:d",
                "ඪ:dh", "ණ:n", "ඬ:nd", "ත:th", "ථ:thh", "ද:d", "ධ:dh", "න:n", "ඳ:nd", "ප:p", "ඵ:ph", "බ:b", "භ:bh", "ම:m", "ඹ:mb",
                "ය:y", "ර:r", "ල:l", "ව:w", "ශ:sh", "ෂ:sh", "ස:s", "හ:h", "ළ:l", "ෆ:f"};
        for (String s : con) CONSONANT.put(s.codePointAt(0), s.substring(s.indexOf(':') + 1));
        // dependent vowel signs; "" means the inherent vowel is replaced by nothing special (handled below)
        String[] sg = {"ා:aa", "ැ:ae", "ෑ:aae", "ි:i", "ී:ii", "ු:u", "ූ:uu", "ෘ:ru",
                "ෙ:e", "ේ:ee", "ෛ:ai", "ො:o", "ෝ:oo", "ෞ:au"};
        for (String s : sg) SIGN.put(s.codePointAt(0), s.substring(s.indexOf(':') + 1));
    }

    private SinhalaRomaniser() {
    }

    public static String romanise(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        int[] cps = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFC).codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            if (CONSONANT.containsKey(cp)) {
                out.append(CONSONANT.get(cp));
                int next = i + 1 < cps.length ? cps[i + 1] : -1;
                if (next == 0x200D && i + 2 < cps.length) { // conjunct with ZWJ: skip joiner, consonant cluster without 'a'
                    i++;
                    next = cps[i + 1];
                }
                if (next == 0x0DCA) { // hal kirima: no vowel
                    i++;
                } else if (SIGN.containsKey(next)) {
                    out.append(SIGN.get(next));
                    i++;
                } else {
                    out.append('a');
                }
            } else if (INDEPENDENT.containsKey(cp)) {
                out.append(INDEPENDENT.get(cp));
            } else if (cp == 0x0D82) { // anusvara
                out.append("ng");
            } else if (cp == 0x0D83) { // visarga
                out.append('h');
            } else if (cp == 0x200D || cp == 0x200C || cp == 0x0DCA) {
                // joiners and stray hal kirima
            } else if (SIGN.containsKey(cp)) {
                out.append(SIGN.get(cp));
            } else {
                out.appendCodePoint(cp);
            }
        }
        return out.toString();
    }
}

package com.suwasarana.api.ai.retrieval;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Unicode-aware cleaning and tokenisation for English, Sinhala, Tamil and romanised (Singlish/Tanglish) text. */
public final class TextUtil {

    private TextUtil() {
    }

    public enum Script { LATIN, SINHALA, TAMIL, DIGIT }

    /** A token with its script. */
    public record Tok(String text, Script script) {
    }

    /**
     * NFC-normalises, removes zero-width characters (Sinhala conjuncts are often typed with and without a ZWJ),
     * replaces control characters with spaces and lower-cases with the root locale.
     */
    public static String clean(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFC);
        StringBuilder sb = new StringBuilder(n.length());
        n.codePoints().forEach(cp -> {
            if (cp == 0x200B || cp == 0x200C || cp == 0x200D || cp == 0xFEFF || cp == 0x2060 || cp == 0xAD) return;
            if (Character.isISOControl(cp)) {
                sb.append(' ');
                return;
            }
            sb.appendCodePoint(cp);
        });
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    public static boolean isSinhala(int cp) {
        return cp >= 0x0D80 && cp <= 0x0DFF;
    }

    public static boolean isTamil(int cp) {
        return cp >= 0x0B80 && cp <= 0x0BFF;
    }

    public static boolean isLatinLetter(int cp) {
        return Character.isLetter(cp) && Character.UnicodeScript.of(cp) == Character.UnicodeScript.LATIN;
    }

    private static Script scriptOf(int cp) {
        if (isSinhala(cp)) return Script.SINHALA;
        if (isTamil(cp)) return Script.TAMIL;
        if (isLatinLetter(cp)) return Script.LATIN;
        if (Character.isDigit(cp)) return Script.DIGIT;
        return null;
    }

    /**
     * Splits already cleaned text into maximal runs of one script. Apostrophes inside a Latin word are dropped
     * ("don't" gives "dont"); every other non-letter is a separator. Combining marks stay with the current token.
     */
    public static List<Tok> tokens(String cleaned) {
        List<Tok> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        Script curScript = null;
        int[] cps = cleaned.codePoints().toArray();
        for (int i = 0; i < cps.length; i++) {
            int cp = cps[i];
            Script sc = scriptOf(cp);
            boolean mark = sc == null && (Character.getType(cp) == Character.NON_SPACING_MARK
                    || Character.getType(cp) == Character.COMBINING_SPACING_MARK
                    || Character.getType(cp) == Character.ENCLOSING_MARK);
            boolean apostrophe = (cp == '\'' || cp == 0x2019) && curScript == Script.LATIN
                    && i + 1 < cps.length && isLatinLetter(cps[i + 1]);
            if (apostrophe) continue;
            if (sc == null && mark && curScript != null) {
                cur.appendCodePoint(cp);
                continue;
            }
            if (sc != null && sc == curScript) {
                cur.appendCodePoint(cp);
                continue;
            }
            if (curScript != null) {
                out.add(new Tok(cur.toString(), curScript));
                cur.setLength(0);
                curScript = null;
            }
            if (sc != null) {
                curScript = sc;
                cur.appendCodePoint(cp);
            }
        }
        if (curScript != null) out.add(new Tok(cur.toString(), curScript));
        return out;
    }

    /** Number of Sinhala / Tamil / Latin letters (marks and digits ignored). */
    public static int[] letterCounts(String cleaned) {
        int si = 0, ta = 0, la = 0;
        for (int cp : cleaned.codePoints().toArray()) {
            if (isSinhala(cp) && Character.isLetter(cp)) si++;
            else if (isTamil(cp) && Character.isLetter(cp)) ta++;
            else if (isLatinLetter(cp)) la++;
        }
        return new int[]{si, ta, la};
    }

    /** Strips trailing combining marks (vowel signs, virama) so that stems can be compared consonant-wise. */
    public static int codePointLength(String s) {
        return s.codePointCount(0, s.length());
    }
}

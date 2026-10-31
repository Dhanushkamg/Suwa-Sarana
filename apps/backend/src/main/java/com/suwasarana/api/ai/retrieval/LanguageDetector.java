package com.suwasarana.api.ai.retrieval;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the input style of a message: English, Sinhala script, Tamil script, Singlish (Sinhala in Latin letters)
 * or Tanglish (Tamil in Latin letters). Script detection is by Unicode letters; romanised text is recognised by
 * language-specific marker words from the lexicon.
 */
@Component
public class LanguageDetector {

    /** The style of the user's text. */
    public enum InputStyle {
        EN("en"), SI("si"), TA("ta"), SINGLISH("si"), TANGLISH("ta"), UNKNOWN("en");

        private final String replyLanguage;

        InputStyle(String replyLanguage) {
            this.replyLanguage = replyLanguage;
        }

        /** Language of the script a natural reply would use ("en", "si" or "ta"). */
        public String replyLanguage() {
            return replyLanguage;
        }

        public boolean isRomanised() {
            return this == SINGLISH || this == TANGLISH;
        }

        /** Short code for the API: en, si, ta, singlish, tanglish, unknown. */
        public String code() {
            return name().toLowerCase();
        }
    }

    private static final double SCRIPT_SHARE = 0.30;

    private final Lexicon lexicon;

    public LanguageDetector(Lexicon lexicon) {
        this.lexicon = lexicon;
    }

    public InputStyle detect(String text) {
        String cleaned = TextUtil.clean(text);
        List<TextUtil.Tok> toks = TextUtil.tokens(cleaned);
        int si = 0, ta = 0, la = 0;
        for (TextUtil.Tok t : toks) {
            if (TextUtil.codePointLength(t.text()) < 2) continue;
            switch (t.script()) {
                case SINHALA -> si++;
                case TAMIL -> ta++;
                case LATIN -> la++;
                default -> { }
            }
        }
        int total = si + ta + la;
        if (total == 0) return InputStyle.UNKNOWN;
        if (si + ta > 0 && (double) (si + ta) / total >= SCRIPT_SHARE) {
            return si >= ta ? InputStyle.SI : InputStyle.TA;
        }
        int siHits = 0, taHits = 0;
        for (TextUtil.Tok t : toks) {
            if (t.script() != TextUtil.Script.LATIN) continue;
            if (lexicon.isSinhalaMarker(t.text())) siHits++;
            if (lexicon.isTamilMarker(t.text())) taHits++;
        }
        if (siHits == 0 && taHits == 0) {
            return InputStyle.EN;
        }
        return taHits > siHits ? InputStyle.TANGLISH : InputStyle.SINGLISH;
    }
}

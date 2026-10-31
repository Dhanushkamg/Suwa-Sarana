package com.suwasarana.api.ai.retrieval;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Turns free text in any of the supported input styles into an ordered list of canonical {@link Term}s.
 * <ul>
 *   <li>English: lower-case, lexicon canonicalisation (inflections, synonyms), light plural stemming, stopwords out.</li>
 *   <li>Singlish/Tanglish: romanised words mapped to canonical English tokens through the lexicon (phrases first).</li>
 *   <li>Sinhala/Tamil script: the token is reduced to the longest knowledge-base keyword stem it starts with, which
 *       strips case/plural suffixes without a stemmer (matching is by whole token or stem prefix, never by
 *       substring in the middle of a word).</li>
 * </ul>
 * "Soft" terms (blood, donate ...) are domain words: they never score on their own but still form bigrams
 * ("after donating" stays a phrase).
 */
public final class TextNormalizer {

    /** A normalised term; {@code soft} terms are domain words excluded from unigram scoring. */
    public record Term(String text, boolean soft) {
    }

    private static final int MIN_SCRIPT_TOKEN = 2;

    private final Lexicon lexicon;
    private final Set<String> sinhalaVocab;
    private final Set<String> tamilVocab;
    private final int maxVocabLen;

    public TextNormalizer(Lexicon lexicon, Set<String> sinhalaVocab, Set<String> tamilVocab) {
        this.lexicon = lexicon;
        this.sinhalaVocab = sinhalaVocab;
        this.tamilVocab = tamilVocab;
        int m = 0;
        for (String s : sinhalaVocab) m = Math.max(m, s.length());
        for (String s : tamilVocab) m = Math.max(m, s.length());
        this.maxVocabLen = m;
    }

    /** All tokens of a text after cleaning (used to build the script vocabularies from the knowledge base). */
    public static List<TextUtil.Tok> rawTokens(String text) {
        return TextUtil.tokens(TextUtil.clean(text));
    }

    public List<Term> normalize(String text) {
        List<TextUtil.Tok> toks = rawTokens(text);
        List<Term> out = new ArrayList<>();
        for (int i = 0; i < toks.size(); i++) {
            TextUtil.Tok t = toks.get(i);
            switch (t.script()) {
                case LATIN -> i = latin(toks, i, out);
                case SINHALA -> script(t.text(), true, out);
                case TAMIL -> script(t.text(), false, out);
                default -> { /* numbers carry no retrieval signal */ }
            }
        }
        return out;
    }

    /** Distinct canonical term strings of a text, unigrams (non-soft) plus bigrams over all terms. */
    public static List<String> termsWithBigrams(List<Term> terms) {
        List<String> out = new ArrayList<>();
        for (Term t : terms) if (!t.soft()) out.add(t.text());
        for (int i = 0; i + 1 < terms.size(); i++) {
            out.add(terms.get(i).text() + "_" + terms.get(i + 1).text());
        }
        return out;
    }

    // ------------------------------------------------------------------ Latin

    private int latin(List<TextUtil.Tok> toks, int i, List<Term> out) {
        // phrases of 3 then 2 consecutive Latin words
        if (lexicon.hasPhrases()) {
            for (int len = 3; len >= 2; len--) {
                if (i + len <= toks.size() && allLatin(toks, i, len)) {
                    StringBuilder sb = new StringBuilder();
                    for (int k = 0; k < len; k++) {
                        if (k > 0) sb.append(' ');
                        sb.append(toks.get(i + k).text());
                    }
                    String canon = lexicon.lookupPhrase(sb.toString());
                    if (canon != null) {
                        emitCanon(canon, out);
                        return i + len - 1;
                    }
                }
            }
        }
        String w = toks.get(i).text();
        String canon = lexicon.lookup(w);
        if (canon == null) canon = stem(w);
        emitCanon(canon, out);
        return i;
    }

    private static boolean allLatin(List<TextUtil.Tok> toks, int from, int len) {
        for (int k = 0; k < len; k++) if (toks.get(from + k).script() != TextUtil.Script.LATIN) return false;
        return true;
    }

    private void emitCanon(String canon, List<Term> out) {
        if (Lexicon.DROP.equals(canon)) return;
        for (String part : canon.split(" ")) {
            if (part.isEmpty() || lexicon.isLatinStop(part)) continue;
            out.add(new Term(part, lexicon.isLatinDomain(part)));
        }
    }

    /** Conservative English plural stemming (the lexicon already holds the important inflections). */
    static String stem(String w) {
        int n = w.length();
        if (n > 4 && w.endsWith("ies")) return w.substring(0, n - 3) + "y";
        if (n > 4 && (w.endsWith("sses") || w.endsWith("shes") || w.endsWith("ches") || w.endsWith("xes"))) return w.substring(0, n - 2);
        if (n > 3 && w.endsWith("s") && !w.endsWith("ss") && !w.endsWith("us") && !w.endsWith("is")) return w.substring(0, n - 1);
        return w;
    }

    // ------------------------------------------------------------------ Sinhala / Tamil

    private void script(String token, boolean sinhala, List<Term> out) {
        if (TextUtil.codePointLength(token) < MIN_SCRIPT_TOKEN) return;
        if (sinhala ? lexicon.isSinhalaStop(token) : lexicon.isTamilStop(token)) return;
        Set<String> vocab = sinhala ? sinhalaVocab : tamilVocab;
        String vocabStem = longestPrefix(token, vocab);
        String domainStem = sinhala ? lexicon.sinhalaDomainStem(token) : lexicon.tamilDomainStem(token);
        if (domainStem != null && (vocabStem == null || vocabStem.length() <= domainStem.length())) {
            out.add(new Term(domainStem, true));
            return;
        }
        out.add(new Term(vocabStem != null ? vocabStem : token, false));
    }

    private static String longestPrefix(String token, Collection<String> vocab) {
        if (vocab.contains(token)) return token;
        // prefixes on code point boundaries, longest first
        int[] cps = token.codePoints().toArray();
        for (int len = Math.min(cps.length - 1, 64); len >= MIN_SCRIPT_TOKEN; len--) {
            String p = new String(cps, 0, len);
            if (vocab.contains(p)) return p;
        }
        return null;
    }

    /** Longest vocabulary stem the token starts with (package-visible for tests). */
    String stemOf(String token, boolean sinhala) {
        return longestPrefix(token, sinhala ? sinhalaVocab : tamilVocab);
    }

    int maxVocabLen() {
        return maxVocabLen;
    }
}

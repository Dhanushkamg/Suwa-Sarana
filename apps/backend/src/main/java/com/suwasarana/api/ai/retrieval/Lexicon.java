package com.suwasarana.api.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The Singlish/Tanglish transliteration lexicon ({@code kb/translit-lexicon.json}): romanised Sinhala/Tamil spellings
 * (and English inflections) mapped to canonical tokens, plus language-detector markers, stopwords and domain terms.
 * <p>
 * Lookup is exact first, then on a phonetic {@linkplain #skeleton(String) skeleton} (so "puluvanda" and "puluwanda" or
 * "dhenna" and "denna" meet) for words of 4+ letters. Canon {@code _} means "carries no topic, drop it".
 */
@Component
public class Lexicon {

    public static final String RESOURCE = "kb/translit-lexicon.json";
    public static final String DROP = "_";

    private final Map<String, String> exact = new HashMap<>();
    private final Map<String, String> skeleton = new HashMap<>();
    private final Map<String, String> phrases = new HashMap<>();
    private final Set<String> markersSi = new HashSet<>();
    private final Set<String> markersTa = new HashSet<>();
    private final Set<String> markersSiSk = new HashSet<>();
    private final Set<String> markersTaSk = new HashSet<>();
    private final Set<String> stopLatin = new HashSet<>();
    private final Set<String> stopSinhala = new HashSet<>();
    private final Set<String> stopTamil = new HashSet<>();
    private final Set<String> domainLatin = new HashSet<>();
    private final List<String> domainSinhala = new ArrayList<>();
    private final List<String> domainTamil = new ArrayList<>();
    private final List<String> conflicts = new ArrayList<>();
    private int formCount;

    public Lexicon() {
        this(RESOURCE);
    }

    public Lexicon(String resource) {
        try (InputStream in = Lexicon.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Missing resource " + resource);
            JsonNode root = new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            for (JsonNode g : root.path("groups")) {
                String canon = g.path("canon").asText();
                for (JsonNode f : g.path("forms")) {
                    addForm(f.asText().toLowerCase(Locale.ROOT), canon);
                }
            }
            for (JsonNode m : root.path("markers").path("si")) markersSi.add(m.asText().toLowerCase(Locale.ROOT));
            for (JsonNode m : root.path("markers").path("ta")) markersTa.add(m.asText().toLowerCase(Locale.ROOT));
            for (String m : markersSi) if (m.length() >= 4) markersSiSk.add(skeleton(m));
            for (String m : markersTa) if (m.length() >= 4) markersTaSk.add(skeleton(m));
            for (JsonNode m : root.path("stopwords").path("latin")) stopLatin.add(m.asText().toLowerCase(Locale.ROOT));
            for (JsonNode m : root.path("stopwords").path("si")) stopSinhala.add(TextUtil.clean(m.asText()));
            for (JsonNode m : root.path("stopwords").path("ta")) stopTamil.add(TextUtil.clean(m.asText()));
            for (JsonNode m : root.path("domain").path("latin")) domainLatin.add(m.asText().toLowerCase(Locale.ROOT));
            for (JsonNode m : root.path("domain").path("si")) domainSinhala.add(TextUtil.clean(m.asText()));
            for (JsonNode m : root.path("domain").path("ta")) domainTamil.add(TextUtil.clean(m.asText()));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + resource, e);
        }
    }

    private void addForm(String form, String canon) {
        formCount++;
        if (form.contains("+")) {
            phrases.putIfAbsent(form.replace('+', ' '), canon);
            return;
        }
        String prev = exact.putIfAbsent(form, canon);
        if (prev != null && !prev.equals(canon)) {
            conflicts.add(form + " -> " + prev + " | " + canon);
        }
        if (form.length() >= 4) {
            String sk = skeleton(form);
            String p2 = skeleton.putIfAbsent(sk, canon);
            if (p2 != null && !p2.equals(canon)) {
                conflicts.add("skeleton " + sk + " (" + form + ") -> " + p2 + " | " + canon);
            }
        }
    }

    /**
     * Phonetic skeleton of a romanised word: aspirates and sibilant digraphs are folded (th, dh, ph, bh, kh, gh, sh, ch),
     * w becomes v, z becomes s and runs of the same letter collapse. Used only as a fallback after an exact lookup.
     */
    public static String skeleton(String word) {
        String s = word.toLowerCase(Locale.ROOT)
                .replace("ph", "p").replace("bh", "b").replace("dh", "d").replace("th", "t")
                .replace("kh", "k").replace("gh", "g").replace("sh", "s").replace("ch", "c")
                .replace('w', 'v').replace('z', 's');
        StringBuilder sb = new StringBuilder(s.length());
        char prev = 0;
        for (char c : s.toCharArray()) {
            if (c != prev) sb.append(c);
            prev = c;
        }
        return sb.toString();
    }

    /** Canonical token(s) for a romanised/English word, or null when the word is not in the lexicon. */
    public String lookup(String word) {
        String c = exact.get(word);
        if (c != null) return c;
        if (word.length() >= 4) return skeleton.get(skeleton(word));
        return null;
    }

    /** Canonical token(s) for a two- or three-word phrase (words separated by one space), or null. */
    public String lookupPhrase(String phrase) {
        return phrases.get(phrase);
    }

    public boolean hasPhrases() {
        return !phrases.isEmpty();
    }

    public boolean isSinhalaMarker(String word) {
        return markersSi.contains(word) || (word.length() >= 4 && markersSiSk.contains(skeleton(word)));
    }

    public boolean isTamilMarker(String word) {
        return markersTa.contains(word) || (word.length() >= 4 && markersTaSk.contains(skeleton(word)));
    }

    public boolean isLatinStop(String t) { return stopLatin.contains(t); }
    public boolean isSinhalaStop(String t) { return stopSinhala.contains(t); }
    public boolean isTamilStop(String t) { return stopTamil.contains(t); }
    public boolean isLatinDomain(String t) { return domainLatin.contains(t); }

    /** Longest Sinhala domain stem that prefixes the token, or null. */
    public String sinhalaDomainStem(String token) {
        return longestPrefix(domainSinhala, token);
    }

    public String tamilDomainStem(String token) {
        return longestPrefix(domainTamil, token);
    }

    private static String longestPrefix(List<String> stems, String token) {
        String best = null;
        for (String s : stems) {
            if (token.startsWith(s) && (best == null || s.length() > best.length())) best = s;
        }
        return best;
    }

    public int formCount() { return formCount; }
    public int phraseCount() { return phrases.size(); }

    /** Forms that map to two different canonical tokens (should be empty; checked by a unit test). */
    public List<String> conflicts() { return Collections.unmodifiableList(conflicts); }
}

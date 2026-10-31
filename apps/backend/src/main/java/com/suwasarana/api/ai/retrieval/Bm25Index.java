package com.suwasarana.api.ai.retrieval;

import com.suwasarana.api.ai.kb.KbEntry;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small in-memory BM25 index over the knowledge-base entries. Each entry is one document whose terms come from its
 * keyword phrases of every language (weight {@link #W_KEYWORD}), its example questions and its topic (weight
 * {@link #W_EXAMPLE}). Terms are canonical unigrams plus bigrams (see {@link TextNormalizer}).
 */
public final class Bm25Index {

    static final double W_KEYWORD = 3.0;
    static final double W_EXAMPLE = 1.0;
    private static final double K1 = 1.2;
    private static final double B = 0.5;

    /** An indexed document. */
    public static final class Doc {
        private final KbEntry entry;
        private final Map<String, Double> weights = new HashMap<>();
        private double length;

        Doc(KbEntry entry) {
            this.entry = entry;
        }

        public KbEntry entry() { return entry; }
        public Set<String> terms() { return weights.keySet(); }
    }

    private final List<Doc> docs;
    private final Map<String, Integer> df = new HashMap<>();
    private final double avgLength;

    Bm25Index(List<KbEntry> entries, TextNormalizer normalizer) {
        this.docs = new java.util.ArrayList<>();
        double total = 0;
        for (KbEntry e : entries) {
            Doc d = new Doc(e);
            for (Map.Entry<String, List<String>> kw : e.keywords().entrySet()) {
                for (String phrase : kw.getValue()) addPhrase(d, normalizer, phrase, W_KEYWORD);
            }
            for (Map.Entry<String, List<String>> ex : e.questionExamples().entrySet()) {
                for (String q : ex.getValue()) addPhrase(d, normalizer, q, W_EXAMPLE);
            }
            addPhrase(d, normalizer, e.topic(), W_EXAMPLE);
            for (double w : d.weights.values()) d.length += w;
            total += d.length;
            docs.add(d);
            for (String t : new HashSet<>(d.weights.keySet())) df.merge(t, 1, Integer::sum);
        }
        this.avgLength = docs.isEmpty() ? 1 : total / docs.size();
    }

    private static void addPhrase(Doc d, TextNormalizer normalizer, String phrase, double weight) {
        for (String t : TextNormalizer.termsWithBigrams(normalizer.normalize(phrase))) {
            d.weights.merge(t, weight, Double::sum);
        }
    }

    public List<Doc> docs() { return docs; }

    public double idf(String term) {
        int n = df.getOrDefault(term, 0);
        if (n == 0) return 0;
        return Math.log(1 + (docs.size() - n + 0.5) / (n + 0.5));
    }

    /** Whether the term occurs in at least one document. */
    public boolean known(String term) {
        return df.containsKey(term);
    }

    public double score(Doc d, Collection<String> queryTerms) {
        double s = 0;
        for (String t : queryTerms) {
            Double tf = d.weights.get(t);
            if (tf == null) continue;
            s += idf(t) * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * d.length / avgLength));
        }
        return s;
    }
}

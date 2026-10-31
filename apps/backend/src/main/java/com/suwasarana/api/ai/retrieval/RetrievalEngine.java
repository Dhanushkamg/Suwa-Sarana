package com.suwasarana.api.ai.retrieval;

import com.suwasarana.api.ai.AiProperties;
import com.suwasarana.api.ai.kb.KbEntry;
import com.suwasarana.api.ai.kb.KnowledgeBase;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Lexical retrieval over the knowledge base: normalisation ({@link TextNormalizer}), BM25 scoring ({@link Bm25Index}),
 * intent decomposition and a confidence threshold.
 * <p>
 * Multi-intent handling: the question is split into sentences, and for every sentence the engine repeatedly takes the
 * best-scoring entry, removes the query terms that entry explains and retrieves again with the residual terms. So
 * "minimum age and weight", "age saha weight" and the Tamil "வயதும் எடையும்" all return both the age and the weight
 * entry without any conjunction list. A hit needs a minimum score and at least one topical (rare) matching term;
 * below that the caller says it has no confirmed answer instead of guessing.
 */
@Component
public class RetrievalEngine {

    /** One retrieved entry. */
    public record Hit(KbEntry entry, double score, int segment) {
    }

    /**
     * @param hits            retrieved entries in answer order (segment order, best first)
     * @param domainTermSeen  the text mentions blood/donation words or matched an entry
     * @param topicalTerms    number of query terms that exist in the knowledge base vocabulary
     */
    public record Result(List<Hit> hits, boolean domainTermSeen, int topicalTerms) {
    }

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("[?!;\\n।]+|\\.(?!\\d)|(?<!\\d)\\.");
    private static final double TOPICAL_IDF = 1.0;
    private static final int MAX_PER_SEGMENT = 3;
    private static final int MAX_TOTAL = 4;

    private final TextNormalizer normalizer;
    private final Bm25Index index;
    private final double minScore;
    private final double minScoreExtra;

    public RetrievalEngine(KnowledgeBase kb, Lexicon lexicon, AiProperties props) {
        this(kb, lexicon, props.getFaq().getMinScore(), props.getFaq().getMinScoreExtra());
    }

    public RetrievalEngine(KnowledgeBase kb, Lexicon lexicon, double minScore, double minScoreExtra) {
        Set<String> si = new HashSet<>();
        Set<String> ta = new HashSet<>();
        for (KbEntry e : kb.entries()) {
            for (List<String> phrases : e.keywords().values()) {
                for (String p : phrases) {
                    for (TextUtil.Tok t : TextNormalizer.rawTokens(p)) {
                        if (TextUtil.codePointLength(t.text()) < 2) continue;
                        if (t.script() == TextUtil.Script.SINHALA && !lexicon.isSinhalaStop(t.text())) si.add(t.text());
                        if (t.script() == TextUtil.Script.TAMIL && !lexicon.isTamilStop(t.text())) ta.add(t.text());
                    }
                }
            }
        }
        this.normalizer = new TextNormalizer(lexicon, si, ta);
        this.index = new Bm25Index(kb.entries(), normalizer);
        this.minScore = minScore;
        this.minScoreExtra = minScoreExtra;
    }

    public TextNormalizer normalizer() {
        return normalizer;
    }

    /**
     * @param question       the user's question
     * @param priorUserTurns earlier user messages, oldest first; only used when the question alone finds nothing
     *                       (a follow-up such as "and for first-time donors?")
     */
    public Result retrieve(String question, List<String> priorUserTurns) {
        Result r = retrieveText(question, minScore, minScoreExtra);
        if (r.hits().isEmpty() && priorUserTurns != null && !priorUserTurns.isEmpty()) {
            String prev = priorUserTurns.get(priorUserTurns.size() - 1);
            Result combined = retrieveText(prev + " . " + question, minScore, minScoreExtra);
            if (!combined.hits().isEmpty()) return combined;
        }
        return r;
    }

    /** Retrieval with explicit thresholds (the chatbot raises them for text that looks like prompt injection). */
    public Result retrieveStrict(String question, double factor) {
        return retrieveText(question, minScore * factor, minScoreExtra * factor);
    }

    private Result retrieveText(String text, double first, double extra) {
        Map<String, Hit> picked = new LinkedHashMap<>();
        boolean domain = false;
        int topical = 0;
        String[] segments = SENTENCE_SPLIT.split(text == null ? "" : text);
        int segNo = 0;
        for (String seg : segments) {
            if (seg.isBlank()) continue;
            List<TextNormalizer.Term> terms = normalizer.normalize(seg);
            for (TextNormalizer.Term t : terms) if (t.soft()) domain = true;
            Set<String> remaining = new LinkedHashSet<>(TextNormalizer.termsWithBigrams(terms));
            for (String t : remaining) if (index.known(t) && index.idf(t) >= TOPICAL_IDF) topical++;
            int fromSegment = 0;
            while (fromSegment < MAX_PER_SEGMENT && picked.size() < MAX_TOTAL && !remaining.isEmpty()) {
                Bm25Index.Doc best = null;
                double bestScore = 0;
                for (Bm25Index.Doc d : index.docs()) {
                    if (picked.containsKey(d.entry().id())) continue;
                    double s = index.score(d, remaining);
                    if (s > bestScore) {
                        bestScore = s;
                        best = d;
                    }
                }
                double threshold = fromSegment == 0 ? first : extra;
                if (best == null || bestScore < threshold) break;
                Set<String> matched = new HashSet<>();
                boolean topicalMatch = false;
                for (String t : remaining) {
                    if (best.terms().contains(t)) {
                        matched.add(t);
                        if (index.idf(t) >= TOPICAL_IDF) topicalMatch = true;
                    }
                }
                if (!topicalMatch) break;
                picked.put(best.entry().id(), new Hit(best.entry(), bestScore, segNo));
                remaining.removeAll(matched);
                fromSegment++;
                domain = true;
            }
            segNo++;
        }
        return new Result(new ArrayList<>(picked.values()), domain, topical);
    }
}

package com.suwasarana.api.ai.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.AiProperties;
import com.suwasarana.api.ai.DonorFaqAiService;
import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.ai.kb.KbMessages;
import com.suwasarana.api.ai.kb.KnowledgeBase;
import com.suwasarana.api.ai.llm.LlmClient;
import com.suwasarana.api.ai.llm.LlmException;
import com.suwasarana.api.ai.reply.AnswerComposer;
import com.suwasarana.api.ai.retrieval.LanguageDetector;
import com.suwasarana.api.ai.retrieval.Lexicon;
import com.suwasarana.api.ai.retrieval.RetrievalEngine;
import com.suwasarana.api.ai.retrieval.SafetyClassifier;
import com.suwasarana.api.ai.retrieval.TextUtil;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Runs the evaluation set through the deterministic chatbot pipeline (no network, no LLM) and scores it. */
public final class ChatbotEvaluator {

    public static final String RESOURCE = "chatbot-eval.json";
    private static final Set<String> GAP_IDS = Set.of("GAP-TATTOO-PIERCING", "GAP-MEDICATION", "GAP-CHRONIC-ILLNESS",
            "GAP-ACUTE-ILLNESS", "GAP-SURGERY-DENTAL", "GAP-BREASTFEEDING-POSTPARTUM", "GAP-VACCINATION", "GAP-SMOKING",
            "GAP-APPOINTMENT-CHANNEL", "MOBILE-CAMP-ORGANISING", "GAP-POST-DONATION-CARE");

    /** An LLM that is never available (the deterministic pipeline). */
    public static final LlmClient NO_LLM = new LlmClient() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public LlmResult complete(String system, String user, int maxTokens, boolean jsonObject, String model) throws LlmException {
            throw new LlmException(LlmException.Reason.DISABLED, "disabled");
        }
    };

    private ChatbotEvaluator() {
    }

    public record Item(String id, String category, String style, String question, List<FaqQuestionDto.Turn> history,
                       String expectedKind, List<String> expectedIds, List<String> keyFacts, List<String> mustNotInclude,
                       String expectedLang, boolean needsReview) {
    }

    public record Result(Item item, FaqAnswerDto answer, boolean retrievalHit, boolean kindOk, boolean factsOk,
                         boolean forbidOk, boolean langOk, boolean gapNoNumber, List<String> problems) {
        public boolean pass() {
            return retrievalHit && kindOk && factsOk && forbidOk && langOk && gapNoNumber;
        }

        public boolean isRetrievalItem() {
            return !item.expectedIds().isEmpty();
        }
    }

    public static final class Services {
        public final DonorFaqAiService service;
        public final KnowledgeBase kb;
        public final AiProperties props;

        Services(DonorFaqAiService service, KnowledgeBase kb, AiProperties props) {
            this.service = service;
            this.kb = kb;
            this.props = props;
        }
    }

    public static Services build(LlmClient llm, AiProperties props) {
        KnowledgeBase kb = new KnowledgeBase(props);
        Lexicon lexicon = new Lexicon();
        RetrievalEngine engine = new RetrievalEngine(kb, lexicon, props);
        KbMessages messages = new KbMessages(kb);
        DonorFaqAiService svc = new DonorFaqAiService(kb, messages, engine, new LanguageDetector(lexicon), new SafetyClassifier(),
                new AnswerComposer(kb, messages), llm, props);
        return new Services(svc, kb, props);
    }

    public static List<Item> load() throws IOException {
        return load(RESOURCE);
    }

    public static List<Item> load(String resource) throws IOException {
        try (InputStream in = ChatbotEvaluator.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing " + resource);
            JsonNode root = new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            List<Item> items = new ArrayList<>();
            for (JsonNode n : root.path("entries")) {
                List<FaqQuestionDto.Turn> hist = new ArrayList<>();
                for (JsonNode h : n.path("history")) hist.add(new FaqQuestionDto.Turn(h.path("role").asText(), h.path("content").asText()));
                items.add(new Item(n.path("id").asText(), n.path("category").asText(), n.path("style").asText(), n.path("question").asText(), hist,
                        n.path("expected_kind").asText("ANSWER"), strings(n.path("expected_entry_ids")), strings(n.path("expected_key_facts")),
                        strings(n.path("must_not_include")), n.path("expected_reply_language").asText("en"), n.path("needs_human_review").asBoolean()));
            }
            return items;
        }
    }

    private static List<String> strings(JsonNode a) {
        List<String> out = new ArrayList<>();
        for (JsonNode x : a) out.add(x.asText());
        return out;
    }

    public static List<Result> run(Services s, List<Item> items) {
        List<Result> out = new ArrayList<>();
        for (Item it : items) {
            FaqQuestionDto q = new FaqQuestionDto(it.question(), null);
            q.setHistory(new ArrayList<>(it.history()));
            FaqAnswerDto a = s.service.askQuestion(q);
            out.add(score(s, it, a));
        }
        return out;
    }

    static Result score(Services s, Item it, FaqAnswerDto a) {
        List<String> problems = new ArrayList<>();
        String text = a.getAnswer() == null ? "" : a.getAnswer();
        String lower = text.toLowerCase(Locale.ROOT);

        Set<String> cited = new java.util.HashSet<>();
        for (FaqAnswerDto.SourceDto src : a.getSources()) cited.add(src.getId().replace(":press", ""));
        boolean hit = cited.containsAll(it.expectedIds());
        if (!hit) problems.add("expected " + it.expectedIds() + " but cited " + cited + " kind=" + a.getKind());

        boolean kindOk = it.expectedKind().equals("ANY") || it.expectedKind().equals(a.getKind());
        if (!kindOk) problems.add("expected kind " + it.expectedKind() + " got " + a.getKind());

        boolean factsOk = true;
        for (String f : it.keyFacts()) {
            if (!lower.contains(f.toLowerCase(Locale.ROOT))) {
                factsOk = false;
                problems.add("missing fact '" + f + "'");
            }
        }
        boolean forbidOk = true;
        for (String f : it.mustNotInclude()) {
            if (lower.contains(f.toLowerCase(Locale.ROOT))) {
                forbidOk = false;
                problems.add("contains forbidden '" + f + "'");
            }
        }
        boolean langOk = it.expectedLang().equals(a.getLanguage());
        if (!langOk) problems.add("expected reply language " + it.expectedLang() + " got " + a.getLanguage());

        boolean gapNoNumber = true;
        if (!it.expectedIds().isEmpty() && GAP_IDS.containsAll(it.expectedIds())) {
            // when only GAP topics were asked, only GAP/verified-contact entries may be cited, and no figure may appear
            for (String id : cited) {
                if (!GAP_IDS.contains(id)) {
                    // a GAP question answered by a figure-bearing entry
                    gapNoNumber = false;
                    problems.add("GAP question also cited " + id);
                }
            }
            String stripped = text.replace(s.props.getContacts().getNbtsPhone(), " ").replace(s.props.getContacts().getNbtsEmail(), " ");
            if (TextUtil.clean(stripped).codePoints().anyMatch(Character::isDigit)) {
                gapNoNumber = false;
                problems.add("GAP answer contains a digit");
            }
        }
        return new Result(it, a, hit, kindOk, factsOk, forbidOk, langOk, gapNoNumber, problems);
    }

    // ------------------------------------------------------------------ report

    public static String markdown(List<Result> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Chatbot evaluation report\n\n");
        sb.append("Deterministic pipeline (no LLM, no network). ").append(results.size()).append(" questions. ")
                .append("The evaluation set is authored by the same engineer as the engine (not independent); Sinhala/Tamil wording needs native review.\n\n");
        Map<String, int[]> byStyle = new TreeMap<>();
        Map<String, int[]> byCat = new TreeMap<>();
        int[] hits = new int[2];
        int[] pass = new int[2];
        for (Result r : results) {
            pass[1]++;
            if (r.pass()) pass[0]++;
            byCat.computeIfAbsent(r.item().category(), k -> new int[2])[1]++;
            if (r.pass()) byCat.get(r.item().category())[0]++;
            if (r.isRetrievalItem()) {
                hits[1]++;
                if (r.retrievalHit()) hits[0]++;
                int[] st = byStyle.computeIfAbsent(r.item().style(), k -> new int[2]);
                st[1]++;
                if (r.retrievalHit()) st[0]++;
            }
        }
        sb.append("## Retrieval hit rate (items with expected knowledge-base entries)\n\n| Input style | Hits | Items | Rate |\n|---|---|---|---|\n");
        for (Map.Entry<String, int[]> e : byStyle.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" | ")
                    .append(pct(e.getValue())).append(" |\n");
        }
        sb.append("| **overall** | ").append(hits[0]).append(" | ").append(hits[1]).append(" | ").append(pct(hits)).append(" |\n\n");
        sb.append("## Full pass (retrieval, kind, key facts, forbidden strings, reply language, GAP-no-number)\n\n| Category | Pass | Items |\n|---|---|---|\n");
        for (Map.Entry<String, int[]> e : byCat.entrySet()) {
            sb.append("| ").append(e.getKey()).append(" | ").append(e.getValue()[0]).append(" | ").append(e.getValue()[1]).append(" |\n");
        }
        sb.append("| **all** | ").append(pass[0]).append(" | ").append(pass[1]).append(" |\n\n");
        sb.append("## Failures\n\n");
        int n = 0;
        for (Result r : results) {
            if (r.pass()) continue;
            n++;
            sb.append("- ").append(r.item().id()).append(" [").append(r.item().style()).append("/").append(r.item().category()).append("] `")
                    .append(r.item().question().replace('\n', ' ')).append("`: ").append(String.join("; ", r.problems())).append("\n");
        }
        if (n == 0) sb.append("None.\n");
        return sb.toString();
    }

    private static String pct(int[] hv) {
        return hv[1] == 0 ? "n/a" : String.format(Locale.ROOT, "%.1f %%", 100.0 * hv[0] / hv[1]);
    }

    public static Map<String, Double> hitRateByStyle(List<Result> results) {
        Map<String, int[]> m = new LinkedHashMap<>();
        for (Result r : results) {
            if (!r.isRetrievalItem()) continue;
            int[] c = m.computeIfAbsent(r.item().style(), k -> new int[2]);
            c[1]++;
            if (r.retrievalHit()) c[0]++;
        }
        Map<String, Double> out = new LinkedHashMap<>();
        m.forEach((k, v) -> out.put(k, (double) v[0] / v[1]));
        return out;
    }

    public static void main(String[] args) throws IOException {
        Services s = build(NO_LLM, new AiProperties());
        List<Result> res = run(s, load(args.length > 0 ? args[0] : RESOURCE));
        System.out.println(markdown(res));
    }
}

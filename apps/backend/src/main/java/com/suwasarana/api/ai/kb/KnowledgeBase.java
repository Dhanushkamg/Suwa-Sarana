package com.suwasarana.api.ai.kb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.AiProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The versioned eligibility knowledge base ({@code kb/nbts-eligibility.v1.json}), loaded once at start-up.
 * It is the single source of truth for the numeric eligibility thresholds (see {@link EligibilityFacts}).
 * <p>
 * Answers may contain {@code {{name}}} placeholders. They are resolved from the JSON {@code facts} block and from the
 * configured NBTS contact details; an unresolved placeholder fails the start-up (so a typo cannot reach a donor).
 */
@Component
public class KnowledgeBase {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    public static final List<String> LANGS = List.of("en", "si", "ta");

    private final String kbVersion;
    private final String lastUpdated;
    private final String reviewStatus;
    private final Map<String, String> facts;
    private final List<KbEntry> entries;
    private final Map<String, KbEntry> byId;
    private final Map<String, String> variables;

    @Autowired
    public KnowledgeBase(AiProperties props) {
        this(props.getKb().getPath(), contactVariables(props), new ObjectMapper());
    }

    /** Test/utility constructor. */
    public KnowledgeBase(String resourcePath, Map<String, String> contactVariables, ObjectMapper mapper) {
        JsonNode root = readResource(resourcePath, mapper);
        this.kbVersion = root.path("kbVersion").asText("unknown");
        this.lastUpdated = root.path("lastUpdated").asText("");
        this.reviewStatus = root.path("reviewStatus").asText("");

        Map<String, String> f = new LinkedHashMap<>();
        root.path("facts").fields().forEachRemaining(e -> f.put(e.getKey(), e.getValue().asText()));
        this.facts = Collections.unmodifiableMap(f);

        Map<String, String> vars = new LinkedHashMap<>(f);
        vars.putAll(contactVariables);
        this.variables = Collections.unmodifiableMap(vars);

        List<KbEntry> list = new ArrayList<>();
        for (JsonNode n : root.path("entries")) {
            list.add(parseEntry(n));
        }
        this.entries = Collections.unmodifiableList(list);
        Map<String, KbEntry> idx = new LinkedHashMap<>();
        for (KbEntry e : list) {
            if (idx.put(e.id(), e) != null) throw new IllegalStateException("Duplicate KB entry id " + e.id());
        }
        this.byId = Collections.unmodifiableMap(idx);
        for (KbEntry e : list) {
            for (String c : e.companions()) requireKnown(e, c);
            for (String c : e.related()) requireKnown(e, c);
        }
        log.info("Knowledge base {} loaded: {} entries (last updated {})", kbVersion, entries.size(), lastUpdated);
    }

    /** Variables for {{placeholders}} that come from configuration rather than from the KB facts block. */
    public static Map<String, String> contactVariables(AiProperties props) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("nbtsPhone", props.getContacts().getNbtsPhone());
        v.put("nbtsEmail", props.getContacts().getNbtsEmail());
        v.put("nbtsAddress", props.getContacts().getNbtsAddress());
        v.put("nbtsWebsite", props.getContacts().getNbtsWebsite());
        String em = props.getFaq().getEmergencyNumber();
        v.put("emergencyNumber", em == null ? "" : em.trim());
        return v;
    }

    private static JsonNode readResource(String path, ObjectMapper mapper) {
        try (InputStream in = KnowledgeBase.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) throw new IllegalStateException("Knowledge base resource not found: " + path);
            return mapper.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read knowledge base " + path, e);
        }
    }

    private void requireKnown(KbEntry from, String id) {
        if (!byId.containsKey(id)) {
            throw new IllegalStateException("KB entry " + from.id() + " refers to unknown entry " + id);
        }
    }

    private KbEntry parseEntry(JsonNode n) {
        String id = text(n, "id");
        KbStatus status;
        try {
            status = KbStatus.valueOf(text(n, "status").toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("KB entry " + id + " has an invalid status", ex);
        }
        Map<String, List<String>> keywords = stringLists(n.path("keywords"));
        Map<String, List<String>> examples = stringLists(n.path("question_examples"));
        Map<String, String> answers = new LinkedHashMap<>();
        for (String lang : LANGS) {
            String raw = text(n, "answer_" + lang);
            answers.put(lang, resolve(raw, id, lang));
        }
        JsonNode src = n.path("source");
        if (src.isMissingNode() || src.path("url").asText("").isBlank()) {
            throw new IllegalStateException("KB entry " + id + " has no source url");
        }
        KbSource source = new KbSource(src.path("title").asText(), src.path("url").asText());
        JsonNode sec = n.path("secondarySource");
        KbSource secondary = sec.isMissingNode() ? null : new KbSource(sec.path("title").asText(), sec.path("url").asText());
        Map<String, Boolean> draft = new LinkedHashMap<>();
        draft.put("si", n.path("needsHumanReview_si").asBoolean(true));
        draft.put("ta", n.path("needsHumanReview_ta").asBoolean(true));
        return new KbEntry(id, text(n, "topic"), status, n.path("category").asText(id),
                keywords, examples, Collections.unmodifiableMap(answers), source, secondary,
                LocalDate.parse(text(n, "lastReviewed")), n.path("confidence").asText("unknown"),
                n.path("needsHumanReview").asBoolean(false), n.path("reviewReason").isMissingNode() ? null : n.path("reviewReason").asText(),
                Collections.unmodifiableMap(draft), strings(n.path("companions")), strings(n.path("related")));
    }

    /** Replaces {{placeholders}}; fails when one is unknown. */
    public String resolve(String raw, String ownerId, String lang) {
        Matcher m = PLACEHOLDER.matcher(raw);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String key = m.group(1);
            String val = variables.get(key);
            if (val == null) {
                throw new IllegalStateException("Unresolved placeholder {{" + key + "}} in " + ownerId + " (" + lang + ")");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String text(JsonNode n, String field) {
        String v = n.path(field).asText("");
        if (v.isBlank()) {
            throw new IllegalStateException("KB entry " + n.path("id").asText("?") + " is missing '" + field + "'");
        }
        return v;
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        for (JsonNode x : arr) out.add(x.asText());
        return Collections.unmodifiableList(out);
    }

    private static Map<String, List<String>> stringLists(JsonNode obj) {
        Map<String, List<String>> out = new LinkedHashMap<>();
        obj.fields().forEachRemaining(e -> out.put(e.getKey(), strings(e.getValue())));
        return Collections.unmodifiableMap(out);
    }

    public List<KbEntry> entries() { return entries; }
    public Optional<KbEntry> byId(String id) { return Optional.ofNullable(byId.get(id)); }
    public String version() { return kbVersion; }
    public String lastUpdated() { return lastUpdated; }
    public String reviewStatus() { return reviewStatus; }
    public Map<String, String> facts() { return facts; }
    public Map<String, String> variables() { return variables; }
}

package com.suwasarana.api.ai.poster;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.AiProperties;
import com.suwasarana.api.ai.geo.GeoData;
import com.suwasarana.api.ai.llm.LlmClient;
import com.suwasarana.api.ai.llm.LlmException;
import com.suwasarana.api.ai.llm.OutputGuard;
import com.suwasarana.api.ai.retrieval.TextUtil;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Poster copy for social media. Facts (blood type, hospital, dates, phone numbers) are structured fields that are only
 * ever substituted into text; they are NEVER given to the language model as something to "remember". The model may only
 * write a short headline/body/caption around {{placeholders}}; the result is validated (required placeholders present,
 * no digits, links, phone numbers or place names of its own, no scare language, length limits, right script) and any
 * failure or an unavailable model returns the deterministic localised template.
 */
@Service
public class PosterCopyService {

    private static final Logger log = LoggerFactory.getLogger(PosterCopyService.class);
    static final Set<String> FACT_KEYS = Set.of("bloodType", "units", "hospital", "district", "date", "time", "venue",
            "contactPhone", "campName", "organiser");
    private static final Pattern PATIENT_KEY = Pattern.compile("(?i).*(patient|victim|nic|photo|image|picture|fullname|full_name|firstname|lastname|surname|^name$|person|relative|child|mother|father|wife|husband|son|daughter).*");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");
    private static final Pattern LINKISH = Pattern.compile("(?i)https?://|www\\.|@|\\.com\\b|\\.lk\\b");
    private static final List<String> BLOOD_TYPES = List.of("A+", "A-", "B+", "B-", "AB+", "AB-", "O+", "O-");
    private static final Pattern PHONE = Pattern.compile("^(?:\\+94|0)\\d{9}$");
    private static final Pattern SCARE = Pattern.compile("(?i)(?<![\\p{L}])(die|dies|dying|death|dead|deadly|fatal|tragic|tragedy|horrific|horrible|shocking|desperate|desperately|beg|begging|panic|kill|killed|killing|or else|last chance|running out of time|blood on your hands)(?![\\p{L}])"
            + "|මරණ|මියය|இறப்பு|மரண|சாவு");
    private static final int MAX_HEADLINE = 80;
    private static final int MAX_BODY = 300;
    private static final int MAX_CAPTION = 320;

    private static final String SYSTEM_PROMPT = """
            You write short social-media poster copy that promotes voluntary blood donation in Sri Lanka.
            You are NOT given any facts. Write only around placeholders such as {{bloodType}}; the system fills them in later.
            RULES:
            1. Use ONLY these placeholders, written exactly like that: %s. Include every one of these: %s.
            2. Do not write any digit, date, time, phone number, web address, place name, hospital name or person name.
            3. Calm, respectful tone. No fear, guilt or pressure. No emojis. Never mention death or suffering.
            4. Language: %s. headline at most 8 words; body at most 2 short sentences; caption at most 2 short sentences.
            5. Template type: %s. %s
            Reply with ONE JSON object only: {"headline": "...", "body": "...", "caption": "..."}
            """;

    private final LlmClient llm;
    private final GeoData geo;
    private final UserRepository userRepository;
    private final AiProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Properties> bundles = new HashMap<>();

    public PosterCopyService(LlmClient llm, GeoData geo, UserRepository userRepository, AiProperties props) {
        this.llm = llm;
        this.geo = geo;
        this.userRepository = userRepository;
        this.props = props;
        for (String lang : List.of("en", "si", "ta")) bundles.put(lang, load(lang));
    }

    private static Properties load(String lang) {
        Properties p = new Properties();
        String res = "ai/poster-copy_" + lang + ".properties";
        try (InputStream in = PosterCopyService.class.getClassLoader().getResourceAsStream(res)) {
            if (in == null) throw new IllegalStateException("Missing resource " + res);
            p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + res, e);
        }
        return p;
    }

    // ------------------------------------------------------------------ entry point

    public PosterCopyResponseDto generate(Long userId, PosterCopyRequestDto req) {
        String template = req.getTemplate();
        String lang = req.getLanguage();
        Map<String, String> facts = validateFacts(req.getFacts());
        requireFacts(template, facts);
        if (template.equals("URGENT_REQUEST")) requireVerified(userId);

        PosterCopyResponseDto deterministic = deterministic(template, lang, facts);
        if (props.getGroq().isEnabled() && llm.isAvailable()) {
            PosterCopyResponseDto polished = tryModel(template, lang, facts, deterministic);
            if (polished != null) return polished;
        }
        return deterministic;
    }

    // ------------------------------------------------------------------ guardrails

    private void requireVerified(Long userId) {
        User u = userId == null ? null : userRepository.findById(userId).orElse(null);
        boolean ok = u != null && (u.getRole() == Role.ADMIN || u.getVerificationStatus() == VerificationStatus.VERIFIED);
        if (!ok) {
            throw new PosterException(HttpStatus.UNPROCESSABLE_ENTITY, "poster.unverified_requester",
                    "Only verified requesters can create an urgent request poster.");
        }
    }

    Map<String, String> validateFacts(Map<String, String> raw) {
        Map<String, String> out = new LinkedHashMap<>();
        if (raw == null) return out;
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            if (!FACT_KEYS.contains(key)) {
                if (PATIENT_KEY.matcher(key).matches()) {
                    throw new PosterException(HttpStatus.UNPROCESSABLE_ENTITY, "poster.patient_name_not_allowed",
                            "Patient names and personal details are never put on a poster.");
                }
                throw new PosterException(HttpStatus.BAD_REQUEST, "poster.unknown_fact", "Unknown fact '" + safeKey(key) + "'.");
            }
            String v = e.getValue() == null ? "" : e.getValue().replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
            if (v.isEmpty()) continue;
            if (v.length() > 120 || v.contains("<") || v.contains(">") || v.contains("{{")) {
                throw new PosterException(HttpStatus.BAD_REQUEST, "poster.invalid_fact", "Invalid value for '" + key + "'.");
            }
            out.put(key, normalise(key, v));
        }
        return out;
    }

    private static String safeKey(String k) {
        return k.length() > 30 ? k.substring(0, 30) : k.replaceAll("[^A-Za-z0-9_]", "?");
    }

    private String normalise(String key, String v) {
        try {
            switch (key) {
                case "bloodType" -> {
                    String t = v.toUpperCase(Locale.ROOT).replace(" ", "");
                    if (!BLOOD_TYPES.contains(t)) throw bad(key);
                    return t;
                }
                case "units" -> {
                    int n = Integer.parseInt(v);
                    if (n < 1 || n > 20) throw bad(key);
                    return String.valueOf(n);
                }
                case "district" -> {
                    return geo.exact(v).orElseThrow(() -> bad(key)).name();
                }
                case "date" -> {
                    return LocalDate.parse(v).toString();
                }
                case "time" -> {
                    return LocalTime.parse(v).toString();
                }
                case "contactPhone" -> {
                    String p = v.replaceAll("[\\s-]", "");
                    if (!PHONE.matcher(p).matches()) throw bad(key);
                    return p;
                }
                default -> {
                    if (v.replaceAll("\\D", "").length() >= 7) throw bad(key); // no phone numbers hidden in free-text fields
                    return v;
                }
            }
        } catch (NumberFormatException | DateTimeParseException e) {
            throw bad(key);
        }
    }

    private static PosterException bad(String key) {
        return new PosterException(HttpStatus.BAD_REQUEST, "poster.invalid_fact", "Invalid value for '" + key + "'.");
    }

    static List<String> required(String template) {
        return switch (template) {
            case "URGENT_REQUEST" -> List.of("bloodType", "units", "hospital", "district", "contactPhone");
            case "CAMP" -> List.of("campName", "date", "venue", "district");
            default -> List.of();
        };
    }

    private void requireFacts(String template, Map<String, String> facts) {
        List<String> missing = new ArrayList<>();
        for (String k : required(template)) if (!facts.containsKey(k)) missing.add(k);
        if (!missing.isEmpty()) {
            throw new PosterException(HttpStatus.BAD_REQUEST, "poster.missing_fact", "Missing facts: " + String.join(", ", missing));
        }
    }

    // ------------------------------------------------------------------ deterministic copy

    private static String key(String template) {
        return switch (template) {
            case "URGENT_REQUEST" -> "urgent";
            case "CAMP" -> "camp";
            case "AWARENESS" -> "awareness";
            default -> "thanks";
        };
    }

    PosterCopyResponseDto deterministic(String template, String lang, Map<String, String> facts) {
        Properties p = bundles.get(lang);
        String k = key(template);
        Map<String, String> vars = new HashMap<>(facts);
        if (facts.containsKey("district")) vars.put("districtTag", facts.get("district").replace(" ", ""));
        String headline = fill(p.getProperty(k + ".headline"), vars);
        String body = fill(p.getProperty(k + ".body"), vars);
        body += extraLines(p, k, facts, vars);
        String caption = fill(p.getProperty(k + ".caption"), vars);
        List<String> tags = new ArrayList<>();
        for (String t : p.getProperty(k + ".hashtags", "").split(",")) {
            if (t.isBlank()) continue;
            if (t.contains("{{") && !vars.containsKey("districtTag")) continue;
            tags.add(fill(t.trim(), vars));
        }
        return new PosterCopyResponseDto(headline, body, tags, caption, false);
    }

    /** Optional detail lines (date, time, contact ...) appended only for facts that were supplied. */
    private String extraLines(Properties p, String k, Map<String, String> facts, Map<String, String> vars) {
        StringBuilder sb = new StringBuilder();
        for (String fact : List.of("date", "time", "hospital", "organiser", "contactPhone")) {
            String line = p.getProperty(k + ".line." + fact);
            if (line != null && facts.containsKey(fact)) sb.append('\n').append(fill(line, vars));
        }
        return sb.toString();
    }

    private static String fill(String template, Map<String, String> vars) {
        if (template == null) return "";
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = vars.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : v));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    // ------------------------------------------------------------------ model-written copy

    private PosterCopyResponseDto tryModel(String template, String lang, Map<String, String> facts, PosterCopyResponseDto fallback) {
        List<String> allowed = new ArrayList<>();
        for (String k : List.of("bloodType", "units", "hospital", "district", "venue", "campName", "date", "organiser")) {
            if (!facts.containsKey(k)) continue;
            if (template.equals("URGENT_REQUEST") && k.equals("date")) continue; // appended automatically
            allowed.add(k);
        }
        List<String> mustUse = new ArrayList<>(required(template));
        mustUse.removeIf(k -> k.equals("contactPhone") || k.equals("units"));
        String system = SYSTEM_PROMPT.formatted(placeholders(allowed), placeholders(mustUse), languageName(lang), template, templateHint(template));
        try {
            LlmClient.LlmResult res = llm.complete(system, "Write the copy now.", 300, true, null);
            JsonNode n = mapper.readTree(res.text());
            String h = n.path("headline").asText("").trim();
            String b = n.path("body").asText("").trim();
            String c = n.path("caption").asText("").trim();
            String reason = validate(h, b, c, allowed, mustUse, lang);
            if (reason != null) {
                log.info("poster copy rejected reason={}", reason);
                return null;
            }
            Map<String, String> vars = new HashMap<>(facts);
            if (facts.containsKey("district")) vars.put("districtTag", facts.get("district").replace(" ", ""));
            String headline = fill(h, vars);
            String body = fill(b, vars) + extraLines(bundles.get(lang), key(template), facts, vars);
            String caption = fill(c, vars);
            if (headline.length() > MAX_HEADLINE || body.length() > MAX_BODY + 120 || caption.length() > MAX_CAPTION) {
                log.info("poster copy rejected reason=too-long-after-fill");
                return null;
            }
            return new PosterCopyResponseDto(headline, body, fallback.getHashtags(), caption, true);
        } catch (LlmException e) {
            log.info("poster copy model unavailable reason={}", e.getReason());
        } catch (IOException | RuntimeException e) {
            log.info("poster copy model output unreadable ({})", e.getClass().getSimpleName());
        }
        return null;
    }

    /** @return null when acceptable, otherwise a short reason code */
    String validate(String headline, String body, String caption, List<String> allowed, List<String> mustUse, String lang) {
        if (headline.isEmpty() || body.isEmpty() || caption.isEmpty()) return "empty";
        String all = headline + " " + body + " " + caption;
        Set<String> used = new java.util.HashSet<>();
        Matcher m = PLACEHOLDER.matcher(all);
        while (m.find()) {
            if (!allowed.contains(m.group(1))) return "unknown-placeholder:" + m.group(1);
            used.add(m.group(1));
        }
        Set<String> inHeadlineBody = new java.util.HashSet<>();
        Matcher hb = PLACEHOLDER.matcher(headline + " " + body);
        while (hb.find()) inHeadlineBody.add(hb.group(1));
        for (String r : mustUse) if (!inHeadlineBody.contains(r)) return "missing-placeholder:" + r;
        String bare = PLACEHOLDER.matcher(all).replaceAll(" ");
        if (bare.codePoints().anyMatch(Character::isDigit)) return "digit";
        if (LINKISH.matcher(bare).find()) return "link";
        if (SCARE.matcher(bare).find()) return "scare-language";
        if (bare.contains("{") || bare.contains("}") || bare.contains("<") || bare.contains(">")) return "markup";
        if (geo.findDistrictIn(bare).isPresent() || geo.findHospitalIn(bare).isPresent()) return "place-name";
        if (headline.length() > MAX_HEADLINE || body.length() > MAX_BODY || caption.length() > MAX_CAPTION) return "too-long";
        if (!OutputGuard.scriptMatches(bare, lang)) return "wrong-language";
        if (TextUtil.clean(bare).codePoints().anyMatch(cp -> Character.getType(cp) == Character.OTHER_SYMBOL)) return "symbol";
        return null;
    }

    private static String placeholders(List<String> keys) {
        if (keys.isEmpty()) return "(none)";
        return String.join(", ", keys.stream().map(k -> "{{" + k + "}}").toList());
    }

    private static String languageName(String lang) {
        return switch (lang) {
            case "si" -> "Sinhala";
            case "ta" -> "Tamil";
            default -> "English";
        };
    }

    private static String templateHint(String template) {
        return switch (template) {
            case "URGENT_REQUEST" -> "A verified request for blood at a hospital. Phone number, date and time are added by the system.";
            case "CAMP" -> "An invitation to a donation camp. Time, organiser and phone number are added by the system.";
            case "AWARENESS" -> "A general encouragement to give blood; no placeholders are needed.";
            default -> "A thank-you to blood donors; no placeholders are needed.";
        };
    }
}

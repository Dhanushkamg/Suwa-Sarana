package com.suwasarana.api.ai.retrieval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pattern-based safety triage of a user message, run before retrieval and before any LLM call: emergency symptoms,
 * prompt-injection attempts, abuse, greetings, thanks and account/app-support questions. The pattern lists live in
 * {@code kb/safety-patterns.json} (seed lists; native speakers must extend them).
 */
@Component
public class SafetyClassifier {

    public static final String RESOURCE = "kb/safety-patterns.json";
    private static final String LEFT = "(?<![\\p{L}\\p{M}\\p{N}])";
    private static final String RIGHT = "(?![\\p{L}\\p{M}\\p{N}])";
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.UNICODE_CHARACTER_CLASS;

    private final Pattern emergencyStrong;
    private final Pattern emergencyWeak;
    private final Pattern selfContext;
    private final Pattern injection;
    private final Pattern abuse;
    private final Pattern greeting;
    private final Pattern thanks;
    private final Pattern platform;

    public SafetyClassifier() {
        try (InputStream in = SafetyClassifier.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) throw new IllegalStateException("Missing resource " + RESOURCE);
            JsonNode root = new ObjectMapper().readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            emergencyStrong = compile(root.path("emergencyStrong"), false);
            emergencyWeak = compile(root.path("emergencyWeak"), false);
            selfContext = compile(root.path("selfContext"), false);
            injection = compile(root.path("injection"), false);
            abuse = compile(root.path("abuse"), false);
            greeting = compile(root.path("greeting"), true);
            thanks = compile(root.path("thanks"), true);
            platform = compile(root.path("platform"), false);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + RESOURCE, e);
        }
    }

    private static Pattern compile(JsonNode group, boolean anchoredAtStart) {
        List<String> alts = new ArrayList<>();
        for (JsonNode p : group.path("latin")) alts.add(LEFT + "(?:" + p.asText() + ")" + RIGHT);
        for (JsonNode p : group.path("script")) alts.add(LEFT + "(?:" + p.asText() + ")");
        String body = String.join("|", alts);
        return Pattern.compile(anchoredAtStart ? "^\\s*(?:" + body + ")" : body, FLAGS);
    }

    /** True for a symptom that needs urgent medical attention rather than an eligibility answer. */
    public boolean isEmergency(String text) {
        String t = TextUtil.clean(text);
        if (emergencyStrong.matcher(t).find()) return true;
        return emergencyWeak.matcher(t).find() && selfContext.matcher(t).find();
    }

    public boolean isInjection(String text) {
        return injection.matcher(TextUtil.clean(text)).find();
    }

    /** The text with every injection phrase removed (what is left may still be a legitimate question). */
    public String stripInjection(String text) {
        Matcher m = injection.matcher(TextUtil.clean(text));
        return m.replaceAll(" ");
    }

    public boolean isAbusive(String text) {
        return abuse.matcher(TextUtil.clean(text)).find();
    }

    /** A short message that opens with a greeting. */
    public boolean isGreeting(String text) {
        String t = TextUtil.clean(text).trim();
        return t.length() <= 40 && greeting.matcher(t).find();
    }

    public boolean isThanks(String text) {
        String t = TextUtil.clean(text).trim();
        return t.length() <= 60 && thanks.matcher(t).find();
    }

    public boolean isPlatformSupport(String text) {
        return platform.matcher(TextUtil.clean(text)).find();
    }
}

package com.suwasarana.api.ai.llm;

import com.suwasarana.api.ai.retrieval.TextUtil;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Validation of LLM output against the facts it was given. The model may only rephrase: every number, URL and e-mail in
 * the output must exist in the source text, every number of the source must survive, and the script of the output must
 * match the requested language. Anything else is rejected and the caller uses its deterministic text.
 */
public final class OutputGuard {

    private static final Pattern NUMBER = Pattern.compile("\\d+(?:[.,]\\d+)?");
    private static final Pattern LINK = Pattern.compile("(?i)https?://[^\\s)]+|[\\w.+-]+@[\\w-]+\\.[\\w.-]+|www\\.[^\\s)]+");

    private OutputGuard() {
    }

    /** Digit sequences with a decimal part normalised to a dot ("12,5" gives "12.5"). */
    public static Set<String> numbers(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(text);
        while (m.find()) out.add(m.group().replace(',', '.'));
        return out;
    }

    public static Set<String> links(String text) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = LINK.matcher(text);
        while (m.find()) out.add(stripTrailingPunct(m.group()).toLowerCase());
        return out;
    }

    private static String stripTrailingPunct(String s) {
        int end = s.length();
        while (end > 0 && ".,;:!?)".indexOf(s.charAt(end - 1)) >= 0) end--;
        return s.substring(0, end);
    }

    /**
     * Whether {@code output} is an acceptable rephrasing of {@code source}.
     *
     * @return null when acceptable, otherwise a short reason code (never user text)
     */
    public static String rejectionReason(String output, String source, String lang) {
        if (output == null || output.isBlank()) return "empty";
        if (output.contains("<") || output.contains(">")) return "markup";
        if (output.length() > Math.max(700, source.length() * 2)) return "too-long";
        Set<String> srcNumbers = numbers(source);
        Set<String> outNumbers = numbers(output);
        if (!srcNumbers.containsAll(outNumbers)) return "invented-number";
        if (!outNumbers.containsAll(srcNumbers)) return "dropped-number";
        Set<String> srcLinks = links(source);
        Set<String> outLinks = links(output);
        if (!srcLinks.containsAll(outLinks)) return "invented-link";
        if (!outLinks.containsAll(srcLinks)) return "dropped-link";
        if (!scriptMatches(output, lang)) return "wrong-language";
        return null;
    }

    /** Script check: Sinhala/Tamil replies must be mostly in that script, English replies must contain none of them. */
    public static boolean scriptMatches(String text, String lang) {
        int[] c = TextUtil.letterCounts(TextUtil.clean(text));
        int total = c[0] + c[1] + c[2];
        if (total == 0) return false;
        return switch (lang) {
            case "si" -> (double) c[0] / total >= 0.5;
            case "ta" -> (double) c[1] / total >= 0.5;
            default -> c[0] + c[1] == 0;
        };
    }
}

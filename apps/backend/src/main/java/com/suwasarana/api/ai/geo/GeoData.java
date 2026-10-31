package com.suwasarana.api.ai.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.retrieval.TextUtil;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The 25 official Sri Lanka districts (English, Sinhala, Tamil names, approximate centroid) and a table of well-known
 * hospitals mapped to their district. Used by request intake and the poster guard. District coordinates are
 * APPROXIMATE (centroids); hospital coordinates are deliberately not stored because they were not verified.
 */
@Component
public class GeoData {

    /** An official district. Coordinates are the district centroid. */
    public record District(String name, String si, String ta, double latitude, double longitude, List<String> aliases) {
    }

    /** A known hospital and its district. */
    public record Hospital(String name, String district, List<String> aliases) {
    }

    private final Map<String, District> districts = new LinkedHashMap<>();
    private final List<Hospital> hospitals = new ArrayList<>();
    private final Map<String, Pattern> patterns = new LinkedHashMap<>();

    public GeoData() {
        ObjectMapper m = new ObjectMapper();
        try {
            JsonNode d = read(m, "kb/sri-lanka-districts.json");
            for (JsonNode n : d.path("districts")) {
                List<String> aliases = new ArrayList<>();
                for (JsonNode a : n.path("aliases")) aliases.add(a.asText());
                District dist = new District(n.path("name").asText(), n.path("si").asText(), n.path("ta").asText(),
                        n.path("latitude").asDouble(), n.path("longitude").asDouble(), aliases);
                districts.put(dist.name(), dist);
            }
            JsonNode h = read(m, "kb/hospitals.json");
            for (JsonNode n : h.path("hospitals")) {
                List<String> aliases = new ArrayList<>();
                for (JsonNode a : n.path("aliases")) aliases.add(a.asText());
                hospitals.add(new Hospital(n.path("name").asText(), n.path("district").asText(), aliases));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read geo data", e);
        }
        if (districts.size() != 25) throw new IllegalStateException("Expected 25 districts, found " + districts.size());
        for (Hospital hp : hospitals) {
            if (!districts.containsKey(hp.district())) throw new IllegalStateException("Hospital " + hp.name() + " has unknown district");
        }
    }

    private static JsonNode read(ObjectMapper m, String resource) throws IOException {
        try (InputStream in = GeoData.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing " + resource);
            return m.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    public List<District> districts() {
        return Collections.unmodifiableList(new ArrayList<>(districts.values()));
    }

    /** The official district with this exact name (case-insensitive), or empty. Never guesses. */
    public Optional<District> exact(String name) {
        if (name == null) return Optional.empty();
        String n = name.trim();
        for (District d : districts.values()) {
            if (d.name().equalsIgnoreCase(n)) return Optional.of(d);
        }
        return Optional.empty();
    }

    /** First district named in the text (English, alias, Sinhala or Tamil), or empty. */
    public Optional<District> findDistrictIn(String text) {
        String t = TextUtil.clean(text);
        District best = null;
        int bestPos = Integer.MAX_VALUE;
        for (District d : districts.values()) {
            List<String> names = new ArrayList<>();
            names.add(d.name().toLowerCase(Locale.ROOT));
            for (String a : d.aliases()) names.add(a.toLowerCase(Locale.ROOT));
            for (String n : names) {
                int pos = indexOfWord(t, n, false);
                if (pos >= 0 && pos < bestPos) {
                    best = d;
                    bestPos = pos;
                }
            }
            for (String n : new String[]{TextUtil.clean(d.si()), TextUtil.clean(d.ta())}) {
                int pos = indexOfWord(t, n, true);
                if (pos >= 0 && pos < bestPos) {
                    best = d;
                    bestPos = pos;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** First well-known hospital named in the text, or empty. */
    public Optional<Hospital> findHospitalIn(String text) {
        String t = TextUtil.clean(text);
        Hospital best = null;
        int bestLen = 0;
        for (Hospital h : hospitals) {
            List<String> names = new ArrayList<>(h.aliases());
            names.add(h.name());
            for (String raw : names) {
                String n = TextUtil.clean(raw);
                if (n.length() > bestLen && indexOfWord(t, n, !isLatin(n)) >= 0) {
                    best = h;
                    bestLen = n.length();
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean isLatin(String s) {
        return s.codePoints().noneMatch(cp -> TextUtil.isSinhala(cp) || TextUtil.isTamil(cp));
    }

    /** Word-boundary search; script names allow a suffix (they are inflected), Latin names do not. */
    private int indexOfWord(String text, String needle, boolean allowSuffix) {
        if (needle.isEmpty()) return -1;
        Pattern p = patterns.computeIfAbsent((allowSuffix ? "s:" : "l:") + needle, k -> Pattern.compile(
                "(?<![\\p{L}\\p{M}\\p{N}])" + Pattern.quote(needle) + (allowSuffix ? "" : "(?![\\p{L}\\p{M}\\p{N}])")));
        var m = p.matcher(text);
        return m.find() ? m.start() : -1;
    }
}

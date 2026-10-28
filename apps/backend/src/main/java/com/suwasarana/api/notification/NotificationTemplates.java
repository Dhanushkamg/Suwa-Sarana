package com.suwasarana.api.notification;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Server-side text for the two places the server must produce a sentence itself: SMS (the user is not looking at
 * the app) and the English fallback shipped next to the keys. In-app text is localised by the client from the keys.
 *
 * Sinhala and Tamil strings here are first drafts that still need a native-speaker review.
 */
public final class NotificationTemplates {

    public static final String EN = "en";
    public static final String SI = "si";
    public static final String TA = "ta";

    // key -> language -> template with {placeholders}
    private static final Map<String, Map<String, String>> T = new HashMap<>();

    static {
        put("notifications.match.title", "Urgent blood request", "හදිසි රුධිර ඉල්ලීමක්", "அவசர இரத்தத் தேவை");
        put("notifications.match.body",
                "{bloodType} blood needed at {hospital} ({district}). Urgency: {urgency}.",
                "{hospital} ({district}) හි {bloodType} රුධිරය අවශ්‍යයි. ප්‍රමුඛතාව: {urgency}.",
                "{hospital} ({district}) இல் {bloodType} இரத்தம் தேவை. முன்னுரிமை: {urgency}.");
        put("notifications.sms.respond", "Open Suwa Sarana to respond.", "පිළිතුරු දීමට සුව සරණ විවෘත කරන්න.", "பதிலளிக்க சுவ சரணவைத் திறக்கவும்.");
        put("notifications.slot.confirmed.title", "Slot booked", "වේලාව වෙන්කරන ලදී", "நேரம் பதிவு செய்யப்பட்டது");
        put("notifications.slot.confirmed.body",
                "Your donation slot at {host} is confirmed for {start}.",
                "{host} හි ඔබේ රුධිර පරිත්‍යාග වේලාව {start} සඳහා තහවුරු විය.",
                "{host} இல் உங்கள் இரத்த தான நேரம் {start} க்கு உறுதிசெய்யப்பட்டது.");
        put("notifications.accepted.title", "A donor accepted", null, null);
        put("notifications.accepted.body", "A donor accepted your request at {hospital}. {accepted} of {needed} donors have accepted.", null, null);
        put("notifications.fulfilled.title", "Request fulfilled", null, null);
        put("notifications.fulfilled.body", "Your request at {hospital} is marked fulfilled. Thank you.", null, null);
        put("notifications.closed.title", "Request closed", null, null);
        put("notifications.closed.body", "The request at {hospital} has been closed. You do not need to go.", null, null);
        put("notifications.expired.title", "Request expired", null, null);
        put("notifications.expired.body", "Your request at {hospital} expired without being fulfilled.", null, null);
        put("notifications.reopened.title", "Request needs donors again", null, null);
        put("notifications.reopened.body", "The donor for your request at {hospital} did not confirm in time. We are looking for more donors.", null, null);
        put("notifications.lowStock.title", "Low blood stock", null, null);
        put("notifications.lowStock.body", "{bloodType} {component} stock is {status}: {units} units available.", null, null);
        put("notifications.pledge.title", "A friend pledged to donate", null, null);
        put("notifications.pledge.body", "{volunteerName} ({bloodType}) pledged through your private circle. {pledgeCount} pledge(s) so far.", null, null);
        put("notifications.generic.title", "{title}", null, null);
        put("notifications.generic.body", "{body}", null, null);
    }

    private NotificationTemplates() {}

    private static void put(String key, String en, String si, String ta) {
        Map<String, String> m = new HashMap<>();
        m.put(EN, en);
        if (si != null) m.put(SI, si);
        if (ta != null) m.put(TA, ta);
        T.put(key, m);
    }

    /** Normalises a stored language preference to en/si/ta (anything else is English). */
    public static String language(String raw) {
        if (raw == null) return EN;
        String l = raw.trim().toLowerCase(Locale.ROOT);
        if (l.startsWith("si")) return SI;
        if (l.startsWith("ta")) return TA;
        return EN;
    }

    /** Renders a template in {@code lang}, falling back to English, then to the key itself. */
    public static String render(String key, Map<String, Object> params, String lang) {
        Map<String, String> byLang = T.get(key);
        if (byLang == null) return key;
        String template = byLang.getOrDefault(language(lang), byLang.get(EN));
        if (params != null) {
            for (Map.Entry<String, Object> e : params.entrySet()) {
                template = template.replace("{" + e.getKey() + "}", String.valueOf(e.getValue()));
            }
        }
        return template;
    }

    /** Urgency word for the match SMS, localised. */
    public static String urgencyWord(String urgency, String lang) {
        String l = language(lang);
        if (urgency == null) return "";
        return switch (urgency) {
            case "CRITICAL" -> l.equals(SI) ? "අතිශය හදිසි" : l.equals(TA) ? "மிக அவசரம்" : "CRITICAL";
            case "URGENT" -> l.equals(SI) ? "හදිසි" : l.equals(TA) ? "அவசரம்" : "URGENT";
            default -> l.equals(SI) ? "සාමාන්‍ය" : l.equals(TA) ? "வழக்கமான" : "ROUTINE";
        };
    }
}

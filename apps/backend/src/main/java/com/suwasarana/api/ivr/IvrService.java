package com.suwasarana.api.ivr;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.RequestStatus;
import com.suwasarana.api.request.Urgency;
import com.suwasarana.api.user.VerificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Builds the TwiML the hotline answers with. Everything a caller can hear that comes from the database is limited to
 * requests that are active, critical, raised by a VERIFIED requester and not flagged by the fraud triage; free text is
 * XML-escaped.
 */
@Service
public class IvrService {

    /** Statuses a request must have to be read aloud. */
    private static final Set<RequestStatus> SPEAKABLE = Set.of(RequestStatus.OPEN, RequestStatus.ESCALATING);

    private final RequestRepository requestRepository;
    private final IvrProperties properties;
    private final IvrFacts facts;

    @Autowired
    public IvrService(RequestRepository requestRepository, IvrProperties properties, IvrFacts facts) {
        this.requestRepository = requestRepository;
        this.properties = properties;
        this.facts = facts;
    }

    /** Read-only transaction: the requester of each request is loaded lazily. */
    @Transactional(readOnly = true)
    public String respond(String digits) {
        if (digits == null || digits.trim().isEmpty()) {
            return menu();
        }
        switch (digits.trim()) {
            case "1":
                return say(criticalRequestsSpeech(), true);
            case "2":
                return say(facts.eligibilityScript(), true);
            case "3":
                return coordinator();
            default:
                return say("Invalid selection. Thank you for calling Suwa Sarana. Goodbye.", true);
        }
    }

    private String menu() {
        return prolog()
                + "    <Gather numDigits=\"1\" action=\"/api/ivr/webhook\" method=\"POST\">\n"
                + "        " + sayTag("Welcome to the Suwa Sarana Emergency Blood Hotline. "
                + "Press 1 to hear verified critical blood shortages. "
                + "Press 2 for donor eligibility guidelines. "
                + "Press 3 to reach the emergency coordinator.") + "\n"
                + "    </Gather>\n"
                + "    " + sayTag("We did not receive any input. Goodbye.") + "\n"
                + "    <Hangup/>\n"
                + "</Response>";
    }

    private String coordinator() {
        String number = properties.getCoordinatorNumber();
        if (number == null || !number.matches("^\\+?[0-9]{7,15}$")) {
            return say("The coordinator line is not available right now. Please try again later. Goodbye.", true);
        }
        return prolog()
                + "    " + sayTag("Connecting you to the National Blood Transfusion Service coordinator. Please hold.") + "\n"
                + "    <Dial>" + escapeXml(number) + "</Dial>\n"
                + "</Response>";
    }

    String criticalRequestsSpeech() {
        LocalDateTime now = LocalDateTime.now();
        List<BloodRequest> speakable = requestRepository.findActiveRequests().stream()
                .filter(r -> r.getUrgency() == Urgency.CRITICAL)
                .filter(r -> r.getStatus() == null || SPEAKABLE.contains(r.getStatus()))
                .filter(r -> r.getExpiresAt() == null || r.getExpiresAt().isAfter(now))
                .filter(r -> r.getFraudRiskScore() == null || r.getFraudRiskScore() <= properties.getMaxFraudRiskScore())
                .filter(r -> r.getRequester() != null && r.getRequester().getVerificationStatus() == VerificationStatus.VERIFIED)
                .sorted(Comparator.comparing(BloodRequest::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(properties.getMaxCriticalRequests())
                .toList();

        if (speakable.isEmpty()) {
            return "There are currently no verified critical blood requests. Thank you for your willingness to help. "
                    + "To speak to the coordinator, please call again and press 3.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("There ").append(speakable.size() == 1 ? "is currently one verified critical request: " : "are currently "
                + speakable.size() + " verified critical requests: ");
        for (BloodRequest req : speakable) {
            sb.append("Urgent need of ").append(req.getUnitsNeeded())
                    .append(req.getUnitsNeeded() == 1 ? " unit of " : " units of ")
                    .append(spokenBloodType(req.getPatientBloodType()))
                    .append(" blood at ").append(limit(req.getHospitalName(), 80))
                    .append(" in ").append(limit(req.getDistrict(), 40)).append(". ");
        }
        sb.append("Please log into the Suwa Sarana app to respond to these requests.");
        return sb.toString();
    }

    static String spokenBloodType(String type) {
        if (type == null) {
            return "unknown";
        }
        return type.replace("+", " positive").replace("-", " negative");
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String v = value.replaceAll("\\p{Cntrl}", " ").trim();
        return v.length() <= max ? v : v.substring(0, max);
    }

    private String say(String text, boolean hangup) {
        return prolog() + "    " + sayTag(text) + "\n" + (hangup ? "    <Hangup/>\n" : "") + "</Response>";
    }

    private String sayTag(String text) {
        return "<Say voice=\"" + escapeXml(properties.getVoice()) + "\" language=\"" + escapeXml(properties.getLanguage()) + "\">"
                + escapeXml(text) + "</Say>";
    }

    private static String prolog() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<Response>\n";
    }

    static String escapeXml(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

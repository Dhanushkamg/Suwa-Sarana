package com.suwasarana.api.ivr;

import com.suwasarana.api.request.BloodRequest;
import com.suwasarana.api.request.RequestRepository;
import com.suwasarana.api.request.Urgency;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ivr")
public class IvrController {

    @Autowired
    private RequestRepository requestRepository;

    @PostMapping(value = "/webhook", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleTwilioWebhook(
            @RequestParam(value = "Digits", required = false) String digits,
            @RequestParam(value = "From", required = false) String fromNumber) {
        
        String xml = processIvrInput(digits);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    @GetMapping(value = "/webhook", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> handleTwilioWebhookGet(
            @RequestParam(value = "Digits", required = false) String digits) {
        String xml = processIvrInput(digits);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_XML)
                .body(xml);
    }

    private String processIvrInput(String digits) {
        if (digits == null || digits.trim().isEmpty()) {
            return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                    + "<Response>\n"
                    + "    <Gather numDigits=\"1\" action=\"/api/ivr/webhook\" method=\"POST\">\n"
                    + "        <Say voice=\"Polly.Amy\" language=\"en-US\">"
                    + "Welcome to the Suwa Sarana Emergency Blood Hotline. "
                    + "Press 1 to hear active critical blood shortages. "
                    + "Press 2 for donor eligibility guidelines. "
                    + "Press 3 to reach the emergency coordinator."
                    + "</Say>\n"
                    + "    </Gather>\n"
                    + "    <Say voice=\"Polly.Amy\" language=\"en-US\">We did not receive any input. Goodbye.</Say>\n"
                    + "    <Hangup/>\n"
                    + "</Response>";
        }

        switch (digits.trim()) {
            case "1":
                List<BloodRequest> criticalRequests = requestRepository.findActiveRequests().stream()
                        .filter(r -> r.getUrgency() == Urgency.CRITICAL)
                        .limit(5)
                        .collect(Collectors.toList());

                StringBuilder sayContent = new StringBuilder();
                if (criticalRequests.isEmpty()) {
                    sayContent.append("There are currently no active critical blood requests in the system. Thank you for your willingness to help.");
                } else {
                    sayContent.append("There are currently ").append(criticalRequests.size()).append(" critical requests: ");
                    for (int i = 0; i < criticalRequests.size(); i++) {
                        BloodRequest req = criticalRequests.get(i);
                        sayContent.append("Urgent need of ")
                                .append(req.getUnitsNeeded()).append(" units of ")
                                .append(req.getPatientBloodType().replace("+", " positive").replace("-", " negative"))
                                .append(" blood at ").append(req.getHospitalName())
                                .append(" in ").append(req.getDistrict()).append(". ");
                    }
                    sayContent.append("Please log into the Suwa Sarana app to accept these requests.");
                }

                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Response>\n"
                        + "    <Say voice=\"Polly.Amy\" language=\"en-US\">" + escapeXml(sayContent.toString()) + "</Say>\n"
                        + "    <Hangup/>\n"
                        + "</Response>";

            case "2":
                String eligibility = "Under National Blood Transfusion Service guidelines: "
                        + "Donors must be between 18 and 60 years of age, weigh at least 50 kilograms, and feel well. "
                        + "You must wait 3 to 4 months between blood donations. "
                        + "Persons with recent tattoos or major surgeries must wait 12 months. Thank you for saving lives.";
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Response>\n"
                        + "    <Say voice=\"Polly.Amy\" language=\"en-US\">" + escapeXml(eligibility) + "</Say>\n"
                        + "    <Hangup/>\n"
                        + "</Response>";

            case "3":
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Response>\n"
                        + "    <Say voice=\"Polly.Amy\" language=\"en-US\">Connecting you to the National Blood Transfusion Service coordinator. Please hold.</Say>\n"
                        + "    <Dial>+94112369931</Dial>\n"
                        + "</Response>";

            default:
                return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                        + "<Response>\n"
                        + "    <Say voice=\"Polly.Amy\" language=\"en-US\">Invalid selection. Thank you for calling Suwa Sarana. Goodbye.</Say>\n"
                        + "    <Hangup/>\n"
                        + "</Response>";
        }
    }

    private String escapeXml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}

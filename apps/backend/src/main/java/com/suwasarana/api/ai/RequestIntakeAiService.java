package com.suwasarana.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.admin.AdminService;
import com.suwasarana.api.ai.dto.AiDraftRequestDto;
import com.suwasarana.api.ai.dto.AiDraftResponseDto;
import com.suwasarana.api.request.Urgency;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RequestIntakeAiService {

    private static final Logger log = LoggerFactory.getLogger(RequestIntakeAiService.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "llama-3.3-70b-versatile";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${groq.api.key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    public RequestIntakeAiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = new ObjectMapper();
    }

    public RequestIntakeAiService(ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.objectMapper = objectMapper;
    }

    public RequestIntakeAiService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public AiDraftResponseDto extractDraft(AiDraftRequestDto request) {
        String userText = request.getText();
        if (userText == null || userText.trim().isEmpty()) {
            AiDraftResponseDto res = new AiDraftResponseDto();
            res.setExtractedSuccessfully(false);
            res.setMessage("Input text cannot be empty.");
            return res;
        }

        // Attempt LLM Extraction if API key is present
        if (groqApiKey != null && !groqApiKey.trim().isEmpty()) {
            try {
                AiDraftResponseDto llmResult = callGroqLlm(userText, request.getLocale());
                if (llmResult != null) {
                    enrichCoordinates(llmResult);
                    llmResult.setExtractedSuccessfully(true);
                    llmResult.setMessage("AI extracted draft requisition successfully.");
                    return llmResult;
                }
            } catch (Exception e) {
                log.warn("Groq LLM extraction call failed or timed out: {}. Falling back to rule-based parser.", e.getMessage());
            }
        }

        // Fallback Rule-Based Parser (Deterministic NLP & regex for EN / SI / TA)
        AiDraftResponseDto fallback = ruleBasedExtraction(userText);
        enrichCoordinates(fallback);
        fallback.setExtractedSuccessfully(true);
        fallback.setMessage("Extracted draft requisition via local language parsing engine.");
        return fallback;
    }

    private AiDraftResponseDto callGroqLlm(String text, String locale) throws Exception {
        String systemPrompt = "You are Suwa Sarana's Emergency Blood Requisition Assistant for Sri Lanka hospitals.\n" +
                "Extract structured emergency blood requisition details from the user's natural language description in English, Sinhala (සිංහල), or Tamil (தமிழ்).\n" +
                "Respond ONLY with a valid JSON object matching this schema:\n" +
                "{\n" +
                "  \"patientBloodType\": \"A+|\"A-|\"B+|\"B-|\"AB+|\"AB-|\"O+|\"O-\" or null,\n" +
                "  \"unitsNeeded\": 1-10 or null,\n" +
                "  \"urgency\": \"ROUTINE\"|\"URGENT\"|\"CRITICAL\" or null,\n" +
                "  \"hospitalName\": string or null,\n" +
                "  \"district\": \"Colombo\"|\"Gampaha\"|\"Kalutara\"|\"Kandy\"|\"Matale\"|\"Nuwara Eliya\"|\"Galle\"|\"Matara\"|\"Hambantota\"|\"Jaffna\"|\"Kilinochchi\"|\"Mannar\"|\"Vavuniya\"|\"Mullaitivu\"|\"Batticaloa\"|\"Ampara\"|\"Trincomalee\"|\"Kurunegala\"|\"Puttalam\"|\"Anuradhapura\"|\"Polonnaruwa\"|\"Badulla\"|\"Monaragala\"|\"Ratnapura\"|\"Kegalle\" or null\n" +
                "}\n" +
                "CRITICAL INSTRUCTIONS:\n" +
                "1. If a field is not explicitly or clearly mentioned in the text, leave it null. NEVER hallucinate or guess a blood type.\n" +
                "2. Standardize district and hospital names to English even if input was in Sinhala or Tamil.\n" +
                "3. Return raw JSON without markdown code blocks.";

        Map<String, Object> body = new HashMap<>();
        body.put("model", GROQ_MODEL);
        body.put("temperature", 0.1);
        body.put("response_format", Map.of("type", "json_object"));

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", text));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(GROQ_URL, HttpMethod.POST, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            if (!contentNode.isMissingNode()) {
                String rawJson = contentNode.asText();
                return parseLlmJson(rawJson);
            }
        }
        return null;
    }

    private AiDraftResponseDto parseLlmJson(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            AiDraftResponseDto dto = new AiDraftResponseDto();

            if (node.hasNonNull("patientBloodType")) {
                String bt = node.get("patientBloodType").asText().trim().toUpperCase();
                if (isValidBloodType(bt)) {
                    dto.setPatientBloodType(bt);
                }
            }

            if (node.hasNonNull("unitsNeeded")) {
                int units = node.get("unitsNeeded").asInt(1);
                dto.setUnitsNeeded((short) Math.max(1, Math.min(10, units)));
            }

            if (node.hasNonNull("urgency")) {
                String urg = node.get("urgency").asText().trim().toUpperCase();
                try {
                    dto.setUrgency(Urgency.valueOf(urg));
                } catch (IllegalArgumentException ignored) {
                    dto.setUrgency(Urgency.URGENT);
                }
            }

            if (node.hasNonNull("hospitalName")) {
                dto.setHospitalName(node.get("hospitalName").asText().trim());
            }

            if (node.hasNonNull("district")) {
                String dist = node.get("district").asText().trim();
                dto.setDistrict(normalizeDistrict(dist));
            }

            return dto;
        } catch (Exception e) {
            log.warn("Failed to parse LLM JSON response: {}", json, e);
            return null;
        }
    }

    public AiDraftResponseDto ruleBasedExtraction(String text) {
        AiDraftResponseDto dto = new AiDraftResponseDto();

        // 1. Blood Type Extraction
        Pattern btPattern = Pattern.compile("(?i)\\b(A|B|AB|O)[\\s]*[\\+\\-]|\\b(A|B|AB|O)[\\s]*(positive|negative|pos|neg)\\b");
        Matcher btMatcher = btPattern.matcher(text);
        if (btMatcher.find()) {
            String match = btMatcher.group(0).toUpperCase().replaceAll("\\s+", "");
            match = match.replace("POSITIVE", "+").replace("POS", "+")
                    .replace("NEGATIVE", "-").replace("NEG", "-");
            if (isValidBloodType(match)) {
                dto.setPatientBloodType(match);
            }
        }

        // 2. Units extraction
        Pattern unitPattern = Pattern.compile("(?i)(?:units?|යුනිට්|யூனிட்)[\\s:]*(\\d+)|(\\d+)\\s*(?:units?|bottles?|packs?|ක්|යුනිට්|யூனிட்)");
        Matcher unitMatcher = unitPattern.matcher(text);
        if (unitMatcher.find()) {
            try {
                String digitStr = unitMatcher.group(1) != null ? unitMatcher.group(1) : unitMatcher.group(2);
                short u = Short.parseShort(digitStr);
                dto.setUnitsNeeded((short) Math.max(1, Math.min(10, u)));
            } catch (NumberFormatException ignored) {}
        } else {
            dto.setUnitsNeeded((short) 1);
        }

        // 3. Urgency extraction (EN, SI, TA)
        String lower = text.toLowerCase();
        if (lower.contains("critical") || lower.contains("icu") || lower.contains("emergency") ||
                lower.contains("හදිසි") || lower.contains("අසාධ්‍ය") || lower.contains("அவசர") || lower.contains("தீவிர")) {
            dto.setUrgency(Urgency.CRITICAL);
        } else if (lower.contains("routine") || lower.contains("planned") || lower.contains("සාමාන්‍ය") || lower.contains("வழக்கமான")) {
            dto.setUrgency(Urgency.ROUTINE);
        } else {
            dto.setUrgency(Urgency.URGENT);
        }

        // 4. District matching
        for (String district : AdminService.DISTRICT_COORDINATES.keySet()) {
            if (lower.contains(district.toLowerCase())) {
                dto.setDistrict(district);
                break;
            }
        }

        // 5. Hospital name extraction heuristic
        Pattern hospPattern = Pattern.compile("(?i)(?:at|in|hospital|රෝහල|വൈத்தியசாலை)\\s*[:\\-]?\\s*([A-Za-z\\s]+(?:Hospital|Teaching Hospital|General Hospital|Base Hospital|Clinic))");
        Matcher hospMatcher = hospPattern.matcher(text);
        if (hospMatcher.find()) {
            dto.setHospitalName(hospMatcher.group(1).trim());
        } else if (dto.getDistrict() != null && !dto.getDistrict().isEmpty()) {
            dto.setHospitalName(dto.getDistrict() + " General Hospital");
        }

        return dto;
    }

    private void enrichCoordinates(AiDraftResponseDto dto) {
        if (dto.getDistrict() != null && AdminService.DISTRICT_COORDINATES.containsKey(dto.getDistrict())) {
            double[] coords = AdminService.DISTRICT_COORDINATES.get(dto.getDistrict());
            dto.setLatitude(coords[0]);
            dto.setLongitude(coords[1]);
        } else {
            dto.setDistrict("Colombo");
            dto.setLatitude(6.9271);
            dto.setLongitude(79.8612);
        }
    }

    private boolean isValidBloodType(String bt) {
        return bt != null && (bt.equals("A+") || bt.equals("A-") || bt.equals("B+") || bt.equals("B-")
                || bt.equals("AB+") || bt.equals("AB-") || bt.equals("O+") || bt.equals("O-"));
    }

    private String normalizeDistrict(String district) {
        for (String valid : AdminService.DISTRICT_COORDINATES.keySet()) {
            if (valid.equalsIgnoreCase(district.trim())) {
                return valid;
            }
        }
        return "Colombo";
    }
}

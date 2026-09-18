package com.suwasarana.api.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.donor.DeferralRulesConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class DonorFaqAiService {

    private static final Logger log = LoggerFactory.getLogger(DonorFaqAiService.class);
    private static final String GROQ_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String GROQ_MODEL = "qwen/qwen3.8-27b";
    private static final String CLINICAL_DISCLAIMER = null;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final DeferralRulesConfig deferralRulesConfig;
    private String knowledgeBaseContent = "";

    @Value("${groq.api.key:${GROQ_API_KEY:}}")
    private String groqApiKey;

    public DonorFaqAiService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.objectMapper = new ObjectMapper();
        this.deferralRulesConfig = new DeferralRulesConfig();
        loadKnowledgeBase();
    }

    @Autowired
    public DonorFaqAiService(ObjectMapper objectMapper, DeferralRulesConfig deferralRulesConfig) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(6000);
        factory.setReadTimeout(10000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().add(0, new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8));
        this.objectMapper = objectMapper;
        this.deferralRulesConfig = deferralRulesConfig;
        loadKnowledgeBase();
    }

    private void loadKnowledgeBase() {
        try {
            java.io.InputStream is = this.getClass().getClassLoader().getResourceAsStream("faq-knowledge.txt");
            if (is != null) {
                this.knowledgeBaseContent = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            log.error("Failed to load knowledge base", e);
        }
    }

    public void setGroqApiKey(String groqApiKey) {
        this.groqApiKey = groqApiKey;
    }

    public DeferralRulesConfig getDeferralRulesConfig() {
        return deferralRulesConfig;
    }

    public FaqAnswerDto askQuestion(FaqQuestionDto request) {
        String question = request.getQuestion();
        String locale = request.getLocale() != null ? request.getLocale().toLowerCase() : "en";

        if (question == null || question.trim().isEmpty()) {
            return new FaqAnswerDto(
                    "",
                    "Please ask a question regarding blood donation eligibility.",
                    locale,
                    false,
                    CLINICAL_DISCLAIMER
            );
        }

        // 1. Attempt Groq LLM completion with grounded rule set
        if (groqApiKey != null && !groqApiKey.trim().isEmpty()) {
            try {
                String llmAnswer = callGroqFaq(question, locale);
                if (llmAnswer != null && !llmAnswer.trim().isEmpty()) {
                    return new FaqAnswerDto(
                            question,
                            llmAnswer.trim(),
                            locale,
                            true,
                            CLINICAL_DISCLAIMER
                    );
                }
            } catch (Exception e) {
                log.warn("Groq FAQ call failed or timed out: {}. Using rule-grounded fallback engine.", e.getMessage());
            }
        }

        // 2. Fallback Rule-Matching Engine (EN, SI, TA)
        String fallbackAnswer = getRuleGroundedAnswer(question, locale);
        return new FaqAnswerDto(
                question,
                fallbackAnswer,
                locale,
                true,
                CLINICAL_DISCLAIMER
        );
    }

    private String callGroqFaq(String question, String locale) throws Exception {
        String systemPrompt = "You are the official Suwa Sarana Blood Donation Assistant and System Support Agent in Sri Lanka.\n" +
                "Your role is to guide voluntary blood donors on eligibility, deferral criteria, donation safety, AND to help users with questions or technical issues related to using the Suwa Sarana platform itself.\n\n" +
                "VERIFIED KNOWLEDGE BASE (100 Q&A):\n" +
                knowledgeBaseContent + "\n\n" +
                deferralRulesConfig.generateSystemPromptContext() + "\n\n" +
                "RESPONSE INSTRUCTIONS:\n" +
                "1. Answer strictly in the same language as the user's input (English, Sinhala, or Tamil). DO NOT answer in a different language. EXCEPTIONS: If the user asks a question in 'Singlish' (Sinhala words typed using the English alphabet), you MUST respond in native Sinhala script (සිංහල). Do NOT reply in Singlish or English.\n" +
                "2. If the user asks in Singlish, use this glossary to understand them: pachcha/pachchayak=tattoo, wayasa=age, bara=weight, wathura/bonna=drink water, kama/kanna=food/eat, le/denna=donate blood, kalin/passe=before/after, awulak=problem, diyawadiyawa=diabetes, pressure=blood pressure, beheth=medicine, leda=disease.\n" +
                "3. If the user greets you (e.g., Hi, Hello, Ayubowan, Vanakkam), respond politely in the same language.\n" +
                "4. ONLY reject questions if they are CLEARLY about unrelated topics like politics, sports, or cooking. NEVER reject a question that asks about medical conditions, diseases (like diyawadiyawa), age, weight, tattoos, medications, or donating blood ('le denna'). Treat ALL such questions as valid blood donation inquiries.\n" +
                "5. For blood donation questions, use the VERIFIED KNOWLEDGE BASE. If not in the knowledge base, use your general knowledge of Sri Lankan NBTS blood donation rules.\n" +
                "6. For questions about the Suwa Sarana platform (e.g., how to register, use features, report bugs, solve app errors), provide accurate, helpful technical support as a system assistant.\n" +
                "7. State exact deferral durations and thresholds for medical questions.\n" +
                "8. Keep your response concise (2-4 sentences max).\n" +
                "9. If uncertain about clinical questions, advise the donor to check with the Medical Officer at the blood bank.";

        Map<String, Object> body = new HashMap<>();
        body.put("model", GROQ_MODEL);
        body.put("temperature", 0.2);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", question));
        body.put("messages", messages);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(groqApiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.exchange(GROQ_URL, HttpMethod.POST, entity, String.class);

        log.info("Groq API Response Status: {}", response.getStatusCode());
        log.info("Groq API Response Body: {}", response.getBody());

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode contentNode = root.path("choices").get(0).path("message").path("content");
            if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                return contentNode.asText();
            }
        }
        return null;
    }

    public String getRuleGroundedAnswer(String question, String locale) {
        String q = question.toLowerCase();

        // Detect Singlish and override locale to Sinhala
        List<String> singlishKeywords = Arrays.asList(
                "kanna", "bonna", "kama", "wathura", "denna", "puluwanda", "wayasa", 
                "bara", "passe", "kalin", "pachcha", "oneda", "mata", "beema", "diyawadiyawa", "pressure", "beheth", "leda"
        );
        for (String keyword : singlishKeywords) {
            if (q.contains(keyword)) {
                locale = "si";
                break;
            }
        }

        // 1. Tattoos and Piercings
        if (q.contains("tattoo") || q.contains("piercing") || q.contains("පච්ච") || q.contains("பச்சை") || q.contains("pachcha")) {
            if ("si".equals(locale)) {
                return "ශ්‍රී ලංකා ජාතික රුධිර පාරවිලයන සේවයේ (NBTS) නීති අනුව, පච්චයක් (Tattoo) හෝ ශරීරය විදීමක් (Piercing) සිදුකළ දින සිට මාස " +
                        DeferralRulesConfig.TATTOO_PIERCING_DEFERRAL_MONTHS + "ක් (වසර 1ක්) සම්පූර්ණ වන තෙක් රුධිරය ලබාදීම තාවකාලිකව කල් තැබිය යුතුය.";
            } else if ("ta".equals(locale)) {
                return "இலங்கை தேசிய இரத்த மாற்ற சேவை (NBTS) விதிகளின்படி, பச்சை (Tattoo) குத்திய அல்லது உடலை துளையிட்ட நாளிலிருந்து " +
                        DeferralRulesConfig.TATTOO_PIERCING_DEFERRAL_MONTHS + " மாதங்கள் (1 வருடம்) நிறைவடையும் வரை நீங்கள் இரத்த தானம் செய்ய முடியாது.";
            } else {
                return "Under NBTS Sri Lanka guidelines, there is a mandatory temporary deferral of " +
                        DeferralRulesConfig.TATTOO_PIERCING_DEFERRAL_MONTHS + " months (1 year) from the date of getting a tattoo or body piercing before you can safely donate blood.";
            }
        }

        // 2. Age Limits
        if (q.contains("age") || q.contains("old") || q.contains("years") || q.contains("වයස") || q.contains("வயது") || q.contains("wayasa")) {
            if ("si".equals(locale)) {
                return "රුධිර පරිත්‍යාගශීලියෙකුගේ වයස අවුරුදු " + DeferralRulesConfig.MIN_DONOR_AGE + " සහ " + DeferralRulesConfig.MAX_DONOR_AGE + " අතර විය යුතුය.";
            } else if ("ta".equals(locale)) {
                return "இரத்த தானம் செய்பவரின் வயது " + DeferralRulesConfig.MIN_DONOR_AGE + " முதல் " + DeferralRulesConfig.MAX_DONOR_AGE + " வயதுக்குள் இருக்க வேண்டும்.";
            } else {
                return "Eligible blood donors in Sri Lanka must be between " +
                        DeferralRulesConfig.MIN_DONOR_AGE + " and " + DeferralRulesConfig.MAX_DONOR_AGE + " years of age.";
            }
        }

        // 3. Weight Limits
        if (q.contains("weight") || q.contains("heavy") || q.contains("kg") || q.contains("බර") || q.contains("எடை") || q.contains("bara")) {
            if ("si".equals(locale)) {
                return "රුධිරය ලබාදීම සඳහා අවම ශරීර බර කිලෝග්‍රෑම් " + DeferralRulesConfig.MIN_DONOR_WEIGHT_KG + "ක් විය යුතුය.";
            } else if ("ta".equals(locale)) {
                return "இரத்த தானம் செய்ய குறைந்தபட்ச உடல் எடை " + DeferralRulesConfig.MIN_DONOR_WEIGHT_KG + " கிலோவாக இருக்க வேண்டும்.";
            } else {
                return "Donors must weigh at least " + DeferralRulesConfig.MIN_DONOR_WEIGHT_KG + " kg to safely donate whole blood.";
            }
        }

        // 4. Donation Interval
        if (q.contains("often") || q.contains("interval") || q.contains("frequency") || q.contains("days") || q.contains("නැවත") || q.contains("மறுபடியும்") || q.contains("passe") || q.contains("kalin")) {
            if ("si".equals(locale)) {
                return "සම්පූර්ණ රුධිර පරිත්‍යාගයක් සිදු කිරීමෙන් පසු නැවත රුධිරය ලබාදීම සඳහා අවම වශයෙන් දින " +
                        DeferralRulesConfig.DONATION_INTERVAL_DAYS + "ක් (මාස 4ක්) විවේක ගත යුතුය.";
            } else if ("ta".equals(locale)) {
                return "ஒரு முழு இரத்த தானத்திற்குப் பிறகு மீண்டும் தானம் செய்ய குறைந்தது " +
                        DeferralRulesConfig.DONATION_INTERVAL_DAYS + " நாட்கள் (4 மாதங்கள்) இடைவெளி தேவை.";
            } else {
                return "The minimum safe interval between whole blood donations is " +
                        DeferralRulesConfig.DONATION_INTERVAL_DAYS + " days (4 months).";
            }
        }

        // 5. Alcohol
        if (q.contains("alcohol") || q.contains("beer") || q.contains("drink") || q.contains("මත්පැන්") || q.contains("மது") || q.contains("beema") || q.contains("biwwama") || q.contains("bonna")) {
            if ("si".equals(locale)) {
                return "රුධිරය පරිත්‍යාග කිරීමට පැය " + DeferralRulesConfig.ALCOHOL_DEFERRAL_HOURS + "කට පෙර මත්පැන් පානයෙන් වැළකී සිටිය යුතුය.";
            } else if ("ta".equals(locale)) {
                return "இரத்த தானம் செய்வதற்கு குறைந்தது " + DeferralRulesConfig.ALCOHOL_DEFERRAL_HOURS + " மணி நேரத்திற்கு முன் மது அருந்துவதை தவிர்க்க வேண்டும்.";
            } else {
                return "Please refrain from consuming alcohol for at least " +
                        DeferralRulesConfig.ALCOHOL_DEFERRAL_HOURS + " hours prior to blood donation.";
            }
        }

        // 6. Medical Conditions (Diabetes, Pressure, etc.)
        if (q.contains("diyawadiyawa") || q.contains("pressure") || q.contains("beheth") || q.contains("leda") || q.contains("diabetes") || q.contains("disease") || q.contains("රෝග") || q.contains("දියවැඩියාව")) {
            if ("si".equals(locale)) {
                return "ඔබ දියවැඩියාව වැනි නිදන්ගත රෝග සඳහා ඖෂධ ලබා ගන්නේ නම්, කරුණාකර රුධිරය ලබාදීමට පෙර රුධිර බැංකුවේ වෛද්‍ය නිලධාරීවරයාගෙන් විමසන්න. එය ඔබගේ සෞඛ්‍ය තත්වය මත රඳා පවතී.";
            } else if ("ta".equals(locale)) {
                return "நீங்கள் நீரிழிவு போன்ற நாள்பட்ட நோய்களுக்கு மருந்து எடுத்துக்கொள்பவராக இருந்தால், இரத்த தானம் செய்வதற்கு முன் இரத்த வங்கி மருத்துவரை அணுகவும். இது உங்கள் சுகாதார நிலையைப் பொறுத்தது.";
            } else {
                return "If you are taking medication for chronic conditions like diabetes, please consult the Medical Officer at the blood bank before donating. Eligibility depends on your clinical health status.";
            }
        }

        // 6. Generic / Clinical fallback
        if ("si".equals(locale)) {
            return "සාමාන්‍ය NBTS මාර්ගෝපදේශ අනුව ඔබ යහපත් සෞඛ්‍ය තත්වයෙන් සිටිය යුතුය. ඔබගේ නිශ්චිත වෛද්‍ය තත්වය හෝ ඖෂධ භාවිතය පිළිබඳව කරුණාකර රුධිර බැංකුවේ වෛද්‍ය නිලධාරීවරයාගෙන් විමසන්න.";
        } else if ("ta".equals(locale)) {
            return "NBTS வழிகாட்டுதல்களின்படி நீங்கள் நல்ல ஆரோக்கியத்துடன் இருக்க வேண்டும். உங்கள் குறிப்பிட்ட மருத்துவ நிலை அல்லது மருந்துகள் குறித்து இரத்த வங்கி மருத்துவரிடம் ஆலோசிக்கவும்.";
        } else {
            return "According to NBTS guidelines, you must be in good general health. For specific medications or medical conditions, please consult the Medical Officer at your nearest blood bank for clinical clearance.";
        }
    }
}

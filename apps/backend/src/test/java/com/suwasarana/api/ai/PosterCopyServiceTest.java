package com.suwasarana.api.ai;

import com.suwasarana.api.ai.eval.ChatbotEvaluator;
import com.suwasarana.api.ai.geo.GeoData;
import com.suwasarana.api.ai.llm.LlmClient;
import com.suwasarana.api.ai.llm.LlmException;
import com.suwasarana.api.ai.poster.PosterCopyRequestDto;
import com.suwasarana.api.ai.poster.PosterCopyResponseDto;
import com.suwasarana.api.ai.poster.PosterCopyService;
import com.suwasarana.api.ai.poster.PosterException;
import com.suwasarana.api.user.Role;
import com.suwasarana.api.user.User;
import com.suwasarana.api.user.UserRepository;
import com.suwasarana.api.user.VerificationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PosterCopyServiceTest {

    private UserRepository users;
    private final GeoData geo = new GeoData();

    @BeforeEach
    void setUp() {
        users = Mockito.mock(UserRepository.class);
        User verified = new User();
        verified.setRole(Role.HOSPITAL_REQUESTER);
        verified.setVerificationStatus(VerificationStatus.VERIFIED);
        User pending = new User();
        pending.setRole(Role.REQUESTER);
        pending.setVerificationStatus(VerificationStatus.PENDING);
        Mockito.when(users.findById(1L)).thenReturn(Optional.of(verified));
        Mockito.when(users.findById(2L)).thenReturn(Optional.of(pending));
    }

    private PosterCopyService service(LlmClient llm) {
        return new PosterCopyService(llm, geo, users, new AiProperties());
    }

    private static PosterCopyRequestDto req(String template, String lang, Map<String, String> facts) {
        PosterCopyRequestDto r = new PosterCopyRequestDto();
        r.setTemplate(template);
        r.setLanguage(lang);
        r.setFacts(new LinkedHashMap<>(facts));
        return r;
    }

    private static Map<String, String> urgentFacts() {
        Map<String, String> f = new LinkedHashMap<>();
        f.put("bloodType", "O+");
        f.put("units", "3");
        f.put("hospital", "Karapitiya Teaching Hospital");
        f.put("district", "galle");
        f.put("contactPhone", "077 123 4567");
        f.put("date", "2026-11-02");
        return f;
    }

    private static final class FakeLlm implements LlmClient {
        final String json;
        final List<String> systems = new ArrayList<>();

        FakeLlm(String json) {
            this.json = json;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public LlmResult complete(String s, String u, int m, boolean j, String model) throws LlmException {
            systems.add(s);
            if (json == null) throw new LlmException(LlmException.Reason.BUDGET, "b");
            return new LlmResult(json, 10, 1);
        }
    }

    @Test
    @DisplayName("deterministic copy in en/si/ta contains exactly the supplied facts")
    void deterministic() {
        PosterCopyService s = service(ChatbotEvaluator.NO_LLM);
        for (String lang : new String[]{"en", "si", "ta"}) {
            PosterCopyResponseDto r = s.generate(1L, req("URGENT_REQUEST", lang, urgentFacts()));
            assertFalse(r.isUsedLlm());
            String all = r.getHeadline() + r.getBody() + r.getCaption();
            assertTrue(all.contains("O+") && all.contains("Karapitiya Teaching Hospital") && all.contains("Galle"), lang + all);
            assertTrue(r.getBody().contains("0771234567"), "phone normalised and added as a line");
            assertTrue(r.getBody().contains("2026-11-02"));
            assertFalse(all.contains("{{"));
            assertFalse(r.getHashtags().isEmpty());
        }
        PosterCopyResponseDto camp = s.generate(1L, req("CAMP", "en", Map.of("campName", "City Drive", "date", "2026-12-01", "venue", "Town Hall", "district", "Kandy", "organiser", "Rotary")));
        assertTrue(camp.getHeadline().contains("City Drive") && camp.getBody().contains("Rotary"));
        assertTrue(s.generate(2L, req("AWARENESS", "si", Map.of())).getHeadline().length() > 3);
        assertTrue(s.generate(2L, req("THANK_YOU", "ta", Map.of())).getBody().length() > 3);
    }

    @Test
    @DisplayName("unverified requester cannot make an URGENT_REQUEST poster (422 poster.unverified_requester) but may make other templates")
    void unverified() {
        PosterCopyService s = service(ChatbotEvaluator.NO_LLM);
        PosterException e = assertThrows(PosterException.class, () -> s.generate(2L, req("URGENT_REQUEST", "en", urgentFacts())));
        assertEquals(422, e.getStatus().value());
        assertEquals("poster.unverified_requester", e.getCode());
        assertThrows(PosterException.class, () -> s.generate(null, req("URGENT_REQUEST", "en", urgentFacts())));
        s.generate(2L, req("AWARENESS", "en", Map.of()));
    }

    @Test
    @DisplayName("patient name and other personal fields are refused; unknown facts and bad values are rejected")
    void guards() {
        PosterCopyService s = service(ChatbotEvaluator.NO_LLM);
        for (String key : new String[]{"patientName", "patient_name", "name", "patient", "nic"}) {
            Map<String, String> f = urgentFacts();
            f.put(key, "Nimal Perera");
            PosterException e = assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", f)), key);
            assertEquals(422, e.getStatus().value());
            assertEquals("poster.patient_name_not_allowed", e.getCode());
        }
        Map<String, String> unknown = urgentFacts();
        unknown.put("favouriteColour", "red");
        assertEquals("poster.unknown_fact", assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", unknown))).getCode());
        Map<String, String> bad = urgentFacts();
        bad.put("bloodType", "Z+");
        assertEquals(400, assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", bad))).getStatus().value());
        Map<String, String> phone = urgentFacts();
        phone.put("hospital", "Call 0771234567 now");
        assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", phone)));
        Map<String, String> missing = urgentFacts();
        missing.remove("contactPhone");
        assertEquals("poster.missing_fact", assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", missing))).getCode());
        Map<String, String> district = urgentFacts();
        district.put("district", "Atlantis");
        assertThrows(PosterException.class, () -> s.generate(1L, req("URGENT_REQUEST", "en", district)));
    }

    @Test
    @DisplayName("a model rewrite around placeholders is accepted; facts are filled in by the system, never given to the model")
    void modelAccepted() {
        FakeLlm llm = new FakeLlm("{\"headline\":\"Help needed: {{bloodType}}\",\"body\":\"A patient at {{hospital}} in {{district}} needs {{bloodType}} blood.\",\"caption\":\"Please share and help if you can.\"}");
        PosterCopyResponseDto r = service(llm).generate(1L, req("URGENT_REQUEST", "en", urgentFacts()));
        assertTrue(r.isUsedLlm());
        assertEquals("Help needed: O+", r.getHeadline());
        assertTrue(r.getBody().contains("Karapitiya Teaching Hospital in Galle"));
        assertTrue(r.getBody().contains("0771234567"));
        String sys = llm.systems.get(0);
        assertFalse(sys.contains("Karapitiya") || sys.contains("0771234567") || sys.contains("Galle"), "facts must not be sent to the model");
        assertTrue(sys.contains("{{bloodType}}"));
    }

    @Test
    @DisplayName("any invalid model output (digits, phone, link, place, scare words, missing placeholder, wrong language) falls back to the template")
    void modelRejected() {
        String[] bad = {
                "{\"headline\":\"Need {{bloodType}} now\",\"body\":\"At {{hospital}}, {{district}}. Call 0112345678.\",\"caption\":\"Share\"}",
                "{\"headline\":\"Need {{bloodType}}\",\"body\":\"At {{hospital}}, {{district}}. Visit https://x.example\",\"caption\":\"Share\"}",
                "{\"headline\":\"Need {{bloodType}}\",\"body\":\"At {{hospital}} in Colombo, {{district}}.\",\"caption\":\"Share\"}",
                "{\"headline\":\"Need {{bloodType}}\",\"body\":\"A life is dying at {{hospital}}, {{district}}.\",\"caption\":\"Share\"}",
                "{\"headline\":\"Need blood\",\"body\":\"At the hospital.\",\"caption\":\"Share\"}",
                "{\"headline\":\"Need {{bloodType}}\",\"body\":\"At {{hospital}}, {{district}}, patient {{patientName}}.\",\"caption\":\"Share\"}",
                "{\"headline\":\"රුධිර {{bloodType}}\",\"body\":\"{{hospital}} {{district}}\",\"caption\":\"x\"}",
                "{\"headline\":\"Need {{bloodType}}\",\"body\":\"At {{hospital}}, {{district}}. 3 units needed.\",\"caption\":\"Share\"}",
                "{\"headline\":\"" + "word ".repeat(40) + "{{bloodType}}\",\"body\":\"At {{hospital}}, {{district}}.\",\"caption\":\"Share\"}",
                "not json at all"};
        for (String b : bad) {
            PosterCopyResponseDto r = service(new FakeLlm(b)).generate(1L, req("URGENT_REQUEST", "en", urgentFacts()));
            assertFalse(r.isUsedLlm(), b);
            assertTrue(r.getHeadline().startsWith("URGENT: O+"), b);
        }
        PosterCopyResponseDto budget = service(new FakeLlm(null)).generate(1L, req("URGENT_REQUEST", "en", urgentFacts()));
        assertFalse(budget.isUsedLlm());
    }
}

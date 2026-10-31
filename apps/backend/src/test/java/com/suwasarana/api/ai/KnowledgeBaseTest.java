package com.suwasarana.api.ai;

import com.suwasarana.api.ai.kb.EligibilityFacts;
import com.suwasarana.api.ai.kb.KbEntry;
import com.suwasarana.api.ai.kb.KbMessages;
import com.suwasarana.api.ai.kb.KbStatus;
import com.suwasarana.api.ai.kb.KnowledgeBase;
import com.suwasarana.api.ai.reply.SinhalaRomaniser;
import com.suwasarana.api.ai.retrieval.LanguageDetector;
import com.suwasarana.api.ai.retrieval.LanguageDetector.InputStyle;
import com.suwasarana.api.ai.retrieval.Lexicon;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeBaseTest {

    private static AiProperties props;
    private static KnowledgeBase kb;

    @BeforeAll
    static void init() {
        props = new AiProperties();
        kb = new KnowledgeBase(props);
    }

    @Test
    @DisplayName("every entry has en/si/ta answers, a source, keywords for the four styles and flagged si/ta drafts")
    void schema() {
        assertTrue(kb.entries().size() >= 29);
        for (KbEntry e : kb.entries()) {
            for (String lang : new String[]{"en", "si", "ta"}) {
                assertFalse(e.answers().get(lang).isBlank(), e.id() + " " + lang);
                assertFalse(e.answers().get(lang).contains("{{"), e.id() + " has an unresolved placeholder");
            }
            assertTrue(e.source().url().startsWith("https://"), e.id());
            for (String style : new String[]{"en", "si", "ta", "singlish"}) {
                assertFalse(e.keywords().getOrDefault(style, java.util.List.of()).isEmpty(), e.id() + " keywords " + style);
            }
            assertTrue(e.translationDraft().get("si") && e.translationDraft().get("ta"), e.id() + " si/ta must be flagged for review");
            assertTrue(e.lastReviewed().isBefore(LocalDate.now().plusDays(1)));
            if (e.status() != KbStatus.VERIFIED) assertTrue(e.needsHumanReview(), e.id());
        }
        assertFalse(kb.entries().stream().anyMatch(e -> e.id().contains("COMBINED")), "multi-intent retrieval replaces the combined entry");
    }

    @Test
    @DisplayName("GAP answers state no figure; the invented 'Raktha' app and old unsourced numbers are gone")
    void gapAnswersHaveNoFigures() {
        String contacts = props.getContacts().getNbtsPhone();
        for (KbEntry e : kb.entries()) {
            for (String lang : new String[]{"en", "si", "ta"}) {
                String a = e.answer(lang);
                assertFalse(a.toLowerCase().contains("raktha"), e.id());
                if (e.status() == KbStatus.GAP) {
                    String stripped = a.replace(contacts, "").replace(props.getContacts().getNbtsEmail(), "");
                    assertFalse(stripped.chars().anyMatch(Character::isDigit), e.id() + " (" + lang + ") must not state a number: " + a);
                }
            }
        }
        String all = String.join(" ", kb.entries().stream().map(e -> e.answer("en")).toList());
        assertFalse(all.contains("12 months") || all.contains("24 hours") || all.contains("7 days") || all.contains("120 days"));
    }

    @Test
    @DisplayName("conflict entries show the official value and say it must be confirmed")
    void conflictEntries() {
        for (String id : new String[]{"CONFLICT-FIRST-TIME-WEIGHT", "CONFLICT-MALARIA-2YR"}) {
            KbEntry e = kb.byId(id).orElseThrow();
            assertEquals(KbStatus.CONFLICT, e.status());
            assertTrue(e.answer("en").contains("must be confirmed"), id);
            assertTrue(e.secondarySource() != null && e.secondarySource().url().contains("themorning.lk"));
        }
        assertTrue(kb.byId("CONFLICT-FIRST-TIME-WEIGHT").orElseThrow().answer("en").contains("above 50 kg"));
        assertTrue(kb.byId("CONFLICT-MALARIA-2YR").orElseThrow().answer("en").contains("3 years"));
    }

    @Test
    @DisplayName("unresolved placeholders fail start-up")
    void unresolvedPlaceholder() {
        assertThrows(IllegalStateException.class, () -> kb.resolve("Call {{doesNotExist}}", "test", "en"));
    }

    @Test
    @DisplayName("EligibilityFacts exposes the verified thresholds with the official boundary semantics")
    void facts() {
        EligibilityFacts f = new EligibilityFacts(kb);
        assertEquals(18, f.getMinAge());
        assertEquals(60, f.getMaxAge());
        assertEquals(55, f.getFirstTimeMaxAge());
        assertEquals(0, f.getWeightKgAbove().compareTo(new BigDecimal("50")));
        assertEquals(0, f.getHaemoglobinGdlAbove().compareTo(new BigDecimal("12.5")));
        assertEquals(4, f.getDonationIntervalMonths());
        assertTrue(f.isAgeEligible(18, false) && f.isAgeEligible(60, false));
        assertFalse(f.isAgeEligible(17, false) || f.isAgeEligible(61, false));
        assertTrue(f.isAgeEligible(55, true));
        assertFalse(f.isAgeEligible(56, true));
        assertFalse(f.isWeightEligible(new BigDecimal("50")), "exactly 50 kg is not 'above 50 kg'");
        assertTrue(f.isWeightEligible(new BigDecimal("50.1")));
        assertFalse(f.isHaemoglobinEligible(new BigDecimal("12.5")));
        assertEquals(LocalDate.of(2027, 2, 28), f.nextEligibleDonationDate(LocalDate.of(2026, 10, 31)), "4 calendar months, not 120 days");
        // answers and facts cannot disagree: the numbers in the age answer come from the same facts
        assertTrue(kb.byId("ELIG-AGE").orElseThrow().answer("en").contains(f.getMinAge() + " and " + f.getMaxAge()));
    }

    @Test
    @DisplayName("fixed messages resolve their placeholders; contacts come from configuration, not code")
    void messages() {
        AiProperties p = new AiProperties();
        p.getContacts().setNbtsPhone("+94 11 000 0000");
        p.getFaq().setEmergencyNumber("999");
        KnowledgeBase k2 = new KnowledgeBase(p);
        KbMessages m = new KbMessages(k2);
        assertTrue(m.get("noAnswer", "en").contains("+94 11 000 0000"));
        assertTrue(m.get("emergency", "si").contains("999"));
        assertTrue(k2.byId("GAP-MEDICATION").orElseThrow().answer("ta").contains("+94 11 000 0000"));
        for (String key : new String[]{"disclaimer", "confirmFooter", "noAnswer", "offTopic", "emergency", "injectionRefusal", "abuse", "greeting"}) {
            for (String lang : new String[]{"en", "si", "ta"}) assertFalse(m.get(key, lang).isBlank(), key + lang);
        }
    }

    @Test
    @DisplayName("lexicon: at least 300 forms, no form maps to two canonical tokens")
    void lexicon() {
        Lexicon lx = new Lexicon();
        assertTrue(lx.formCount() >= 300, "forms: " + lx.formCount());
        assertTrue(lx.conflicts().isEmpty(), lx.conflicts().toString());
        assertEquals("age", lx.lookup("wayasa"));
        assertEquals("age", lx.lookup("vayasa"));
        assertEquals("donate", lx.lookup("dhenna"));
    }

    @Test
    @DisplayName("language / style detection: English, Sinhala, Tamil, Singlish, Tanglish")
    void detection() {
        LanguageDetector d = new LanguageDetector(new Lexicon());
        assertEquals(InputStyle.EN, d.detect("What is the minimum age to donate blood?"));
        assertEquals(InputStyle.SI, d.detect("රුධිර දානය කිරීමට අවම වයස කීයද?"));
        assertEquals(InputStyle.TA, d.detect("இரத்த தானம் செய்ய வயது என்ன?"));
        assertEquals(InputStyle.SINGLISH, d.detect("mama blood denna puluwanda tattoo ekak thiyenawa nam?"));
        assertEquals(InputStyle.SINGLISH, d.detect("blood denna wayasa keeyada"));
        assertEquals(InputStyle.TANGLISH, d.detect("naan ratham kodukkalama enakku sugar irukku"));
        assertEquals(InputStyle.SI, d.detect("මට tattoo එකක් තියෙනවා, can I donate?"));
        assertEquals(InputStyle.EN, d.detect("My haemoglobin (හීමොග්ලොබින්) is low. Can I donate?"));
        assertEquals(InputStyle.UNKNOWN, d.detect("12345 ???"));
    }

    @Test
    @DisplayName("Sinhala romaniser produces readable Singlish")
    void romaniser() {
        assertEquals("rudhira daanaya", SinhalaRomaniser.romanise("රුධිර දානය"));
        assertEquals("wayasa 18", SinhalaRomaniser.romanise("වයස 18"));
        assertEquals("NBTS ok", SinhalaRomaniser.romanise("NBTS ok"));
        assertEquals("bara", SinhalaRomaniser.romanise("බර"));
    }
}

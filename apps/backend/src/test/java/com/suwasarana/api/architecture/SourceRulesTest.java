package com.suwasarana.api.architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cheap, source-level guard rails for rules that are easy to break by accident while several people work in parallel:
 * one clock abstraction (no zone-dependent "now()") and no personal data in log statements.
 */
class SourceRulesTest {

    private static final Path MAIN = Path.of("src/main/java");

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }

    /** Blanks out block and line comments (keeping line numbers) so rule texts in javadoc do not count as code. */
    private static String stripComments(String text) {
        Matcher block = Pattern.compile("(?s)/\\*.*?\\*/").matcher(text);
        StringBuilder out = new StringBuilder();
        while (block.find()) {
            block.appendReplacement(out, Matcher.quoteReplacement(block.group().replaceAll("[^\\n]", " ")));
        }
        block.appendTail(out);
        return out.toString().replaceAll("//[^\\n]*", "");
    }

    private static int lineOf(String text, int index) {
        return text.substring(0, index).split("\n", -1).length;
    }

    @Test
    @DisplayName("no zone-dependent LocalDateTime/LocalDate/LocalTime.now() without a Clock or an explicit zone")
    void noImplicitZoneNow() throws IOException {
        Pattern bad = Pattern.compile("\\b(LocalDateTime|LocalDate|LocalTime)\\.now\\(\\s*\\)");
        List<String> offenders = new ArrayList<>();
        for (Path p : sources()) {
            String text = Files.readString(p);
            // code only: comments (including javadoc that explains the rule) may mention the forbidden calls
            String code = stripComments(text);
            Matcher m = bad.matcher(code);
            while (m.find()) {
                offenders.add(MAIN.relativize(p) + ":" + lineOf(code, m.start()) + " " + m.group());
            }
        }
        assertTrue(offenders.isEmpty(), "use Times.utcNow(clock) / Times.colomboNow(clock) / Times.utcNow(): " + offenders);
    }

    @Test
    @DisplayName("log statements never print exception messages, response bodies, phone numbers or e-mail addresses")
    void noPersonalDataInLogs() throws IOException {
        Pattern statement = Pattern.compile("\\b(log|audit|accessLog|logger)\\.(trace|debug|info|warn|error)\\((.*?)\\);", Pattern.DOTALL);
        Pattern forbidden = Pattern.compile("getMessage\\(\\)|getBody\\(\\)|getPhoneNumber\\(\\)|getEmail\\(\\)|getOtp|\\bphoneNumber\\b|\\botp\\b|\\bpassword\\b");
        List<String> offenders = new ArrayList<>();
        for (Path p : sources()) {
            String text = Files.readString(p);
            Matcher m = statement.matcher(text);
            while (m.find()) {
                // a masked value is fine: PhoneNumbers.mask(...), maskEmail(...)
                String call = m.group(3).replaceAll("PhoneNumbers\\.mask\\([^)]*\\)", "").replaceAll("maskEmail\\([^)]*\\)", "");
                Matcher f = forbidden.matcher(call);
                if (f.find()) {
                    offenders.add(MAIN.relativize(p) + ":" + lineOf(text, m.start()) + " (" + f.group() + ")");
                }
            }
        }
        assertTrue(offenders.isEmpty(), "mask or drop: " + offenders);
    }
}

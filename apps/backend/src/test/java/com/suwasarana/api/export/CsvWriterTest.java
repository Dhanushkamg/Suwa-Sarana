package com.suwasarana.api.export;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvWriterTest {

    @Test
    void formulaLookingTextIsNeutralised() {
        assertEquals("'=HYPERLINK(\"http://evil\")", unquote(CsvWriter.cell("=HYPERLINK(\"http://evil\")")));
        assertEquals("'+94771234567", CsvWriter.cell("+94771234567"));
        assertEquals("'-2+3", CsvWriter.cell("-2+3"));
        assertEquals("'@SUM(A1:A2)", CsvWriter.cell("@SUM(A1:A2)"));
        assertEquals("'  =1+1", CsvWriter.cell("  =1+1"), "leading spaces do not hide a formula");
        assertEquals("'\tcmd", CsvWriter.cell("\tcmd"));
    }

    @Test
    void ordinaryTextAndNumbersAreUnchanged() {
        assertEquals("Kandy", CsvWriter.cell("Kandy"));
        assertEquals("42", CsvWriter.cell(42));
        assertEquals("2026-10-28", CsvWriter.cell("2026-10-28"));
        assertEquals("", CsvWriter.cell(null));
        assertEquals("", CsvWriter.cell(""));
        assertEquals("O+", CsvWriter.cell("O+"));
    }

    @Test
    void commasQuotesAndNewlinesAreQuoted() {
        assertEquals("\"Teaching Hospital, Kandy\"", CsvWriter.cell("Teaching Hospital, Kandy"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.cell("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvWriter.cell("line1\nline2"));
    }

    @Test
    void outputStartsWithUtf8BomAndUsesCrlf() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CsvWriter csv = new CsvWriter(bytes)) {
            csv.row(List.of("a", "b"));
            csv.row(List.of("කොළඹ", "கொழும்பு"));
        }
        byte[] out = bytes.toByteArray();
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, Arrays.copyOfRange(out, 0, 3));
        String text = new String(out, 3, out.length - 3, StandardCharsets.UTF_8);
        assertEquals("a,b\r\nකොළඹ,கொழும்பு\r\n", text);
    }

    @Test
    void aDangerousCellInsideARowIsStillNeutralised() throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (CsvWriter csv = new CsvWriter(bytes)) {
            csv.row(List.of(1, "=cmd|' /C calc'!A0", "Colombo"));
        }
        String text = new String(bytes.toByteArray(), 3, bytes.size() - 3, StandardCharsets.UTF_8);
        assertTrue(text.startsWith("1,'=cmd"), text);
    }

    /** cell() may wrap in quotes when the text has a comma or quote; strip them for the assertion on the prefix. */
    private static String unquote(String s) {
        return s.startsWith("\"") ? s.substring(1, s.length() - 1).replace("\"\"", "\"") : s;
    }
}

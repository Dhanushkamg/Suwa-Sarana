package com.suwasarana.api.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Minimal, safe CSV writer: UTF-8 with a byte-order mark (so Excel opens Sinhala and Tamil text correctly), CRLF
 * line ends, RFC 4180 quoting, and protection against CSV (formula) injection.
 *
 * <p><b>Injection protection.</b> A cell that a spreadsheet would read as a formula (it starts with {@code =},
 * {@code +}, {@code -}, {@code @}, tab or carriage return, ignoring leading spaces) is prefixed with a single quote
 * so it is shown as text. Hospital names and similar free text come from users, so every text cell goes through
 * {@link #cell(Object)}.
 */
public final class CsvWriter implements AutoCloseable {

    private static final byte[] BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};

    private final Writer out;

    public CsvWriter(OutputStream stream) throws IOException {
        stream.write(BOM);
        this.out = new OutputStreamWriter(stream, StandardCharsets.UTF_8);
    }

    /** Writes one row. */
    public void row(List<?> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(cell(cells.get(i)));
        }
        sb.append("\r\n");
        out.write(sb.toString());
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.flush();
    }

    /** Escapes one value for a CSV cell. Null becomes an empty cell. */
    public static String cell(Object value) {
        if (value == null) return "";
        String s = String.valueOf(value);
        if (s.isEmpty()) return "";
        if (isFormulaStart(s)) s = "'" + s;
        boolean needsQuotes = s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (needsQuotes) s = "\"" + s.replace("\"", "\"\"") + "\"";
        return s;
    }

    static boolean isFormulaStart(String s) {
        int i = 0;
        while (i < s.length() && s.charAt(i) == ' ') i++;
        if (i == s.length()) return false;
        char c = s.charAt(i);
        return c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r';
    }
}

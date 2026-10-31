package com.suwasarana.api.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A small dependency-free SMTP client (RFC 5321) so the application can send real mail without any extra library:
 * implicit TLS (port 465), STARTTLS (port 587, required when enabled) or plain (local MailHog/dev), with AUTH PLAIN.
 * Mail is sent as UTF-8 text/plain, base64-encoded, subject as an RFC 2047 encoded-word. It works with Amazon SES,
 * Gmail, Mailgun or any SMTP relay. If you prefer Spring's JavaMailSender, add spring-boot-starter-mail and swap
 * this bean in {@link EmailConfig}; nothing else changes because callers only know {@link EmailSender}.
 */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);
    private static final Pattern ADDRESS = Pattern.compile("^[^\\s<>\"@]+@[^\\s<>\"@]+\\.[^\\s<>\"@]+$");

    private final EmailProperties properties;

    public SmtpEmailSender(EmailProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean send(EmailMessage message) {
        EmailProperties.Smtp cfg = properties.getSmtp();
        String to = message == null ? null : message.to();
        if (to == null || !ADDRESS.matcher(to.trim()).matches()) {
            log.warn("Email not sent: invalid recipient");
            return false;
        }
        String fromHeader = properties.getFrom();
        String fromAddress = extractAddress(fromHeader);
        if (fromAddress == null) {
            log.error("Email not sent: EMAIL_FROM is not a valid address");
            return false;
        }

        Socket socket = null;
        try {
            socket = open(cfg);
            Session session = new Session(socket);
            session.expect(220);

            List<String> capabilities = session.ehlo();

            if (cfg.isStarttls() && !cfg.isSsl()) {
                if (capabilities.stream().noneMatch(c -> c.equalsIgnoreCase("STARTTLS"))) {
                    throw new IOException("server does not offer STARTTLS");
                }
                session.command("STARTTLS", 220);
                socket = upgrade(socket, cfg);
                session = new Session(socket);
                capabilities = session.ehlo();
            }

            if (!cfg.getUsername().isBlank()) {
                String token = "\0" + cfg.getUsername() + "\0" + cfg.getPassword();
                session.command("AUTH PLAIN " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8)), 235);
            }

            session.command("MAIL FROM:<" + fromAddress + ">", 250);
            session.command("RCPT TO:<" + to.trim() + ">", 250, 251);
            session.command("DATA", 354);
            session.writeRaw(buildMessage(fromHeader, to.trim(), message.subject(), message.body()) + "\r\n.\r\n");
            session.expect(250);
            try {
                session.command("QUIT", 221);
            } catch (IOException ignored) {
                // The message was accepted; a failing goodbye changes nothing.
            }
            return true;
        } catch (IOException | RuntimeException e) {
            log.warn("Email to {} failed ({})", LoggingEmailSender.maskEmail(to), e.getClass().getSimpleName());
            return false;
        } finally {
            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException ignored) {
                    // nothing to do
                }
            }
        }
    }

    private Socket open(EmailProperties.Smtp cfg) throws IOException {
        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(cfg.getHost(), cfg.getPort()), cfg.getConnectTimeoutMs());
        plain.setSoTimeout(cfg.getReadTimeoutMs());
        return cfg.isSsl() ? upgrade(plain, cfg) : plain;
    }

    private Socket upgrade(Socket plain, EmailProperties.Smtp cfg) throws IOException {
        SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault())
                .createSocket(plain, cfg.getHost(), cfg.getPort(), true);
        javax.net.ssl.SSLParameters params = ssl.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS"); // verify the server certificate matches the host
        ssl.setSSLParameters(params);
        ssl.setSoTimeout(cfg.getReadTimeoutMs());
        ssl.startHandshake();
        return ssl;
    }

    // ---- message ---------------------------------------------------------------------------------------------

    static String buildMessage(String from, String to, String subject, String body) {
        StringBuilder sb = new StringBuilder();
        sb.append("From: ").append(headerSafe(encodeDisplayName(from))).append("\r\n");
        sb.append("To: ").append(headerSafe(to)).append("\r\n");
        sb.append("Subject: ").append(encodeWord(headerSafe(subject))).append("\r\n");
        sb.append("Date: ").append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC))).append("\r\n");
        sb.append("Message-ID: <").append(UUID.randomUUID()).append("@suwasarana>\r\n");
        sb.append("MIME-Version: 1.0\r\n");
        sb.append("Content-Type: text/plain; charset=UTF-8\r\n");
        sb.append("Content-Transfer-Encoding: base64\r\n");
        sb.append("\r\n");
        String encoded = Base64.getEncoder().encodeToString((body == null ? "" : body).getBytes(StandardCharsets.UTF_8));
        for (int i = 0; i < encoded.length(); i += 76) {
            sb.append(encoded, i, Math.min(encoded.length(), i + 76)).append("\r\n");
        }
        return sb.toString();
    }

    /** Header values must never contain a line break (header injection). */
    static String headerSafe(String value) {
        return value == null ? "" : value.replace("\r", " ").replace("\n", " ").trim();
    }

    /** RFC 2047 encoded-word for non-ASCII text. */
    static String encodeWord(String text) {
        boolean ascii = text.chars().allMatch(c -> c < 128);
        if (ascii) {
            return text;
        }
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)) + "?=";
    }

    private static String encodeDisplayName(String from) {
        int lt = from.indexOf('<');
        if (lt <= 0) {
            return from;
        }
        String name = from.substring(0, lt).trim();
        return encodeWord(name) + " " + from.substring(lt);
    }

    /** "Suwa Sarana <no-reply@x.lk>" -> "no-reply@x.lk" */
    static String extractAddress(String from) {
        if (from == null) {
            return null;
        }
        int lt = from.indexOf('<');
        int gt = from.indexOf('>');
        String address = (lt >= 0 && gt > lt) ? from.substring(lt + 1, gt) : from;
        address = address.trim();
        return ADDRESS.matcher(address).matches() ? address : null;
    }

    // ---- protocol ---------------------------------------------------------------------------------------------

    private static final class Session {
        private final BufferedReader in;
        private final OutputStream out;

        Session(Socket socket) throws IOException {
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
            this.out = socket.getOutputStream();
        }

        List<String> ehlo() throws IOException {
            writeRaw("EHLO suwasarana.local\r\n");
            List<String> lines = readReply(250);
            List<String> caps = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) {
                caps.add(lines.get(i).substring(Math.min(4, lines.get(i).length())).trim().toUpperCase(Locale.ROOT));
            }
            return caps;
        }

        void command(String line, int... okCodes) throws IOException {
            writeRaw(line + "\r\n");
            readReply(okCodes);
        }

        void expect(int... okCodes) throws IOException {
            readReply(okCodes);
        }

        void writeRaw(String data) throws IOException {
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        private List<String> readReply(int... okCodes) throws IOException {
            List<String> lines = new ArrayList<>();
            while (true) {
                String line = in.readLine();
                if (line == null || line.length() < 3) {
                    throw new IOException("connection closed by server");
                }
                lines.add(line);
                if (line.length() == 3 || line.charAt(3) != '-') {
                    break;
                }
            }
            int code;
            try {
                code = Integer.parseInt(lines.get(lines.size() - 1).substring(0, 3));
            } catch (NumberFormatException e) {
                throw new IOException("malformed SMTP reply");
            }
            for (int ok : okCodes) {
                if (code == ok) {
                    return lines;
                }
            }
            throw new IOException("SMTP error " + code);
        }
    }
}

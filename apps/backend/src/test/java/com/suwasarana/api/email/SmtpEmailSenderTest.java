package com.suwasarana.api.email;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

/** The SMTP client against a tiny in-process SMTP server (no TLS): protocol sequence, AUTH, UTF-8 body, failures. */
class SmtpEmailSenderTest {

    private ServerSocket server;
    private Thread thread;
    private final List<String> commands = new CopyOnWriteArrayList<>();
    private volatile String data;
    private volatile String rcptReply = "250 OK";
    private volatile String dataEndReply = "250 queued";

    private EmailProperties properties;

    @BeforeEach
    void start() throws IOException {
        server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        thread = new Thread(this::serve, "fake-smtp");
        thread.setDaemon(true);
        thread.start();

        properties = new EmailProperties();
        properties.setFrom("Suwa Sarana <no-reply@suwasarana.lk>");
        properties.getSmtp().setHost("127.0.0.1");
        properties.getSmtp().setPort(server.getLocalPort());
        properties.getSmtp().setStarttls(false);
        properties.getSmtp().setReadTimeoutMs(2000);
        properties.getSmtp().setConnectTimeoutMs(1000);
    }

    @AfterEach
    void stop() throws IOException {
        server.close();
    }

    private void serve() {
        try (Socket socket = server.accept();
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));
             OutputStream out = socket.getOutputStream()) {
            reply(out, "220 fake ESMTP");
            String line;
            while ((line = in.readLine()) != null) {
                commands.add(line);
                String upper = line.toUpperCase();
                if (upper.startsWith("EHLO")) {
                    reply(out, "250-fake");
                    reply(out, "250-AUTH PLAIN");
                    reply(out, "250 8BITMIME");
                } else if (upper.startsWith("AUTH PLAIN")) {
                    reply(out, "235 authenticated");
                } else if (upper.startsWith("MAIL FROM")) {
                    reply(out, "250 OK");
                } else if (upper.startsWith("RCPT TO")) {
                    reply(out, rcptReply);
                } else if (upper.equals("DATA")) {
                    reply(out, "354 go ahead");
                    StringBuilder sb = new StringBuilder();
                    while ((line = in.readLine()) != null && !line.equals(".")) {
                        sb.append(line).append("\r\n");
                    }
                    data = sb.toString();
                    reply(out, dataEndReply);
                } else if (upper.equals("QUIT")) {
                    reply(out, "221 bye");
                    return;
                } else {
                    reply(out, "500 what");
                }
            }
        } catch (IOException ignored) {
            // the client hung up
        }
    }

    private static void reply(OutputStream out, String line) throws IOException {
        out.write((line + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    private static String decodeBody(String data) {
        String b64 = data.substring(data.indexOf("\r\n\r\n") + 4).replaceAll("\\s", "");
        return new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    @Test
    void sendsAUtf8MessageThroughTheFullSmtpDialogue() {
        String body = "Hello,\nOpen https://app.example/verify?token=abc\nසුව සරණ வணக்கம்";
        boolean sent = new SmtpEmailSender(properties).send(new EmailMessage("nimal@example.com", "Confirm your email සුව", body));

        assertTrue(sent);
        assertTrue(commands.get(0).startsWith("EHLO"));
        assertTrue(commands.contains("MAIL FROM:<no-reply@suwasarana.lk>"), commands.toString());
        assertTrue(commands.contains("RCPT TO:<nimal@example.com>"), commands.toString());
        assertTrue(commands.contains("DATA"));
        assertTrue(data.contains("To: nimal@example.com"));
        assertTrue(data.contains("Subject: =?UTF-8?B?"), "non-ASCII subjects are RFC 2047 encoded");
        assertTrue(data.contains("Content-Type: text/plain; charset=UTF-8"));
        assertEquals(body, decodeBody(data), "Sinhala and Tamil text arrives intact");
    }

    @Test
    void authenticatesWhenCredentialsAreConfigured() {
        properties.getSmtp().setUsername("AKIAXXXX");
        properties.getSmtp().setPassword("s3cret");

        assertTrue(new SmtpEmailSender(properties).send(new EmailMessage("a@example.com", "Hi", "x")));

        String auth = commands.stream().filter(c -> c.startsWith("AUTH PLAIN ")).findFirst().orElseThrow();
        String decoded = new String(Base64.getDecoder().decode(auth.substring("AUTH PLAIN ".length())), StandardCharsets.UTF_8);
        assertEquals("\0AKIAXXXX\0s3cret", decoded);
    }

    @Test
    void rejectedRecipient_isFalse() {
        rcptReply = "550 no such user";

        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("nobody@example.com", "Hi", "x")));
    }

    @Test
    void serverFailureAfterData_isFalse() {
        dataEndReply = "451 try later";

        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("a@example.com", "Hi", "x")));
    }

    @Test
    void unreachableServer_isFalse_notAnException() throws IOException {
        server.close();

        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("a@example.com", "Hi", "x")));
    }

    @Test
    void starttlsRequiredButNotOffered_refusesToSendInTheClear() {
        properties.getSmtp().setStarttls(true);

        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("a@example.com", "Hi", "secret body")));
        assertNull(data, "nothing was sent without encryption");
    }

    @Test
    void headerInjectionAndBadAddressesAreRefused() {
        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("a@example.com\r\nBcc: evil@example.com", "Hi", "x")));
        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage("not an address", "Hi", "x")));
        assertFalse(new SmtpEmailSender(properties).send(new EmailMessage(null, "Hi", "x")));
        assertTrue(commands.isEmpty() || commands.stream().noneMatch(c -> c.contains("evil@example.com")));
    }

    @Test
    void subjectLineBreaksCannotInjectHeaders() {
        String message = SmtpEmailSender.buildMessage("A <a@x.lk>", "b@x.lk", "Hi\r\nBcc: evil@x.lk", "body");

        assertFalse(message.contains("\r\nBcc:"));
        List<String> headers = new ArrayList<>(List.of(message.substring(0, message.indexOf("\r\n\r\n")).split("\r\n")));
        assertTrue(headers.stream().noneMatch(h -> h.startsWith("Bcc:")));
    }

    @Test
    void loggingSender_masksTheRecipient_andOnlyPrintsTheBodyInDev() {
        assertEquals("n*****@example.com", LoggingEmailSender.maskEmail("nimal@example.com"));
        assertTrue(new LoggingEmailSender().send(new EmailMessage("nimal@example.com", "s", "b")));
        assertFalse(new LoggingEmailSender().send(null));
    }
}

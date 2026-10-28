package com.suwasarana.api.notification.sms;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/** The Notify.lk client against a local mock HTTP server: request shape, success/failure mapping, timeouts, log hygiene. */
class NotifyLkSmsProviderTest {

    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final AtomicReference<String> lastContentType = new AtomicReference<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile int status = 200;
    private volatile String responseBody = "{\"status\":\"success\",\"data\":\"Message sent\"}";
    private volatile long delayMs = 0;

    private SmsProperties.NotifyLk config;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/send", exchange -> {
            calls.incrementAndGet();
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            lastContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            if (delayMs > 0) {
                try {
                    Thread.sleep(delayMs);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();

        config = new SmsProperties.NotifyLk();
        config.setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1/send");
        config.setUserId("12345");
        config.setApiKey("super-secret-key");
        config.setSenderId("SuwaSarana");
        config.setRequestTimeoutMs(1000);
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private Map<String, String> form(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            String[] kv = pair.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8), URLDecoder.decode(kv.length > 1 ? kv[1] : "", StandardCharsets.UTF_8));
        }
        return out;
    }

    @Test
    void success_sendsTheDocumentedFormFields_andReturnsTrue() {
        NotifyLkSmsProvider provider = new NotifyLkSmsProvider(config);

        assertTrue(provider.sendSms("+94771234567", "Your code is 123456 සුව සරණ"));

        assertEquals(1, calls.get());
        assertTrue(lastContentType.get().startsWith("application/x-www-form-urlencoded"));
        Map<String, String> f = form(lastBody.get());
        assertEquals("12345", f.get("user_id"));
        assertEquals("super-secret-key", f.get("api_key"));
        assertEquals("SuwaSarana", f.get("sender_id"));
        assertEquals("94771234567", f.get("to"), "international number without the plus sign");
        assertEquals("Your code is 123456 සුව සරණ", f.get("message"), "unicode survives");
    }

    @Test
    void localNumbersAreConvertedBeforeSending() {
        assertTrue(new NotifyLkSmsProvider(config).sendSms("0771234567", "hi"));
        assertEquals("94771234567", form(lastBody.get()).get("to"));
    }

    @Test
    void providerErrorStatus_isFalse() {
        responseBody = "{\"status\":\"error\",\"message\":\"Invalid API key\"}";

        assertFalse(new NotifyLkSmsProvider(config).sendSms("+94771234567", "hi"));
    }

    @Test
    void non200_isFalse_evenIfTheBodyLooksFine() {
        status = 500;

        assertFalse(new NotifyLkSmsProvider(config).sendSms("+94771234567", "hi"));
    }

    @Test
    void slowProvider_timesOut_andReturnsFalse() {
        delayMs = 3000;
        config.setRequestTimeoutMs(300);

        long started = System.currentTimeMillis();
        boolean sent = new NotifyLkSmsProvider(config).sendSms("+94771234567", "hi");

        assertFalse(sent);
        assertTrue(System.currentTimeMillis() - started < 2500, "gave up quickly");
    }

    @Test
    void unreachableProvider_isFalse_notAnException() {
        config.setBaseUrl("http://127.0.0.1:1/api/v1/send");
        config.setConnectTimeoutMs(300);

        assertFalse(new NotifyLkSmsProvider(config).sendSms("+94771234567", "hi"));
    }

    @Test
    void missingCredentials_orBadRecipient_sendNothing() {
        config.setApiKey("");
        assertFalse(new NotifyLkSmsProvider(config).sendSms("+94771234567", "hi"));
        config.setApiKey("k");
        assertFalse(new NotifyLkSmsProvider(config).sendSms("not-a-number", "hi"));
        assertFalse(new NotifyLkSmsProvider(config).sendSms("+94771234567", " "));
        assertEquals(0, calls.get());
    }

    @Test
    void logs_neverContainTheMessageOrTheApiKey_andThePhoneIsMasked() {
        Logger logger = (Logger) LoggerFactory.getLogger(NotifyLkSmsProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            status = 400;
            new NotifyLkSmsProvider(config).sendSms("+94771234567", "Your code is 987654");

            assertFalse(appender.list.isEmpty());
            for (ILoggingEvent e : appender.list) {
                String line = e.getFormattedMessage();
                assertFalse(line.contains("987654"), line);
                assertFalse(line.contains("super-secret-key"), line);
                assertFalse(line.contains("771234567"), line);
            }
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void loggingProvider_returnsTrue_masksThePhone_andHidesTheBodyUnlessAskedTo() {
        Logger logger = (Logger) LoggerFactory.getLogger(LoggingSmsProvider.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertTrue(new LoggingSmsProvider(false).sendSms("+94771234567", "code 111222"));
            assertTrue(new LoggingSmsProvider(true).sendSms("+94771234567", "code 333444"));
            assertFalse(new LoggingSmsProvider(true).sendSms("", "x"));

            String quiet = appender.list.get(0).getFormattedMessage();
            String dev = appender.list.get(1).getFormattedMessage();
            assertFalse(quiet.contains("111222"), quiet);
            assertTrue(dev.contains("333444"), dev);
            assertFalse(quiet.contains("771234567") || dev.contains("771234567"));
        } finally {
            logger.detachAppender(appender);
        }
    }
}

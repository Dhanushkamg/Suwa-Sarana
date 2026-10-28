package com.suwasarana.api.notification.sms;

import com.suwasarana.api.user.PhoneNumbers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Sends SMS through the Notify.lk REST API (POST form to {@code /api/v1/send}: user_id, api_key, sender_id, to, message).
 * A message counts as sent only when HTTP 200 comes back with {@code "status":"success"}; every other outcome
 * (network error, timeout, non-200, {@code "status":"error"}) returns {@code false}. The message body and the
 * API key are never logged; the recipient is masked.
 */
public class NotifyLkSmsProvider implements SmsProvider {

    private static final Logger log = LoggerFactory.getLogger(NotifyLkSmsProvider.class);
    private static final Pattern SUCCESS = Pattern.compile("\"status\"\\s*:\\s*\"success\"", Pattern.CASE_INSENSITIVE);

    private final SmsProperties.NotifyLk config;
    private final HttpClient client;

    public NotifyLkSmsProvider(SmsProperties.NotifyLk config) {
        this.config = config;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(config.getConnectTimeoutMs()))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public boolean sendSms(String recipientPhone, String message) {
        String to = toNotifyFormat(recipientPhone);
        if (to == null || message == null || message.isBlank()) {
            log.warn("SMS not sent: invalid recipient or empty message (recipient={})", PhoneNumbers.mask(recipientPhone));
            return false;
        }
        if (isBlank(config.getUserId()) || isBlank(config.getApiKey())) {
            log.error("SMS not sent: NOTIFYLK_USER_ID / NOTIFYLK_API_KEY are not configured");
            return false;
        }

        Map<String, String> form = new LinkedHashMap<>();
        form.put("user_id", config.getUserId());
        form.put("api_key", config.getApiKey());
        form.put("sender_id", config.getSenderId());
        form.put("to", to);
        form.put("message", message);
        String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));

        HttpRequest request = HttpRequest.newBuilder(URI.create(config.getBaseUrl()))
                .timeout(Duration.ofMillis(config.getRequestTimeoutMs()))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            boolean ok = response.statusCode() == 200 && response.body() != null && SUCCESS.matcher(response.body()).find();
            if (!ok) {
                log.warn("Notify.lk rejected the SMS to {} (HTTP {})", PhoneNumbers.mask(recipientPhone), response.statusCode());
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("SMS to {} interrupted", PhoneNumbers.mask(recipientPhone));
            return false;
        } catch (IOException | RuntimeException e) {
            log.warn("SMS to {} failed: {}", PhoneNumbers.mask(recipientPhone), e.getClass().getSimpleName());
            return false;
        }
    }

    /** Notify.lk wants the international number without "+": 94771234567. */
    static String toNotifyFormat(String phone) {
        String e164 = PhoneNumbers.normalize(phone);
        return e164 == null ? null : e164.substring(1);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}

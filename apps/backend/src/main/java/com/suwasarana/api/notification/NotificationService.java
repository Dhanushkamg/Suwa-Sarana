package com.suwasarana.api.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    
    // Map of UserId -> SseEmitter
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        // Timeout 30 minutes. The client should reconnect automatically.
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        log.info("User {} subscribed to SSE notifications", userId);
        return emitter;
    }

    public void sendNotification(Long userId, NotificationMessage message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name(message.getType())
                        .data(message));
                log.info("Sent SSE notification to user {}", userId);
            } catch (IOException e) {
                log.error("Error sending SSE notification to user {}. Removing emitter.", userId);
                emitters.remove(userId);
            }
        } else {
            // Here in a real application, you would fallback to push notifications (FCM) or SMS.
            log.info("User {} is not connected via SSE. Fallback to Push/SMS needed.", userId);
        }
    }
}

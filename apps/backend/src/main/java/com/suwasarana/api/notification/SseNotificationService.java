package com.suwasarana.api.notification;

import com.suwasarana.api.notification.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Live delivery over Server-Sent Events.
 *
 * <ul>
 *   <li>Several tabs/devices per user: each user has a SET of emitters, and every event goes to all of them.</li>
 *   <li>Every inbox event carries its notification id as the SSE event id, so a reconnecting client sends
 *       {@code Last-Event-ID} and {@link NotificationController} replays what it missed from the inbox table.</li>
 *   <li>A comment heartbeat every 20 seconds keeps proxies and load balancers from closing idle streams and lets the
 *       server notice dead connections (the failed write removes the emitter).</li>
 * </ul>
 *
 * <p><b>Multi-instance limit.</b> Emitters live in this JVM's memory. With more than one backend instance, an event
 * raised on instance A reaches only the streams connected to A. Nothing is lost (the inbox row is in Postgres and the
 * client replays on reconnect, and the SMS fallback covers users with no stream), but live delivery to a stream on
 * instance B waits for that client's next reconnect/poll. Closing the gap needs a shared bus (Postgres LISTEN/NOTIFY
 * or Redis pub/sub) and is deliberately not faked here.
 */
@Service
public class SseNotificationService {

    private static final Logger log = LoggerFactory.getLogger(SseNotificationService.class);

    /** Streams are recycled every 30 minutes; clients reconnect with Last-Event-ID. */
    static final long TIMEOUT_MS = 30 * 60 * 1000L;
    /** A user with more open streams than this loses the oldest (runaway tabs / reconnect storms). */
    static final int MAX_STREAMS_PER_USER = 6;

    // UserId -> every open stream of that user
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /** Opens a stream with no replay (legacy entry point). */
    public SseEmitter subscribe(Long userId) {
        return subscribe(userId, List.of());
    }

    /**
     * Opens a stream and first sends {@code replay} (missed inbox events, oldest first). The event id of each is the
     * notification id.
     */
    public SseEmitter subscribe(Long userId, List<NotificationDto> replay) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        Set<SseEmitter> userStreams = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        userStreams.add(emitter);
        trim(userId, userStreams);

        Runnable cleanup = () -> remove(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().reconnectTime(3000).comment("connected"));
            for (NotificationDto n : replay) {
                emitter.send(event(n));
            }
        } catch (IOException | IllegalStateException e) {
            cleanup.run();
        }
        log.info("User {} subscribed to SSE notifications ({} open streams, {} replayed)",
                userId, emitters.getOrDefault(userId, Set.of()).size(), replay.size());
        return emitter;
    }

    public boolean isConnected(Long userId) {
        Set<SseEmitter> s = emitters.get(userId);
        return s != null && !s.isEmpty();
    }

    public int connectionCount(Long userId) {
        Set<SseEmitter> s = emitters.get(userId);
        return s == null ? 0 : s.size();
    }

    /** Pushes an inbox notification to every open stream of the user. Returns how many streams accepted it. */
    public int push(Long userId, NotificationDto dto) {
        Set<SseEmitter> streams = emitters.get(userId);
        if (streams == null || streams.isEmpty()) return 0;
        int delivered = 0;
        for (SseEmitter emitter : streams) {
            try {
                emitter.send(event(dto));
                delivered++;
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
        return delivered;
    }

    /** Legacy API: sends a pre-built message (no id, not replayable). True if at least one stream got it. */
    public boolean sendNotificationIfConnected(Long userId, NotificationMessage message) {
        Set<SseEmitter> streams = emitters.get(userId);
        if (streams == null || streams.isEmpty()) return false;
        boolean sent = false;
        for (SseEmitter emitter : streams) {
            try {
                emitter.send(SseEmitter.event().name(message.getType()).data(message));
                sent = true;
            } catch (IOException | IllegalStateException e) {
                remove(userId, emitter);
            }
        }
        return sent;
    }

    /** Every 20 seconds: an SSE comment line on each stream. Dead streams fail the write and are dropped. */
    @Scheduled(fixedRate = 20_000, initialDelay = 20_000)
    public void heartbeat() {
        for (Map.Entry<Long, Set<SseEmitter>> e : emitters.entrySet()) {
            for (SseEmitter emitter : e.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException ex) {
                    remove(e.getKey(), emitter);
                }
            }
        }
    }

    private SseEmitter.SseEventBuilder event(NotificationDto n) {
        return SseEmitter.event().id(String.valueOf(n.getId())).name(n.getType()).data(n);
    }

    private void remove(Long userId, SseEmitter emitter) {
        Set<SseEmitter> s = emitters.get(userId);
        if (s == null) return;
        s.remove(emitter);
        if (s.isEmpty()) emitters.remove(userId, s);
    }

    private void trim(Long userId, Set<SseEmitter> streams) {
        while (streams.size() > MAX_STREAMS_PER_USER) {
            SseEmitter oldest = streams.iterator().next();
            streams.remove(oldest);
            try {
                oldest.complete();
            } catch (Exception ignored) {
                // already gone
            }
        }
    }
}

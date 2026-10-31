package com.suwasarana.api.notification;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.suwasarana.api.notification.dto.NotificationDto;
import com.suwasarana.api.notification.dto.NotificationPageDto;
import com.suwasarana.api.shared.AfterCommit;
import com.suwasarana.api.shared.AppClock;
import com.suwasarana.api.common.ApiException;
import com.suwasarana.api.common.ErrorCodes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The persistent inbox. Every notification the platform raises for a user is stored here first (keys + params);
 * live delivery (SSE) and SMS are best-effort extras on top, so a user who was offline still finds everything
 * when they open the app, and an SSE client that reconnects can replay what it missed from the last event id.
 */
@Service
public class NotificationInboxService {

    private static final Logger log = LoggerFactory.getLogger(NotificationInboxService.class);
    static final int DEFAULT_PAGE = 20;
    static final int MAX_PAGE = 100;
    static final int MAX_REPLAY = 100;

    private final UserNotificationRepository repository;
    private final SseNotificationService sse;
    private final ObjectMapper mapper;
    private final AppClock clock;

    @Autowired
    public NotificationInboxService(UserNotificationRepository repository, SseNotificationService sse,
                                    ObjectMapper mapper, AppClock clock) {
        this.repository = repository;
        this.sse = sse;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Stores the notification and pushes it to the user's open streams once the surrounding transaction commits. */
    @Transactional
    public NotificationDto add(Long userId, String type, String titleKey, String bodyKey,
                               Map<String, Object> params, Long referenceId) {
        NotificationDto dto = persist(userId, type, titleKey, bodyKey, params, referenceId);
        AfterCommit.run(() -> {
            try {
                sse.push(userId, dto);
            } catch (Exception e) {
                log.warn("SSE push failed for user {} ({})", userId, e.getClass().getSimpleName());
            }
        });
        return dto;
    }

    /** Stores without pushing: the caller decides how to deliver (used by the SSE-then-SMS cascade). */
    @Transactional
    public NotificationDto persist(Long userId, String type, String titleKey, String bodyKey,
                                   Map<String, Object> params, Long referenceId) {
        UserNotification n = new UserNotification();
        n.setUserId(userId);
        n.setType(type);
        n.setTitleKey(titleKey);
        n.setBodyKey(bodyKey);
        n.setParams(toJson(params));
        n.setReferenceId(referenceId);
        n.setCreatedAt(clock.instant());
        return toDto(repository.save(n));
    }

    /** Convenience for a pre-built {@link NotificationMessage}; titles/bodies without keys go under the generic keys. */
    @Transactional
    public NotificationDto persist(Long userId, NotificationMessage m) {
        if (m.getTitleKey() != null && m.getBodyKey() != null) {
            return persist(userId, m.getType(), m.getTitleKey(), m.getBodyKey(), m.getParams(), m.getReferenceId());
        }
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("title", m.getTitle());
        params.put("body", m.getBody());
        return persist(userId, m.getType(), "notifications.generic.title", "notifications.generic.body", params, m.getReferenceId());
    }

    @Transactional(readOnly = true)
    public NotificationPageDto page(Long userId, Long cursor, boolean unreadOnly, Integer limit) {
        int size = limit == null ? DEFAULT_PAGE : Math.max(1, Math.min(limit, MAX_PAGE));
        // Fetch one extra row to know whether another page exists
        PageRequest pr = PageRequest.of(0, size + 1);
        List<UserNotification> rows;
        if (unreadOnly) {
            rows = cursor == null
                    ? repository.findByUserIdAndReadAtIsNullOrderByIdDesc(userId, pr)
                    : repository.findByUserIdAndReadAtIsNullAndIdLessThanOrderByIdDesc(userId, cursor, pr);
        } else {
            rows = cursor == null
                    ? repository.findByUserIdOrderByIdDesc(userId, pr)
                    : repository.findByUserIdAndIdLessThanOrderByIdDesc(userId, cursor, pr);
        }
        boolean more = rows.size() > size;
        List<UserNotification> pageRows = more ? rows.subList(0, size) : rows;
        List<NotificationDto> items = pageRows.stream().map(this::toDto).toList();
        Long next = more ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new NotificationPageDto(items, next, repository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationDto markRead(Long userId, Long id) {
        UserNotification n = repository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound(ErrorCodes.NOTIFICATION_NOT_FOUND, "Notification not found"));
        if (n.getReadAt() == null) {
            n.setReadAt(clock.instant());
            repository.save(n);
        }
        return toDto(n);
    }

    @Transactional
    public int markAllRead(Long userId) {
        return repository.markAllRead(userId, clock.instant());
    }

    @Transactional(readOnly = true)
    public long unreadCount(Long userId) {
        return repository.countByUserIdAndReadAtIsNull(userId);
    }

    /** What an SSE client missed: everything newer than its Last-Event-ID, oldest first. */
    @Transactional(readOnly = true)
    public List<NotificationDto> replayAfter(Long userId, long lastEventId) {
        return repository.findByUserIdAndIdGreaterThanOrderByIdAsc(userId, lastEventId, PageRequest.of(0, MAX_REPLAY))
                .stream().map(this::toDto).toList();
    }

    // ------------------------------------------------------------------ mapping

    NotificationDto toDto(UserNotification n) {
        NotificationDto d = new NotificationDto();
        d.setId(n.getId());
        d.setType(n.getType());
        d.setTitleKey(n.getTitleKey());
        d.setBodyKey(n.getBodyKey());
        Map<String, Object> params = fromJson(n.getParams());
        d.setParams(params);
        d.setReferenceId(n.getReferenceId());
        d.setCreatedAt(n.getCreatedAt());
        d.setReadAt(n.getReadAt());
        d.setTitle(NotificationTemplates.render(n.getTitleKey(), params, NotificationTemplates.EN));
        d.setBody(NotificationTemplates.render(n.getBodyKey(), params, NotificationTemplates.EN));
        return d;
    }

    private String toJson(Map<String, Object> params) {
        try {
            return mapper.writeValueAsString(params == null ? Map.of() : params);
        } catch (Exception e) {
            log.warn("Could not serialise notification params ({})", e.getClass().getSimpleName());
            return "{}";
        }
    }

    private Map<String, Object> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return Map.of();
            return mapper.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {});
        } catch (Exception e) {
            return Map.of();
        }
    }
}

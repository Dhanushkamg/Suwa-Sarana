package com.suwasarana.api.notification;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.notification.dto.NotificationDto;
import com.suwasarana.api.notification.dto.NotificationPageDto;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Persistent inbox plus a live Server-Sent Events stream")
public class NotificationController {

    private final SseNotificationService sseNotificationService;
    private final NotificationInboxService inbox;

    @Autowired
    public NotificationController(SseNotificationService sseNotificationService, NotificationInboxService inbox) {
        this.sseNotificationService = sseNotificationService;
        this.inbox = inbox;
    }

    @Operation(summary = "Subscribe to live notification stream",
            description = "Server-Sent Events. Each event's id is the inbox notification id: send it back as Last-Event-ID when reconnecting "
                    + "and the missed notifications are replayed first. A comment heartbeat arrives every 20 seconds.")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@AuthenticationPrincipal UserDetailsImpl userDetails,
                                          @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        List<NotificationDto> replay = List.of();
        if (lastEventId != null && !lastEventId.isBlank()) {
            try {
                replay = inbox.replayAfter(userDetails.getId(), Long.parseLong(lastEventId.trim()));
            } catch (NumberFormatException ignored) {
                // not one of our ids: nothing to replay
            }
        }
        return sseNotificationService.subscribe(userDetails.getId(), replay);
    }

    @Operation(summary = "List inbox notifications", description = "Newest first, keyset-paged: pass nextCursor from the previous page as cursor. unread=true lists only unread.")
    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageDto>> list(
            @AuthenticationPrincipal UserDetailsImpl userDetails,
            @RequestParam(required = false) Long cursor,
            @RequestParam(defaultValue = "false") boolean unread,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(ApiResponse.success(inbox.page(userDetails.getId(), cursor, unread, limit)));
    }

    @Operation(summary = "Number of unread notifications")
    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> unreadCount(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("count", inbox.unreadCount(userDetails.getId()))));
    }

    @Operation(summary = "Mark one notification as read")
    @PostMapping("/{id}/read")
    public ResponseEntity<ApiResponse<NotificationDto>> markRead(@AuthenticationPrincipal UserDetailsImpl userDetails,
                                                                 @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(inbox.markRead(userDetails.getId(), id)));
    }

    @Operation(summary = "Mark every notification as read")
    @PostMapping("/read-all")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> markAllRead(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return ResponseEntity.ok(ApiResponse.success(Map.of("updated", inbox.markAllRead(userDetails.getId()))));
    }
}

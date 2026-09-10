package com.suwasarana.api.notification;

import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Real-time dispatch and match notifications via Server-Sent Events (SSE)")
public class NotificationController {

    private final SseNotificationService sseNotificationService;

    @Autowired
    public NotificationController(SseNotificationService sseNotificationService) {
        this.sseNotificationService = sseNotificationService;
    }

    @Operation(summary = "Subscribe to live notification stream", description = "Opens an HTTP Server-Sent Events (SSE) persistent connection for instant blood match alerts.")
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@AuthenticationPrincipal UserDetailsImpl userDetails) {
        return sseNotificationService.subscribe(userDetails.getId());
    }
}

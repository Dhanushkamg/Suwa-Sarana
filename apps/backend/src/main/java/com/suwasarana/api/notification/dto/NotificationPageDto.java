package com.suwasarana.api.notification.dto;

import java.util.List;

/** One page of the inbox, newest first. Pass {@code nextCursor} as {@code cursor} to get the following page. */
public class NotificationPageDto {

    private final List<NotificationDto> items;
    private final Long nextCursor;
    private final long unreadCount;

    public NotificationPageDto(List<NotificationDto> items, Long nextCursor, long unreadCount) {
        this.items = items;
        this.nextCursor = nextCursor;
        this.unreadCount = unreadCount;
    }

    public List<NotificationDto> getItems() { return items; }
    public Long getNextCursor() { return nextCursor; }
    public long getUnreadCount() { return unreadCount; }
}

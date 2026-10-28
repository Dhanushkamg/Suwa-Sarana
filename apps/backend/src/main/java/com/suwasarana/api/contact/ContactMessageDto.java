package com.suwasarana.api.contact;

import java.time.ZonedDateTime;

/** Admin-facing view of a stored contact message. */
public record ContactMessageDto(Long id, String name, String email, String subject, String message,
                                ContactStatus status, ZonedDateTime createdAt,
                                ZonedDateTime handledAt, String handledBy) {

    static ContactMessageDto from(ContactMessage m) {
        return new ContactMessageDto(m.getId(), m.getName(), m.getEmail(), m.getSubject(), m.getMessage(),
                m.getStatus(), m.getCreatedAt(), m.getHandledAt(), m.getHandledBy());
    }
}

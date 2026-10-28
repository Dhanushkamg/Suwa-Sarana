package com.suwasarana.api.email;

/** A plain-text email. {@code body} is UTF-8 (Sinhala/Tamil are fine). */
public record EmailMessage(String to, String subject, String body) {
}

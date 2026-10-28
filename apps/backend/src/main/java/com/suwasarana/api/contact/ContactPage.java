package com.suwasarana.api.contact;

import java.util.List;

/** A page of contact messages (own shape instead of Spring's Page, whose JSON form is not a stable contract). */
public record ContactPage(List<ContactMessageDto> items, int page, int size, long totalElements, int totalPages) {
}

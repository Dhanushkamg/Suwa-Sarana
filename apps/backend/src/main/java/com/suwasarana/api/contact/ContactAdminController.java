package com.suwasarana.api.contact;

import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/contact-messages")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Contact (admin)", description = "Inbox of messages sent through the public contact form")
public class ContactAdminController {

    private final ContactService contactService;

    @Autowired
    public ContactAdminController(ContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "List contact messages", description = "Newest first. Optional filter ?status=NEW|HANDLED; ?page (0-based) &size (max 100).")
    @GetMapping
    public ResponseEntity<ApiResponse<ContactPage>> list(
            @RequestParam(value = "status", required = false) ContactStatus status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(contactService.list(status, page, size)));
    }

    @Operation(summary = "Mark a contact message handled (or reopen it)", description = "Body: {\"status\":\"HANDLED\"} or {\"status\":\"NEW\"}.")
    @PatchMapping("/{id}")
    public ResponseEntity<ApiResponse<ContactMessageDto>> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateContactStatusDto dto,
            Authentication authentication) {
        ContactMessageDto updated = contactService.setStatus(id, dto.getStatus(), authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(updated, "Contact message updated"));
    }
}

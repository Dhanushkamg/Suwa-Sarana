package com.suwasarana.api.contact;

import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/contact")
@Tag(name = "Contact", description = "Public contact us endpoint")
public class ContactController {

    private final ContactService contactService;

    @Autowired
    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @Operation(summary = "Submit contact message",
            description = "Allows any user to submit a contact/support message. Rate limited per IP (group 'contact').")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitContact(@Valid @RequestBody ContactRequestDto dto) {
        // Same answer whether the message was stored or caught by the honeypot, so a bot learns nothing.
        contactService.submit(dto);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully. We will get back to you soon."));
    }
}

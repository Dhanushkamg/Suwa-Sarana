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

    private final ContactRepository contactRepository;

    @Autowired
    public ContactController(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Operation(summary = "Submit contact message", description = "Allows any user to submit a contact/support message.")
    @PostMapping
    public ResponseEntity<ApiResponse<String>> submitContact(@Valid @RequestBody ContactRequestDto dto) {
        ContactMessage msg = new ContactMessage();
        msg.setName(dto.getName());
        msg.setEmail(dto.getEmail());
        msg.setSubject(dto.getSubject());
        msg.setMessage(dto.getMessage());
        contactRepository.save(msg);
        return ResponseEntity.ok(ApiResponse.success("Message sent successfully. We will get back to you soon."));
    }
}

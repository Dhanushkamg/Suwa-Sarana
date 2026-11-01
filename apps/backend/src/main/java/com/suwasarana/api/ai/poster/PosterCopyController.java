package com.suwasarana.api.ai.poster;

import com.suwasarana.api.common.ApiResponse;
import com.suwasarana.api.security.UserDetailsImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Short poster copy (headline, body, caption, hashtags) for urgent requests, camps, awareness and thank-you posters.
 * Any authenticated user may use it; an URGENT_REQUEST poster additionally needs a VERIFIED requester (or an admin).
 * There is no per-role guard beyond that today: if camps/requests are restricted to institution roles elsewhere, add
 * {@code hasAnyRole(...)} here.
 */
@RestController
@RequestMapping("/api/ai")
@Tag(name = "AI Poster Copy", description = "Validated short copy for poster templates; facts are never invented by the model")
public class PosterCopyController {

    private final PosterCopyService service;

    public PosterCopyController(PosterCopyService service) {
        this.service = service;
    }

    @Operation(summary = "Generate poster copy",
            description = "Returns headline, body, hashtags and caption in en/si/ta. Facts are structured fields substituted into the text. "
                    + "422 poster.unverified_requester for URGENT_REQUEST by an unverified requester; 422 poster.patient_name_not_allowed "
                    + "when a patient/personal field is sent.")
    @PostMapping("/poster-copy")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<PosterCopyResponseDto>> posterCopy(@AuthenticationPrincipal UserDetailsImpl user,
                                                                         @Valid @RequestBody PosterCopyRequestDto request) {
        // refusals are PosterException (an ApiException): the global handler renders the standard error envelope
        PosterCopyResponseDto copy = service.generate(user == null ? null : user.getId(), request);
        return ResponseEntity.ok(ApiResponse.success(copy, "Poster copy generated"));
    }
}

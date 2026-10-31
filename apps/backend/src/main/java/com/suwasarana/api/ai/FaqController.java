package com.suwasarana.api.ai;

import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/faq")
@Tag(name = "AI Donor FAQ", description = "Retrieval-grounded donor eligibility assistant (English, Sinhala, Tamil, Singlish)")
public class FaqController {

    private final DonorFaqAiService donorFaqAiService;

    public FaqController(DonorFaqAiService donorFaqAiService) {
        this.donorFaqAiService = donorFaqAiService;
    }

    @Operation(summary = "Ask a donor eligibility question",
            description = "Answers only from the curated, versioned NBTS knowledge base (every answer cites its sources). "
                    + "If the knowledge base has no confirmed answer the reply says so and points to the Medical Officer / NBTS. "
                    + "Emergency symptoms get a short safety reply. Public endpoint; rate limiting is applied by the servlet filter.")
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<FaqAnswerDto>> askQuestion(@Valid @RequestBody FaqQuestionDto request) {
        FaqAnswerDto answer = donorFaqAiService.askQuestion(request);
        return ResponseEntity.ok(ApiResponse.success(answer, "FAQ response retrieved successfully"));
    }
}

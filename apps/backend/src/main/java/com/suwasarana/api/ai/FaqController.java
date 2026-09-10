package com.suwasarana.api.ai;

import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faq")
@Tag(name = "AI Donor FAQ", description = "Grounded donor eligibility, deferral rules, and trilingual guidance FAQ assistant")
public class FaqController {

    @Autowired
    private DonorFaqAiService donorFaqAiService;

    @Operation(summary = "Ask donor eligibility FAQ question", description = "Answers donor eligibility and deferral rule questions grounded in official National Blood Transfusion Service guidelines (EN/SI/TA).")
    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<FaqAnswerDto>> askQuestion(@Valid @RequestBody FaqQuestionDto request) {
        FaqAnswerDto answer = donorFaqAiService.askQuestion(request);
        return ResponseEntity.ok(ApiResponse.success(answer, "FAQ response retrieved successfully"));
    }
}

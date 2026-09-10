package com.suwasarana.api.ai;

import com.suwasarana.api.ai.dto.FaqAnswerDto;
import com.suwasarana.api.ai.dto.FaqQuestionDto;
import com.suwasarana.api.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/faq")
public class FaqController {

    @Autowired
    private DonorFaqAiService donorFaqAiService;

    @PostMapping("/ask")
    public ResponseEntity<ApiResponse<FaqAnswerDto>> askQuestion(@Valid @RequestBody FaqQuestionDto request) {
        FaqAnswerDto answer = donorFaqAiService.askQuestion(request);
        return ResponseEntity.ok(ApiResponse.success(answer, "FAQ response retrieved successfully"));
    }
}

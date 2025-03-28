package com.example.aiservice.controller;

import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping("/process")
    public AIAnalysisResponse processText(@RequestBody AIAnalysisRequest request) {
        return aiService.analyzeText(request);
    }


}

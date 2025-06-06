package com.example.aiservice.controller;

import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;

    @PostMapping("/process")
    public AIAnalysisResponse processText(@RequestBody AIAnalysisRequest request) {
        log.info("AI təhlil sorğusu alındı: pdfId={}, hash={}", request.getPdfId(), request.getHash());
        return aiService.analyzeText(request);
    }

    
}
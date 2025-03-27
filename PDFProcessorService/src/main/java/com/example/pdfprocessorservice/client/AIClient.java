package com.example.pdfprocessorservice.client;

import com.example.pdfprocessorservice.dto.AIAnalysisRequest;
import com.example.pdfprocessorservice.dto.AIAnalysisResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "AIService", url = "${ai.service.url}")
public interface AIClient {

    @PostMapping("/ai/process")
    AIAnalysisResponse analyzeText(AIAnalysisRequest extractedText);
}

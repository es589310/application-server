package com.example.pdfprocessorservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;

@FeignClient(name = "ai-service", url = "${ai.service.url}")
public interface AIClient {

    @PostMapping("/analyze")
    String analyzeText(String extractedText);
}

package com.example.aiservice.controller;

import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.service.AiService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {
    private final AiService aiService;

    @PostMapping("/process")
    public Mono<ResponseEntity<AiResponse>> processAiAnalysis(@RequestBody AiRequest request) {
        return aiService.processAiAnalysis(request)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/latest/{pdfId}")
    public Mono<ResponseEntity<AiResponse>> getLatestAnalysis(@PathVariable String pdfId) {
        return aiService.getLatestAnalysisByDocumentId(pdfId)
                .map(response -> response != null
                        ? ResponseEntity.ok(response)
                        : ResponseEntity.notFound().build());
    }

    @GetMapping("/by-document/{documentId}")
    public Mono<ResponseEntity<List<AiResponse>>> getAnalysesByDocumentId(@PathVariable String documentId) {
        return aiService.findAnalysesByDocumentId(documentId)
                .map(ResponseEntity::ok);
    }

    @GetMapping("/successful")
    public Mono<ResponseEntity<List<AiResponse>>> getSuccessfulAnalyses() {
        return aiService.findSuccessfulAnalyses()
                .map(ResponseEntity::ok);
    }
}
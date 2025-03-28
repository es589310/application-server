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



    /*@GetMapping("/latest/{pdfId}")
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
*/
}

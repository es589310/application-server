package com.example.aiservice.service;

import com.example.aiservice.client.PdfProcessorClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.repository.AiRequestRepository;
import com.example.aiservice.repository.AiResponseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.transaction.annotation.Propagation.REQUIRED;

@Service
@Slf4j
@RequiredArgsConstructor
public class AiService {

    private final PdfProcessorClient pdfProcessorClient;
    private final AiProcessor aiProcessor;
    private final AiRequestRepository aiRequestRepository;
    private final AiResponseRepository aiResponseRepository;

    @Transactional(propagation = REQUIRED)
    public AIAnalysisResponse analyzeText(AIAnalysisRequest request) {
        log.info("Mətn təhlili başlayır: pdfId={}, analysisType={}", request.getPdfId(), request.getAnalysisType());

        try {
            AiRequest aiRequest = AiRequest.builder()
                    .pdfId(String.valueOf(request.getPdfId()))
                    .extractedText(request.getExtractedText())
                    .analysisType(request.getAnalysisType())
                    .requestDate(LocalDateTime.now())
                    .build();
            final AiRequest savedAiRequest = aiRequestRepository.save(aiRequest); // final elan edirik

            String textToAnalyze = request.getExtractedText();
            if (textToAnalyze == null || textToAnalyze.trim().isEmpty()) {
                log.info("Çıxarılmış mətn boşdur, PDF məzmunu pdfId={} ilə alınır", request.getPdfId());
                textToAnalyze = pdfProcessorClient.getPdfContent(String.valueOf(request.getPdfId()));
                if (textToAnalyze == null) {
                    AiResponse errorResponse = AiResponse.builder()
                            .request(savedAiRequest)
                            .success(false)
                            .message("PDF məzmunu alınmadı")
                            .createdAt(LocalDateTime.now())
                            .build();
                    aiResponseRepository.save(errorResponse);
                    return new AIAnalysisResponse(false, null, "PDF məzmunu alınmadı");
                }
                savedAiRequest.setExtractedText(textToAnalyze);
                aiRequestRepository.save(savedAiRequest); // Yenilənmiş obyekti saxlayırıq
            }

            // Flux<AiResponse>-u Mono<AiResponse>-a çeviririk
            Flux<AiResponse> aiResponseFlux = aiProcessor.processWithAi(textToAnalyze, request.getAnalysisType());
            Mono<AiResponse> aiResponseMono = aiResponseFlux
                    .collectList() // Bütün nəticələri List<AiResponse> kimi toplayır
                    .map(responses -> {
                        if (responses.isEmpty()) {
                            return AiResponse.builder()
                                    .request(savedAiRequest) // final dəyişəni istifadə edirik
                                    .success(false)
                                    .message("AI təhlili nəticəsi yoxdur")
                                    .createdAt(LocalDateTime.now())
                                    .build();
                        }

                        // Bütün nəticələri birləşdiririk
                        StringBuilder combinedResult = new StringBuilder();
                        boolean allSuccess = true;
                        for (AiResponse resp : responses) {
                            combinedResult.append(resp.getAnalysisResult()).append("\n");
                            if (!resp.isSuccess()) {
                                allSuccess = false;
                            }
                        }

                        return AiResponse.builder()
                                .request(savedAiRequest) // final dəyişəni istifadə edirik
                                .analysisResult(combinedResult.toString().trim())
                                .success(allSuccess)
                                .message(allSuccess ? "Uğurlu" : "Qismən uğursuz")
                                .createdAt(LocalDateTime.now())
                                .build();
                    });

            AiResponse aiResponse = aiResponseMono.block();
            if (aiResponse == null || !aiResponse.isSuccess()) {
                aiResponse = AiResponse.builder()
                        .request(savedAiRequest)
                        .success(false)
                        .message("AI təhlili uğursuz oldu")
                        .createdAt(LocalDateTime.now())
                        .build();
            }

            aiResponseRepository.save(aiResponse);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("analyzedText", aiResponse.getAnalysisResult());
            metadata.put("tableCount", textToAnalyze.split("\n").length);
            metadata.put("timestamp", aiResponse.getCreatedAt().toString());

            return new AIAnalysisResponse(aiResponse.isSuccess(), metadata, aiResponse.getMessage());
        } catch (Exception e) {
            log.error("Mətn təhlili zamanı xəta: {}", e.getMessage());
            AiRequest errorRequest = AiRequest.builder()
                    .pdfId(String.valueOf(request.getPdfId()))
                    .extractedText(request.getExtractedText())
                    .analysisType(request.getAnalysisType())
                    .requestDate(LocalDateTime.now())
                    .build();
            final AiRequest savedErrorRequest = aiRequestRepository.save(errorRequest);
            AiResponse errorResponse = AiResponse.builder()
                    .request(savedErrorRequest)
                    .success(false)
                    .message("Təhlil uğursuz oldu: " + e.getMessage())
                    .createdAt(LocalDateTime.now())
                    .build();
            aiResponseRepository.save(errorResponse);
            return new AIAnalysisResponse(false, null, "Təhlil uğursuz oldu: " + e.getMessage());
        }
    }
}
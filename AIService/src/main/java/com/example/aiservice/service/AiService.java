package com.example.aiservice.service;

import com.example.aiservice.client.PdfProcessorClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.repository.AiResponseRepository;
import com.example.aiservice.repository.AiRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
    private final AiRequestRepository aiRequestRepository; // AiRequest için
    private final AiResponseRepository aiResponseRepository; // AiResponse için

    @Transactional(propagation = REQUIRED)
    public AIAnalysisResponse analyzeText(AIAnalysisRequest request) {
        log.info("Metin analizi başlatıldı: pdfId={}, analysisType={}", request.getPdfId(), request.getAnalysisType());

        try {
            AiRequest aiRequest = AiRequest.builder()
                    .pdfId(request.getPdfId())
                    .extractedText(request.getExtractedText())
                    .analysisType(request.getAnalysisType())
                    .requestDate(LocalDateTime.now())
                    .build();
            aiRequest = aiRequestRepository.save(aiRequest); // AiRequest’i kaydet

            String textToAnalyze = request.getExtractedText();
            if (textToAnalyze == null || textToAnalyze.trim().isEmpty()) {
                log.info("Extracted text boş, PDF içeriği pdfId={} ile çekiliyor", request.getPdfId());
                textToAnalyze = pdfProcessorClient.getPdfContent(request.getPdfId());
                if (textToAnalyze == null) {
                    AiResponse errorResponse = AiResponse.builder()
                            .request(aiRequest)
                            .success(false)
                            .message("PDF içeriği alınamadı")
                            .createdAt(LocalDateTime.now())
                            .build();
                    aiResponseRepository.save(errorResponse);
                    return new AIAnalysisResponse(false, null, "PDF içeriği alınamadı");
                }
                aiRequest.setExtractedText(textToAnalyze);
                aiRequest = aiRequestRepository.save(aiRequest);
            }

            Mono<AiResponse> aiResponseMono = aiProcessor.processWithAi(textToAnalyze, request.getAnalysisType());
            AiResponse aiResponse = aiResponseMono.block();
            if (aiResponse == null || !aiResponse.isSuccess()) {
                aiResponse = AiResponse.builder()
                        .success(false)
                        .message("AI analizi başarısız")
                        .createdAt(LocalDateTime.now())
                        .build();
            }

            aiResponse.setRequest(aiRequest);
            aiResponseRepository.save(aiResponse);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("analyzedText", aiResponse.getAnalysisResult());
            metadata.put("tableCount", textToAnalyze.split("\n").length);
            metadata.put("timestamp", aiResponse.getCreatedAt().toString());

            return new AIAnalysisResponse(aiResponse.isSuccess(), metadata, aiResponse.getMessage());
        } catch (Exception e) {
            log.error("Metin analizi sırasında hata: {}", e.getMessage());
            AiRequest errorRequest = AiRequest.builder()
                    .pdfId(request.getPdfId())
                    .extractedText(request.getExtractedText())
                    .analysisType(request.getAnalysisType())
                    .requestDate(LocalDateTime.now())
                    .build();
            errorRequest = aiRequestRepository.save(errorRequest);
            AiResponse errorResponse = AiResponse.builder()
                    .request(errorRequest)
                    .success(false)
                    .message("Analiz başarısız: " + e.getMessage())
                    .createdAt(LocalDateTime.now())
                    .build();
            aiResponseRepository.save(errorResponse);
            return new AIAnalysisResponse(false, null, "Analiz başarısız: " + e.getMessage());
        }
    }
}
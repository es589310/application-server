package com.example.aiservice.service;

import com.example.aiservice.client.OpenAIClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.entity.AiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiProcessor {
    private final OpenAIClient openAIClient;

    @Value("${openai.model}")
    private String model;

    public Mono<AiResponse> processWithAi(String content, String analysisType) {
        log.info("AiProcessor: Mətn təhlili başlayır, content={}, analysisType={}", content, analysisType);

        if (content == null || content.trim().isEmpty()) {
            log.warn("AiProcessor: Təhlil ediləcək mətn boşdur və ya null-dur");
            return Mono.just(AiResponse.builder()
                    .success(false)
                    .message("Təhlil ediləcək mətn boşdur")
                    .createdAt(LocalDateTime.now())
                    .build());
        }

        String prompt = generatePrompt(content, analysisType);
        AIAnalysisRequest request = AIAnalysisRequest.builder()
                .pdfId(null)
                .extractedText(prompt)
                .analysisType(analysisType)
                .model(model)
                .build();

        log.info("AiProcessor: OpenAI-yə sorğu göndərilir, model={}, prompt={}", model, prompt);

        return openAIClient.generateText(request)
                .map(response -> {
                    log.info("AiProcessor: OpenAI-dən cavab alındı: {}", response);
                    String analysisResult = response.getExtractedMetadata() != null
                            ? response.getExtractedMetadata().toString()
                            : "Təhlil nəticəsi yoxdur";
                    return AiResponse.builder()
                            .analysisResult(analysisResult)
                            .success(true)
                            .message("Uğurlu")
                            .createdAt(LocalDateTime.now())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("AiProcessor: OpenAI təhlili uğursuz oldu: {}", e.getMessage(), e);
                    return Mono.just(AiResponse.builder()
                            .success(false)
                            .message("OpenAI xətası: " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private String generatePrompt(String content, String analysisType) {
        if ("METADATA_COMPLETION".equals(analysisType)) {
            return String.format("""
            You are an AI assistant performing METADATA_COMPLETION analysis.
            Analyze the following text and categorize each expense into one of these categories:
            - Restoran
            - Kafe
            - Kommunal ödənişlər
            - Banklara ödənişlər
            - Taksilərə ödəniş
            - Alış-veriş (mağaza, market)
            If an expense doesn’t fit any category, mark it as "Digər".
            Return the result as a JSON array where each entry has "date", "category", and "amount" fields.

            Text: %s
            
            Analysis:""", content);
        }
        return String.format("""
        You are an AI assistant performing %s analysis.
        Analyze the following text:

        Text: %s
        
        Analysis:""", analysisType, content);
    }
}
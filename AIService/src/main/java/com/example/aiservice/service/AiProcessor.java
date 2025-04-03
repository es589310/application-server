package com.example.aiservice.service;

import com.example.aiservice.client.GeminiAIClient;
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
    private final GeminiAIClient geminiAIClient;

    @Value("${gemini.model}")
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

        log.info("AiProcessor: Gemini AI-yə sorğu göndərilir, model={}, prompt={}", model, prompt);

        return geminiAIClient.generateText(request)
                .map(response -> {
                    log.info("AiProcessor: Gemini AI-dən cavab alındı: {}", response);
                    String analysisResult = response.getExtractedMetadata() != null
                            ? response.getExtractedMetadata().toString()
                            : "Təhlil nəticəsi yoxdur";

                    // JSON parse etme (isteğe bağlı)
                    if ("METADATA_COMPLETION".equals(analysisType) && analysisResult.startsWith("[")) {
                        try {
                            // JSON stringini kontrol et ve gerekirse parse et
                            return AiResponse.builder()
                                    .analysisResult(analysisResult) // JSON string olarak bırakabilirsin
                                    .success(true)
                                    .message("Uğurlu")
                                    .createdAt(LocalDateTime.now())
                                    .build();
                        } catch (Exception e) {
                            log.error("JSON parse hatası: {}", e.getMessage());
                        }
                    }

                    return AiResponse.builder()
                            .analysisResult(analysisResult)
                            .success(true)
                            .message("Uğurlu")
                            .createdAt(LocalDateTime.now())
                            .build();
                })
                .onErrorResume(e -> {
                    log.error("AiProcessor: Gemini AI təhlili uğursuz oldu: {}", e.getMessage(), e);
                    return Mono.just(AiResponse.builder()
                            .success(false)
                            .message("Gemini AI xətası: " + e.getMessage())
                            .createdAt(LocalDateTime.now())
                            .build());
                });
    }

    private String generatePrompt(String content, String analysisType) {
        if ("METADATA_COMPLETION".equals(analysisType)) {
            return String.format("""
            Aşağıdaki metni analiz et ve her bir xərci aşağıdakı kateqoriyalardan birinə yerləşdir:
            - Restoran
            - Kafe
            - Kommunal ödənişlər
            - Banklara ödənişlər
            - Taksilərə ödəniş
            - Alış-veriş (mağaza, market)
            Əgər bir xərc heç bir kateqoriyaya uyğun gəlmirsə, onu "Digər" olaraq işarələ.
            Nəticəni JSON formatında qaytar, hər bir girişdə "date", "category" və "amount" sahələri olmalıdır.

            Metn: %s
            """, content);
        }
        return String.format("""
        %s analizini həyata keçirən bir AI köməkçisisən.
        Aşağıdaki metni analiz et:

        Metn: %s
        """, analysisType, content);
    }
}
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

                    if ("METADATA_COMPLETION".equals(analysisType) && analysisResult.startsWith("[")) {
                        try {
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
        Analyze the following text and categorize each transaction into one of these categories:
        - Restaurant
        - Cafe
        - Utility payments
        - Bank payments (official bank transfers, e.g., SWIFT, IBAN payments)
        - Taxi payments (e.g., Bolt, Yango)
        - Shopping (stores, markets, e.g., Favorit Market, Ərzaq Mağaza)
        - Pharmacy (e.g., Zeytun Aptek, Buta Aptek)
        - Food delivery (e.g., Wolt, Volt as courier service)
        - Mobile payments (e.g., Bakcell)
        - Technology/Subscriptions (e.g., Apple, Microsoft, Spotify, Google)
        - Travel (e.g., Kiwi.com)
        - Personal transfers (e.g., payments to individuals like Necibe Xala, Fərid H.)
        - Bank service fees/Commissions (e.g., Conversion fee, Prime üçün xidmət haqqı)
        - Fuel (e.g., Azpetrol)
        - Parking (e.g., azparking)
        - Public transport (e.g., BakıKart)
        - Online payments (e.g., MilliÖn, eManat)
        
        If a transaction doesn’t fit any category or its purpose is unclear, mark it as "Other".
        For transactions starting with "Purchase", consider the words following "Purchase" to determine the category (e.g., "Purchase APPLE.COM" → "Technology/Subscriptions").
        
        Take into account income and expenses based on the "Məbləğ" field:
        - If "Məbləğ" starts with "+", classify it as "income".
        - If "Məbləğ" starts with "-", classify it as "expense".
        - Remove the "+" or "-" signs when assigning the amount to "income" or "expense".
        
        Return each transaction as a separate entry based on its transaction date, even if multiple transactions occur on the same day. Do not combine incomes or expenses for the same date into a single entry.
        For each entry, include:
        - "date": Extracted from "Tarix" in "DD-MM-YYYY" format (ignore time).
        - "category": The determined category.
        - "income": Set to the amount if "Məbləğ" is positive, otherwise 0.00.
        - "expense": Set to the amount if "Məbləğ" is negative, otherwise 0.00.
        - "balance": The value from the "Balans" field, if present; otherwise, leave it as null.
        
        Special cases:
        - "Şəxsi vəsaitlərin qalıq faizi": Treat as "Bank service fees/Commissions" or "Other" if unclear, typically income.
        - "Prime üçün xidmət haqqı": Treat as "Bank service fees/Commissions", typically expense.
        - "Conversion fee": Treat as "Bank service fees/Commissions", typically expense.

        Return the result in JSON format.

        Text: %s
        """, content);
        }
        return String.format("""
    You are an AI assistant performing %s analysis.
    Analyze the following text:

    Text: %s
    """, analysisType, content);
    }
}
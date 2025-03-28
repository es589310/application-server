package com.example.aiservice.service;

import com.example.aiservice.client.OpenAIClient;
import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import com.example.aiservice.entity.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class AiProcessor {
    private final OpenAIClient openAIClient;

    @Value("${openai.model}")
    private String model;

    public Mono<AiResponse> processWithAi(String content, String analysisType) {
        AIAnalysisRequest request = new AIAnalysisRequest(
                null, // pdfId AiService'den gelecek
                content,
                analysisType
        );

        return openAIClient.generateText(request)
                .map(response -> {
                    // OpenAI yanıtını AiResponse'a dönüştür
                    AiResponse aiResponse = new AiResponse();
                    aiResponse.setAnalysisResult(response.getExtractedMetadata() != null
                            ? response.getExtractedMetadata().toString()
                            : "Analiz sonucu"); // OpenAI yanıtına göre özelleştirin
                    aiResponse.setSuccess(true);
                    aiResponse.setMessage("Başarılı");
                    aiResponse.setCreatedAt(LocalDateTime.now());
                    return aiResponse;
                })
                .onErrorResume(e -> Mono.just(
                        AiResponse.builder()
                                .success(false)
                                .message("OpenAI hatası: " + e.getMessage())
                                .createdAt(LocalDateTime.now())
                                .build()
                ));
    }

    private String generatePrompt(String content, String analysisType) {
        return String.format("""
            You are an AI assistant performing %s analysis.
            Carefully analyze the following text and provide a detailed, insightful response:

            Text: %s
            
            Analysis:""", analysisType, content);
    }
}
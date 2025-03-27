package com.example.aiservice.service;

import com.example.aiservice.client.OpenAIClient;
import com.example.aiservice.dto.OpenAIRequest;
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
        OpenAIRequest request = OpenAIRequest.builder()
                .model(model)
                .prompt(generatePrompt(content, analysisType))
                .max_tokens(150)
                .temperature(0.7)
                .build();

        return openAIClient.generateText(request)
                .map(response -> {
                    AiResponse aiResponse = new AiResponse();
                    aiResponse.setAnalysisResult(response.getChoices().get(0).getText());
                    aiResponse.setSuccess(true);
                    aiResponse.setCreatedAt(LocalDateTime.now());
                    return aiResponse;
                });
    }

    private String generatePrompt(String content, String analysisType) {
        return String.format("""
        You are an AI assistant performing %s analysis.
        Carefully analyze the following text and provide a detailed, insightful response:

        Text: %s
        
        Analysis:""", analysisType, content);
    }
}
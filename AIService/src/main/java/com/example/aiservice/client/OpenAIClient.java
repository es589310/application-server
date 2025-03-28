package com.example.aiservice.client;

import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class OpenAIClient {

    private final WebClient webClient;
    private final String apiKey;

    public OpenAIClient(WebClient.Builder webClientBuilder, @Value("${openai.api.key}") String apiKey) {
        this.apiKey = apiKey;
        log.info("OpenAI API açarı: {}", apiKey);
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Mono<AIAnalysisResponse> generateText(AIAnalysisRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", request.getModel());
        body.put("messages", List.of(Map.of("role", "user", "content", request.getExtractedText())));
        body.put("max_tokens", 150);

        log.info("OpenAI-yə göndərilən sorğu: {}", body);

        return webClient.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey) // Təhlükəsizlik üçün manual əlavə
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("OpenAI API Xətası: " + errorBody)))
                )
                .bodyToMono(Map.class)
                .map(response -> {
                    log.info("OpenAI-dən alınan ümumi cavab: {}", response);
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                    if (choices != null && !choices.isEmpty()) {
                        String text = (String) ((Map<String, Object>) choices.get(0).get("message")).get("content");
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("analyzedText", text);
                        return new AIAnalysisResponse(true, metadata, "Uğurlu");
                    }
                    return new AIAnalysisResponse(false, null, "OpenAI cavabı boşdur");
                });
    }
}
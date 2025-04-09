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
public class GeminiAIClient {

    private final WebClient webClient;
    private final String apiKey;
    private final String model;

    public GeminiAIClient(WebClient.Builder webClientBuilder,
                          @Value("${gemini.api.key}") String apiKey,
                          @Value("${gemini.api.base-url}") String baseUrl,
                          @Value("${gemini.model}") String model) {
        this.apiKey = apiKey;
        this.model = model;
        //log.info("Gemini API key: {}", apiKey);
        log.info("Gemini API modeli: {}", model);
        this.webClient = webClientBuilder
                .baseUrl(baseUrl) // propertiesdən alınan url
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Mono<AIAnalysisResponse> generateText(AIAnalysisRequest request) {
        // Gemini API-ın gözlədiyi request məntiqi
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", request.getExtractedText())))
        ));

        log.info("Gemini AI-a göndərilən istək: {}", body);

        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/models/{model}:generateContent")
                        .queryParam("key", apiKey) // API key-i sorğu parametri olaraq əlavə edilib
                        .build(model))
                .bodyValue(body)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("Gemini AI API Xətası: " + errorBody)))
                )
                .bodyToMono(Map.class)
                .map(response -> {
                    log.info("Gemini AI-dan alınan cavab: {}", response);
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
                    if (candidates != null && !candidates.isEmpty()) {
                        Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                        List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                        String text = parts.get(0).get("text");
                        Map<String, Object> metadata = new HashMap<>();
                        metadata.put("analyzedText", text);
                        return new AIAnalysisResponse(true, metadata, "Alındı!");
                    }
                    return new AIAnalysisResponse(false, null, "Gemini AI cavabı boşdur.");
                });
    }
}
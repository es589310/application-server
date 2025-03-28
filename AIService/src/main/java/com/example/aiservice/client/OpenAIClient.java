package com.example.aiservice.client;

import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class OpenAIClient {

    @Value("${openai.api.key}")
    private String apiKey;

    private final WebClient webClient;

    public OpenAIClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .defaultHeader("Accept", "application/json")
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    public Mono<AIAnalysisResponse> generateText(AIAnalysisRequest request) {
        return webClient.post()
                .uri("/completions")
                .bodyValue(request)
                .retrieve()
                .onStatus(
                        httpStatus -> httpStatus.is4xxClientError() || httpStatus.is5xxServerError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .flatMap(errorBody -> Mono.error(new RuntimeException("OpenAI API Error: " + errorBody)))
                )
                .bodyToMono(AIAnalysisResponse.class);
    }
}
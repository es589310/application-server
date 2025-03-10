package com.example.aiservice.client;

import com.example.aiservice.entity.OpenAIRequest;
import com.example.aiservice.entity.OpenAIResponse;
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
                .build();
    }

    public Mono<OpenAIResponse> generateText(OpenAIRequest request) {
        return webClient.post()
                .uri("/completions")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(OpenAIResponse.class);
    }
}
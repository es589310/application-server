package com.example.pdfprocessorservice.client;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class AiClient {

    private final WebClient webClient;

    public AiClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder.baseUrl("http://ai-service:8082").build();
    }

    public String analyzeText(String extractedText) {
        return webClient.post()
                .uri("/analyze")
                .bodyValue(extractedText)
                .retrieve()
                .bodyToMono(String.class)
                .block();
    }
}

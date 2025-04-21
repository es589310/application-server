package com.example.aiservice.client;

import com.example.aiservice.dto.AIAnalysisRequest;
import com.example.aiservice.dto.AIAnalysisResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class GeminiAIClient {

    private final RestTemplate restTemplate;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public GeminiAIClient(RestTemplate restTemplate,
                          @Value("${gemini.api.key}") String apiKey,
                          @Value("${gemini.api.base-url}") String baseUrl,
                          @Value("${gemini.model}") String model) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
        this.model = model;
        this.baseUrl = baseUrl;
        log.info("Gemini API modeli: {}", model);
    }

    @Retryable(
            value = {HttpServerErrorException.class, org.springframework.web.client.ResourceAccessException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2) // 1sn, 2sn, 4sn gecikme
    )
    public AIAnalysisResponse generateText(AIAnalysisRequest request) {
        Map<String, Object> body = new HashMap<>();
        body.put("contents", List.of(
                Map.of("parts", List.of(Map.of("text", request.getExtractedText())))
        ));

        if (log.isDebugEnabled()) {
            log.debug("Gemini AI-a göndərilən istək: {}", body); // info -> debug
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            String url = baseUrl + "/models/" + model + ":generateContent?key=" + apiKey;

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);

            log.info("Gemini AI-dan cavab alındı, status={}", response.getStatusCode()); // Önemli, info

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.getBody().get("candidates");
            if (candidates != null && !candidates.isEmpty()) {
                Map<String, Object> content = (Map<String, Object>) candidates.get(0).get("content");
                List<Map<String, String>> parts = (List<Map<String, String>>) content.get("parts");
                String text = parts.get(0).get("text");
                Map<String, Object> metadata = new HashMap<>();
                metadata.put("analyzedText", text);
                return new AIAnalysisResponse(true, metadata, "Alındı!");
            }
            return new AIAnalysisResponse(false, null, "Gemini AI cavabı boşdur.");
        } catch (Exception e) {
            log.error("Gemini AI API Xətası: {}", e.getMessage());
            throw e; // Retry için hatayı fırlat
        }
    }
}
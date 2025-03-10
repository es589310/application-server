package com.example.aiservice.service;

import com.example.aiservice.client.OpenAIClient;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.entity.OpenAIRequest;
import com.example.aiservice.entity.OpenAIResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiProcessor {

    private final OpenAIClient openAIClient;

    public AiResponse processContent(String content) {
        OpenAIRequest request = new OpenAIRequest();
        request.setModel("text-davinci-003"); // ChatGPT modeli
        request.setPrompt(content); // Gönderilecek metin
        request.setTemperature(0.7); // Yaratıcılık seviyesi
        request.setMax_tokens(100); // Maksimum token sayısı

        OpenAIResponse response = openAIClient.generateText(request).block();

        AiResponse aiResponse = new AiResponse();
        if (response != null && response.getChoices() != null && !response.getChoices().isEmpty()) {
            aiResponse.setResult(response.getChoices().get(0).getText());
        } else {
            aiResponse.setResult("No response from OpenAI.");
        }
        return aiResponse;
    }
}
package com.example.aiservice.dto;

import lombok.*;

import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AIAnalysisResponse {
    private boolean success;
    private Map<String, Object> extractedMetadata;
    private String message;
}
package com.example.aiservice.dto;

import lombok.*;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AIAnalysisRequest {
    private Long pdfId;
    private String extractedText;
    private String analysisType;
    private String model;
}
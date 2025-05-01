package com.example.pdfprocessorservice.dto;

import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Builder
public class AIAnalysisRequest {
    private String pdfId;
    private String extractedText;
    private String hash; // SHA-256 hash
    private String analysisType;
}
package com.example.pdfprocessorservice.dto;

import lombok.*;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AIAnalysisRequest {
    private String pdfId;
    private String extractedText;
    private String analysisType;
}
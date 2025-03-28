package com.example.pdfprocessorservice.dto;

import lombok.*;

import java.util.Map;


@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AIAnalysisResponse {
    private boolean success;
    private Map<String, Object> extractedMetadata;  // AI tərəfindən doldurulan əksik metadatalar
    private String message;
}
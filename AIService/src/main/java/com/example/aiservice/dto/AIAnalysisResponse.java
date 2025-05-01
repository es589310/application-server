package com.example.aiservice.dto;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class AIAnalysisResponse implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private boolean success;
    private Map<String, Object> extractedMetadata;
    private String message;
}
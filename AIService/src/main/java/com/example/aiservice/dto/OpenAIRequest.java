package com.example.aiservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class OpenAIRequest {
    private String model;
    private String prompt;
    @Builder.Default
    private Double temperature = 0.7;
    @Builder.Default
    private Integer max_tokens = 150;
}

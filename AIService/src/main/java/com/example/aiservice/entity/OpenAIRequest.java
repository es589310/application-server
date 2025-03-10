package com.example.aiservice.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class OpenAIRequest {
    private String model;
    private String prompt;
    private Double temperature;
    private Integer max_tokens;
}

package com.example.aiservice.dto;

import lombok.Data;

import java.util.List;


@Data
public class OpenAIResponse {
    private String id;
    private String object;
    private String model;
    private List<Choice> choices;

    @Data
    public static class Choice {
        private String text;
        private int index;
        private String finish_reason;
    }
}

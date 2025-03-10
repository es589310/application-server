package com.example.aiservice.entity;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;


@Data
@Getter
@Setter
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

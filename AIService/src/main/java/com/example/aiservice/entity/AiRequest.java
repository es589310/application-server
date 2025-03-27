package com.example.aiservice.entity;

import lombok.*;

import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class AiRequest implements Serializable {
    private String pdfId;
    private String content;
    private String analysisType;
}

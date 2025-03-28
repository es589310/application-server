package com.example.aiservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ai_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String pdfId;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    private String analysisType;

    @Column(nullable = false)
    private LocalDateTime requestDate;
}
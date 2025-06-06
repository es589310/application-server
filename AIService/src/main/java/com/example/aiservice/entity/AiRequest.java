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

    @Column(name = "pdf_id")
    private String pdfId;

    @Column(name = "extracted_text",columnDefinition = "TEXT")
    private String extractedText;

    @Column(name = "analysis_type")
    private String analysisType;

    @Column(name = "hash")
    private String hash;

    @Column(name = "request_date")
    private LocalDateTime requestDate;
}

/*

    @Column(nullable = false)
    private String pdfId;

    @Column(columnDefinition = "TEXT")
    private String extractedText;

    @Column(nullable = false)
    private String analysisType;

    @Column(nullable = false)
    private LocalDateTime requestDate;
 */
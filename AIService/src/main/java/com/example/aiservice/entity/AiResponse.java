package com.example.aiservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_table")
public class AiResponse implements Serializable {
    @Id
    private String pdfId;

    @Column(columnDefinition = "TEXT")
    private String analysisResult;

    private boolean success;

    @Column(updatable = false)
    private LocalDateTime createdAt;

    // Constructor
    public AiResponse() {
        this.createdAt = LocalDateTime.now();
    }
}

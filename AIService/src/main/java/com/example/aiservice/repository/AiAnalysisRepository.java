package com.example.aiservice.repository;

import com.example.aiservice.entity.AiResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiResponse, String> {
    // PDF ID'sine göre AI analizlerini bulma
    List<AiResponse> findByPdfId(String pdfId);

    // En son yapılan AI analizini bulma
    Optional<AiResponse> findTopByPdfIdOrderByCreatedAtDesc(String pdfId);

    // Başarılı analizleri bulma
    List<AiResponse> findBySuccess(boolean success);
}

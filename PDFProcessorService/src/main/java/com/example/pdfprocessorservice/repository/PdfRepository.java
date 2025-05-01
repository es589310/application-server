package com.example.pdfprocessorservice.repository;

import com.example.pdfprocessorservice.entity.PdfEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PdfRepository extends JpaRepository<PdfEntity, Long> {
    Optional<PdfEntity> findByHash(String hash);
    Optional<PdfEntity> findByExtractedText(String extractedText);
    List<PdfEntity> findByFileName(String fileName);
    List<PdfEntity> findAll();
}
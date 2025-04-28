package com.example.pdfprocessorservice.repository;

import com.example.pdfprocessorservice.entity.PdfEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PdfRepository extends JpaRepository<PdfEntity, Long> {
    //Optional<PdfEntity> findByHash(String hash);
    PdfEntity findByExtractedText(String extractedText);
    List<PdfEntity> findByFileName(String fileName);
    List<PdfEntity> findAll();
}
package com.example.pdfprocessorservice.repository;

import com.example.pdfprocessorservice.entity.PdfEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PdfRepository extends JpaRepository<PdfEntity, Long> {
    PdfEntity findByContent(String content);
}

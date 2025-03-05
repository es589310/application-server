package com.example.pdfprocessorservice.repository;

import com.example.pdfprocessorservice.entity.ReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

}

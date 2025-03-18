package com.example.pdfprocessorservice.controller;

import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.service.PdfProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/pdf")
@RequiredArgsConstructor
@Slf4j
public class PdfController {

    private final PdfProcessorService pdfProcessorService;

    // PDF yükleme ve işleme endpoint'i
    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdfEntity> processPdf(@RequestParam("file") MultipartFile file) {
        try {
            PdfEntity processedPdf = pdfProcessorService.processPdf(file);
            log.info("PDF processed successfully: {}", processedPdf.getFileName());
            return ResponseEntity.ok(processedPdf);
        } catch (IOException e) {
            log.error("Error processing PDF: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    // PDF indirme endpoint'i (MinIO'dan dosyayı döndürür)
    @GetMapping("/download/{id}")
    public ResponseEntity<InputStreamResource> downloadPdf(@PathVariable Long id) {
        try {
            PdfEntity pdfEntity = pdfProcessorService.getPdfEntityById(id);
            if (pdfEntity == null || pdfEntity.getMinioPath() == null) {
                return ResponseEntity.notFound().build();
            }

            byte[] fileContent = pdfProcessorService.downloadFromMinIO(pdfEntity.getMinioPath());

            InputStreamResource resource = new InputStreamResource(new ByteArrayInputStream(fileContent));

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + pdfEntity.getFileName());
            headers.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            headers.add(HttpHeaders.PRAGMA, "no-cache");
            headers.add(HttpHeaders.EXPIRES, "0");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(fileContent.length)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (IOException e) {
            log.error("Error downloading PDF: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    // Yeni endpoint: Dosya adına göre PDF'leri listeleme
    @GetMapping("/by-filename/{fileName}")
    public ResponseEntity<List<PdfEntity>> getPdfsByFileName(@PathVariable String fileName) {
        List<PdfEntity> pdfs = pdfProcessorService.getPdfsByFileName(fileName);
        if (pdfs.isEmpty()) {
            log.info("No PDFs found with file name: {}", fileName);
            return ResponseEntity.noContent().build();
        }
        log.info("Found {} PDFs with file name: {}", pdfs.size(), fileName);
        return ResponseEntity.ok(pdfs);
    }

    // Yeni endpoint: ExtractedText'e göre PDF bulma
    @GetMapping("/by-extracted-text")
    public ResponseEntity<PdfEntity> getPdfByExtractedText(@RequestParam String extractedText) {
        PdfEntity pdfEntity = pdfProcessorService.getPdfByExtractedText(extractedText);
        if (pdfEntity == null) {
            log.info("No PDF found with extracted text: {}", extractedText);
            return ResponseEntity.notFound().build();
        }
        log.info("Found PDF with extracted text: {}", pdfEntity.getFileName());
        return ResponseEntity.ok(pdfEntity);
    }

    // Yeni endpoint: Tüm PDF'leri listeleme
    @GetMapping("/all")
    public ResponseEntity<List<PdfEntity>> getAllPdfs() {
        List<PdfEntity> allPdfs = pdfProcessorService.getAllPdfs();
        if (allPdfs.isEmpty()) {
            log.info("No PDFs found in the database");
            return ResponseEntity.noContent().build();
        }
        log.info("Found {} PDFs in total", allPdfs.size());
        return ResponseEntity.ok(allPdfs);
    }
}
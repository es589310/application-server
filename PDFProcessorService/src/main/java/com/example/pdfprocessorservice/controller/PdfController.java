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


    @PostMapping(value = "/process", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PdfEntity> processPdf(@RequestParam("file") MultipartFile file) {
        try {
            PdfEntity processedPdf = pdfProcessorService.processPdf(file);
            log.info("PDF uğurla emal edildi: {}", processedPdf.getFileName());
            return ResponseEntity.ok(processedPdf);
        } catch (IOException e) {
            log.error("PDF emal edilərkən səhv: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    // PDF yükləmə endpoint'i (MinIO-dan faylı döndürür)
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
            log.error("PDF yüklənərkən səhv: {}", e.getMessage());
            return ResponseEntity.status(500).body(null);
        }
    }

    // Fayl adına görə PDF-ləri siyahıya almaq
    @GetMapping("/by-filename/{fileName}")
    public ResponseEntity<List<PdfEntity>> getPdfsByFileName(@PathVariable String fileName) {
        List<PdfEntity> pdfs = pdfProcessorService.getPdfsByFileName(fileName);
        if (pdfs.isEmpty()) {
            log.info("Bu fayl adı ilə PDF tapılmadı: {}", fileName);
            return ResponseEntity.noContent().build();
        }
        log.info("Bu fayl adı ilə {} PDF tapıldı: {}", pdfs.size(), fileName);
        return ResponseEntity.ok(pdfs);
    }

    /*
    // ExtractedText-ə görə PDF tapmaq
    @GetMapping("/by-extracted-text")
    public ResponseEntity<PdfEntity> getPdfByExtractedText(@RequestParam String extractedText) {
        PdfEntity pdfEntity = pdfProcessorService.getPdfByExtractedText(extractedText);
        if (pdfEntity == null) {
            log.info("Extracted text ilə PDF tapılmadı: {}", extractedText);
            return ResponseEntity.notFound().build();
        }
        log.info("Extracted text ilə PDF tapıldı: {}", pdfEntity.getFileName());
        return ResponseEntity.ok(pdfEntity);
    }
    */

    // Bütün PDF-ləri siyahıya almaq
    @GetMapping("/all")
    public ResponseEntity<List<PdfEntity>> getAllPdfs() {
        List<PdfEntity> allPdfs = pdfProcessorService.getAllPdfs();
        if (allPdfs.isEmpty()) {
            log.info("Verilənlər bazasında PDF tapılmadı");
            return ResponseEntity.noContent().build();
        }
        log.info("Ümumilikdə {} PDF tapıldı", allPdfs.size());
        return ResponseEntity.ok(allPdfs);
    }
}
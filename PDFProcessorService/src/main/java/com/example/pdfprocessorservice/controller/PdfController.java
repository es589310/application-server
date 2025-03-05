package com.example.pdfprocessorservice.controller;

import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.exception.PdfProcessingException;
import com.example.pdfprocessorservice.repository.PdfRepository;
import com.example.pdfprocessorservice.service.PdfProcessorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdf")
@Tag(name = "PDF Controller", description = "Endpoints for uploading and processing PDF files")
@Slf4j
public class PdfController {
    private final PdfProcessorService pdfProcessorService;
    private final PdfRepository pdfRepository;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload a PDF file", description = "Uploads a PDF file for processing and analysis.")
    public ResponseEntity<Object> uploadPdfAsync(
            @RequestParam("file")
            @Parameter(description = "PDF file to be uploaded", required = true)
            MultipartFile file,
            @RequestParam("language")  // Dil parametresi eklendi
            @Parameter(description = "Language for OCR", required = true)
            String language){

        try {
            pdfProcessorService.processPdf(file, language);  // Dil parametri ilə PDF
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error occurred while processing PDF: " + e.getMessage());
        }
    }

    @GetMapping("/{pdfId}/table")
    @Operation(summary = "Get the processed table data of a PDF", description = "Returns the table data from a processed PDF by ID.")
    public ResponseEntity<List<String[]>> getProcessedTableData(@PathVariable("pdfId") Long pdfId) {
        try {
            PdfEntity pdfEntity = pdfRepository.findById(pdfId).orElse(null);
            if (pdfEntity == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            List<String[]> tableData = pdfProcessorService.extractTableData(pdfEntity.getContent());
            return ResponseEntity.ok(tableData);
        }catch (Exception e) {
            throw new PdfProcessingException("Error occurred while processing PDF: " + e.getMessage());
        }
    }



}

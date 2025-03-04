package com.example.pdfprocessorservice.controller;

import com.example.pdfprocessorservice.exception.PdfProcessingException;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pdf")
@Tag(name = "PDF Controller", description = "Endpoints for uploading and processing PDF files")
@Slf4j
public class PdfController {
    private final PdfProcessorService pdfProcessorService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @Operation(summary = "Upload a PDF file", description = "Uploads a PDF file for processing and analysis.")
    public ResponseEntity<Object> uploadPdfAsync(
            @RequestParam("file")
            @Parameter(description = "PDF file to be uploaded", required = true)
            MultipartFile file) throws Exception  {
        try {
            pdfProcessorService.processPdfAsync(file);
            return ResponseEntity.ok().build();
        } catch (PdfProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("1.Error occured while processing PDF: " + e.getMessage());
        }catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("2.Error occured while processing PDF: " + e.getMessage());
        }
    }


}

package com.example.pdfprocessorservice.controller;

import com.example.pdfprocessorservice.client.AiClient;
import com.example.pdfprocessorservice.service.PdfProcessorService;
import com.example.pdfprocessorservice.service.PdfStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/pdf")
@Slf4j
public class PdfController {
    private final PdfProcessorService pdfProcessorService;
    private final PdfStorageService pdfStorageService;
    private final AiClient aiClient;

    @PostMapping
    public ResponseEntity<String> uploadPdf(@RequestParam("file") MultipartFile file) throws IOException {
        String extractedText = pdfProcessorService.extractTextFromFile(file.getInputStream());
        String aiResponse = aiClient.analyzeText(extractedText);
        String storageResponse = pdfStorageService.uploadFile(file.getBytes(), file.getOriginalFilename());
        return ResponseEntity.ok("AI Yanıtı: " + aiResponse + "\n" + storageResponse);
    }

}

package com.example.aiservice.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "PDFProcessorService", url = "${pdf.processor.service.url}")
public interface PdfProcessorClient {

    @GetMapping("/api/pdf/{pdfId}/table")
    String getPdfContent(@PathVariable("pdfId") String pdfId);
}
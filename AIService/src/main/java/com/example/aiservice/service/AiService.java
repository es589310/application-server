package com.example.aiservice.service;

import com.example.aiservice.client.PdfProcessorClient;
import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiService {
    private final PdfProcessorClient pdfProcessorClient;
    private final AiProcessor aiProcessor;

    public AiResponse processPdfContent(AiRequest request) {
        // PDF Processor Service'den tablo verilerini al
        String pdfContent = pdfProcessorClient.getPdfContent(request.getPdfId());

        // AI işlemcisi ile tablo verilerini işle
        AiResponse response = aiProcessor.processContent(pdfContent);

        return response;
    }
}

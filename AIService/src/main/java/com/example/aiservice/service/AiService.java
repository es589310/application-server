package com.example.aiservice.service;

import com.example.aiservice.client.PdfProcessorClient;
import com.example.aiservice.entity.AiRequest;
import com.example.aiservice.entity.AiResponse;
import com.example.aiservice.repository.AiAnalysisRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AiService {
    private final PdfProcessorClient pdfProcessorClient;
    private final AiProcessor aiProcessor;
    private final AiAnalysisRepository aiAnalysisRepository;

    public Mono<AiResponse> processAiAnalysis(AiRequest request) {
        return Mono.fromSupplier(() -> pdfProcessorClient.getPdfContent(request.getPdfId()))
                .flatMap(pdfContent -> aiProcessor.processWithAi(pdfContent, request.getAnalysisType()))
                .flatMap(aiResponse -> {
                    // Veritabanına kaydetme
                    aiResponse.setPdfId(request.getPdfId());
                    return Mono.fromSupplier(() -> aiAnalysisRepository.save(aiResponse));
                });
    }

    public Mono<AiResponse> getLatestAnalysisByDocumentId(String pdfId) {
        return Mono.fromSupplier(() ->
                aiAnalysisRepository.findTopByPdfIdOrderByCreatedAtDesc(pdfId)
                        .orElse(null)
        );
    }

    public Mono<List<AiResponse>> findAnalysesByDocumentId(String pdfId) {
        return Mono.fromSupplier(() ->
                aiAnalysisRepository.findByPdfId(pdfId)
        );
    }

    public Mono<List<AiResponse>> findSuccessfulAnalyses() {
        return Mono.fromSupplier(() ->
                aiAnalysisRepository.findBySuccess(true)
        );
    }
}
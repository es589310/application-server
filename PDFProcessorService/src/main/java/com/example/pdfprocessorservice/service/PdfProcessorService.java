package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.client.AIClient;
import com.example.pdfprocessorservice.dto.AIAnalysisRequest;
import com.example.pdfprocessorservice.dto.AIAnalysisResponse;
import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.repository.PdfRepository;
import com.example.pdfprocessorservice.util.ImageProcessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessorService {

    private final MinioService minIOService;
    private final PdfRepository pdfRepository;
    private final AIClient aiServiceClient;
    private final Tesseract tesseract;
    private final ImageProcessor imageProcessor;

    public PdfEntity processPdf(MultipartFile file) throws IOException {
        log.info("Processing PDF: {}", file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            // PDF’nin geçerli olduğunu kontrol et
            if (document.getNumberOfPages() == 0) {
                log.warn("Invalid PDF: No pages found in file {}", file.getOriginalFilename());
                throw new IOException("Invalid PDF: No pages found");
            }

            // Tabloları çıkar (Tesseract ile, başarısızsa PDFBox ile)
            String extractedText = extractTablesWithTesseract(document);

            if (extractedText.isEmpty()) {
                log.warn("Tesseract failed to extract tables, falling back to PDFBox for file {}", file.getOriginalFilename());
                extractedText = extractTablesWithPDFBox(document);
            }

            // Aynı extractedText'e sahip bir PDF var mı kontrol et
            PdfEntity existingByText = pdfRepository.findByExtractedText(extractedText);
            if (existingByText != null) {
                log.info("PDF with same extracted text already exists: {}", existingByText.getFileName());
                return existingByText;
            }

            // Aynı fileName'e sahip PDF'ler var mı kontrol et
            List<PdfEntity> existingByFileName = pdfRepository.findByFileName(file.getOriginalFilename());
            if (!existingByFileName.isEmpty()) {
                log.warn("PDF with same file name already exists: {}", file.getOriginalFilename());
            }

            // MinIO'ya dosyayı yükle
            String filePath = saveToMinIO(file);

            // Veritabanına kaydet
            PdfEntity pdfEntity = PdfEntity.builder()
                    .fileName(file.getOriginalFilename())
                    .uploadDate(LocalDateTime.now())
                    .extractedText(extractedText)
                    .minioPath(filePath)
                    .build();

            PdfEntity savedEntity = pdfRepository.save(pdfEntity);

            // AI servisine asenkron olarak metin gönder
            analyzeTextAsync(savedEntity.getId(), extractedText);

            return savedEntity;
        } catch (IOException e) {
            log.error("Failed to process PDF {}: {}", file.getOriginalFilename(), e.getMessage());
            throw e;
        }
    }

    // PDF'den tabloları Tesseract ile çıkaran metod
    private String extractTablesWithTesseract(PDDocument document) throws IOException {
        StringBuilder tableContent = new StringBuilder();
        PDFRenderer pdfRenderer = new PDFRenderer(document);

        // Tek thread ile işleme (çökme riskini azaltmak için)
        for (int page = 0; page < document.getNumberOfPages(); page++) {
            try {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                if (image == null) {
                    log.warn("Failed to render image for page {} in file {}", page, document.getDocumentInformation().getTitle());
                    continue;
                }
                BufferedImage enhancedImage = imageProcessor.enhanceImage(image);
                String text = extractWithTesseract(enhancedImage);
                if (isTableContent(text)) {
                    tableContent.append(text).append("\n");
                }
            } catch (TesseractException e) {
                log.error("Tesseract OCR failed for page {}: {}", page, e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error processing page {}: {}", page, e.getMessage());
            }
        }

        return tableContent.toString();
    }

    // Basit tablo içeriği kontrolü
    private boolean isTableContent(String content) {
        return content != null && (content.contains("|") || content.contains("\t") || content.lines().count() > 1);
    }

    // Tesseract ile OCR yapma
    public String extractWithTesseract(BufferedImage tableImage) throws TesseractException {
        if (tableImage == null) {
            log.error("Tesseract received null image");
            return "";
        }
        try {
            return tesseract.doOCR(tableImage);
        } catch (TesseractException e) {
            log.error("Tesseract OCR failed: {}", e.getMessage());
            throw e;
        }
    }

    // MinIO'ya dosya yükleme yardımcı metodu
    public String saveToMinIO(MultipartFile file) throws IOException {
        return minIOService.uploadFile(file);
    }

    // PdfEntity'yi ID ile alma
    public PdfEntity getPdfEntityById(Long id) {
        return pdfRepository.findById(id).orElse(null);
    }

    // MinIO'dan dosyayı indirme
    public byte[] downloadFromMinIO(String minioPath) throws IOException {
        return minIOService.downloadFile(minioPath);
    }

    // PDFBox ile tablo çıkarma
    private String extractTablesWithPDFBox(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder tableContent = new StringBuilder();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            try {
                String text = stripper.getText(document);
                if (isTableContent(text)) {
                    tableContent.append(text).append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to extract text from page {}: {}", page, e.getMessage());
            }
        }
        return tableContent.toString();
    }

    // Dosya adına göre PDF'leri listeleme
    public List<PdfEntity> getPdfsByFileName(String fileName) {
        return pdfRepository.findByFileName(fileName);
    }

    // ExtractedText'e göre PDF bulma
    public PdfEntity getPdfByExtractedText(String extractedText) {
        return pdfRepository.findByExtractedText(extractedText);
    }

    // Tüm PDF'leri listeleme
    public List<PdfEntity> getAllPdfs() {
        return pdfRepository.findAll();
    }

    public void analyzeTextAsync(Long pdfId, String extractedText) {
        CompletableFuture.supplyAsync(() -> {
            AIAnalysisRequest request = new AIAnalysisRequest(
                    pdfId.toString(),
                    extractedText,
                    "METADATA_COMPLETION"
            );

            AIAnalysisResponse response = aiServiceClient.analyzeText(request);

            if (response.isSuccess() && response.getExtractedMetadata() != null) {
                Optional<PdfEntity> optionalPdf = pdfRepository.findById(pdfId);
                optionalPdf.ifPresent(pdf -> {
                    pdf.setMetadata(convertMetadataToJsonString(response.getExtractedMetadata()));
                    pdfRepository.save(pdf);
                    log.info("PDF metadata updated for ID: {}", pdfId);
                });
            } else {
                log.warn("AI analysis failed for PDF ID: {}", pdfId);
            }

            return response;
        }).exceptionally(ex -> {
            log.error("AI service failed: {}", ex.getMessage());
            return null;
        });
    }

    // Metadata'yı JSON string'ine çevirme yardımcı metodu
    private String convertMetadataToJsonString(Map<String, Object> metadata) {
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Error converting metadata to JSON", e);
            return "{}";
        }
    }
}
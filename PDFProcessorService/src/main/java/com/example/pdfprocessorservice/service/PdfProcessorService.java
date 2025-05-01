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
import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.concurrent.CompletableFuture;

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
        log.info("PDF işlənir: {}", file.getOriginalFilename());
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            if (document.getNumberOfPages() == 0) {
                log.warn("Yanlış PDF: {} faylında səhifə tapılmadı", file.getOriginalFilename());
                throw new IOException("Yanlış PDF: Səhifə tapılmadı");
            }

            String extractedText;
            try {
                extractedText = extractTablesWithTesseract(document);
            } catch (Exception e) {
                log.error("Tesseract ilə cədvəl çıxarılmasında xəta: {}", e.getMessage());
                extractedText = "";
            }

            if (!isTableContent(extractedText)) {
                log.warn("Tesseract düzgün cədvəl məzmunu çıxara bilmədi, PDFBox-a keçilir: {}", file.getOriginalFilename());
                try {
                    extractedText = extractTablesWithPDFBox(document);
                } catch (Exception e) {
                    log.error("PDFBox ilə cədvəl çıxarılmasında xəta: {}", e.getMessage());
                    extractedText = "";
                }
                if (!isTableContent(extractedText)) {
                    log.warn("PDFBox da düzgün cədvəl məzmunu çıxara bilmədi: {}", file.getOriginalFilename());
                }
            }

            if (extractedText.trim().isEmpty()) {
                log.warn("PDF-dən mətn çıxarılmadı: {}", file.getOriginalFilename());
                throw new IOException("PDF-dən mətn çıxarılmadı");
            }

            // SHA-256 hash-i hesablay
            String hash = calculateSha256(extractedText);

            // Eyni mətnə və ya hash-ə malik PDF-in olub-olmadığını yoxla
            Optional<PdfEntity> existingByText = pdfRepository.findByExtractedText(extractedText);
            if (existingByText.isPresent()) {
                log.info("Eyni çıxarılmış mətnə malik PDF artıq mövcuddur: {}", existingByText.get().getFileName());
                return existingByText.get();
            }

            Optional<PdfEntity> existingByHash = pdfRepository.findByHash(hash);
            if (existingByHash.isPresent()) {
                log.info("Eyni hash-ə malik PDF artıq mövcuddur: {}", existingByHash.get().getFileName());
                return existingByHash.get();
            }

            // Eyni fayl adına malik PDF-ləri yoxla
            List<PdfEntity> existingByFileName = pdfRepository.findByFileName(file.getOriginalFilename());
            if (!existingByFileName.isEmpty()) {
                log.warn("Eyni fayl adına malik PDF artıq mövcuddur: {}", file.getOriginalFilename());
            }

            // MinIO-ya yüklə
            String filePath;
            try {
                filePath = saveToMinIO(file);
            } catch (Exception e) {
                log.error("MinIO-ya yükləmə xətası: {}", e.getMessage());
                throw new IOException("MinIO-ya fayl yüklənmədi", e);
            }

            // PdfEntity yarat və saxla
            PdfEntity pdfEntity = PdfEntity.builder()
                    .fileName(file.getOriginalFilename())
                    .uploadDate(LocalDateTime.now())
                    .extractedText(extractedText)
                    .minioPath(filePath)
                    .hash(hash)
                    .build();

            PdfEntity savedEntity;
            try {
                savedEntity = pdfRepository.save(pdfEntity);
            } catch (Exception e) {
                log.error("PDF bazaya yazılarkən xəta: {}", e.getMessage());
                throw new IOException("PDF bazaya yazıla bilmədi", e);
            }

            // AI Service-ə asinxron sorğu göndər
            analyzeTextAsync(savedEntity.getId(), extractedText, hash);

            return savedEntity;
        } catch (IOException e) {
            log.error("PDF işlənməsi uğursuz oldu {}: {}", file.getOriginalFilename(), e.getMessage());
            throw e;
        }
    }

    private String calculateSha256(String text) {
        return DigestUtils.sha256Hex(text);
    }

    private String extractTablesWithTesseract(PDDocument document) throws IOException {
        StringBuilder tableContent = new StringBuilder();
        PDFRenderer pdfRenderer = new PDFRenderer(document);
        boolean tableStarted = false;
        StringBuilder currentRow = new StringBuilder();

        for (int page = 0; page < document.getNumberOfPages(); page++) {
            try {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                if (image == null) {
                    log.warn("{} səhifəsi üçün şəkil yaradıla bilmədi", page);
                    continue;
                }
                BufferedImage enhancedImage = imageProcessor.enhanceImage(image);
                String text = extractWithTesseract(enhancedImage);
                log.info("Tesseract xam çıxışı {} səhifəsi üçün: {}", page, text);
                String[] lines = text.split("\n");

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();

                    // Cədvəlin başlanğıcını tap
                    if (line.contains("Tarix Təyinat Məbləğ Komissiya ƏDV Balans")) {
                        tableStarted = true;
                        continue;
                    }

                    // Cədvəl başladıqdan sonra sətirləri birləşdir
                    if (tableStarted) {
                        if (line.matches("\\d{2}-\\d{2}-\\d{4}")) { // Tarix sətri
                            if (currentRow.length() > 0) {
                                tableContent.append(currentRow.toString()).append("\n");
                                currentRow.setLength(0); // Yeni sətrə keç
                            }
                            currentRow.append(line);
                        } else if (currentRow.length() > 0 && !line.isEmpty()) { // Tarixdən sonrakı sətirlər
                            currentRow.append(" ").append(line);
                        }

                        // Cədvəlin sonunu tap
                        if (line.contains("180.95")) {
                            tableContent.append(currentRow.toString()).append("\n");
                            tableStarted = false;
                            break;
                        }
                    }
                }
            } catch (TesseractException e) {
                log.error("Tesseract OCR {} səhifəsi üçün uğursuz oldu: {}", page, e.getMessage());
            }
        }

        String result = tableContent.toString().trim();
        log.info("Tesseract son çıxarılmış cədvəl: {}", result);
        return result.isEmpty() ? "" : result;
    }

    private boolean isTableContent(String content) {
        if (content == null || content.trim().isEmpty()) {
            return false;
        }

        String[] lines = content.split("\n");
        if (lines.length < 2) {
            return false;
        }

        int tableLikeLines = 0;
        for (String line : lines) {
            if (line.matches(".*\\d{2}[.-]\\d{2}[.-]\\d{4}.*") && // Tarix formatı
                    line.matches(".*[-+]?\\d+\\.\\d{2}.*")) {         // Rəqəm formatı
                tableLikeLines++;
            }
        }

        return tableLikeLines >= 2; // Ən azı 2 məlumat sətri olmalı
    }

    public String extractWithTesseract(BufferedImage tableImage) throws TesseractException {
        if (tableImage == null) {
            log.error("Tesseract null şəkil aldı");
            return "";
        }
        try {
            return tesseract.doOCR(tableImage);
        } catch (TesseractException e) {
            log.error("Tesseract OCR uğursuz oldu: {}", e.getMessage());
            throw e;
        }
    }

    public String saveToMinIO(MultipartFile file) throws IOException {
        try {
            return minIOService.uploadFile(file);
        } catch (IOException e) {
            log.error("MinIO-ya fayl yükləmə uğursuz oldu: {}", e.getMessage());
            throw e;
        }
    }

    public PdfEntity getPdfEntityById(Long id) {
        try {
            return pdfRepository.findById(id).orElse(null);
        } catch (Exception e) {
            log.error("ID ilə PdfEntity alınarkən xəta: {}", e.getMessage());
            return null;
        }
    }

    public byte[] downloadFromMinIO(String minioPath) throws IOException {
        try {
            return minIOService.downloadFile(minioPath);
        } catch (IOException e) {
            log.error("MinIO-dan fayl endirilməsi uğursuz oldu: {}", e.getMessage());
            throw e;
        }
    }

    private String extractTablesWithPDFBox(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper();
        StringBuilder tableContent = new StringBuilder();
        boolean tableStarted = false;
        StringBuilder currentRow = new StringBuilder();
        int nonTableLinesCount = 0;
        boolean headerFound = false;

        try {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                log.info("PDFBox xam çıxışı {} səhifəsi üçün: {}", page, text);
                String[] lines = text.split("\n");

                for (int i = 0; i < lines.length; i++) {
                    String line = lines[i].trim();

                    // Cədvəl başlığını tapmaq
                    if (!headerFound && isPotentialTableHeader(line)) {
                        headerFound = true;
                        log.debug("Cədvəl başlığı tapıldı: {}", line);
                        continue; // Başlığı əlavə etmirik, yalnız məlumat sətirlərini toplayırıq
                    }

                    // Cədvəlin başlanğıcını dinamik tapmaq
                    if (headerFound && !tableStarted) {
                        if (line.matches("\\d{2}[.-]\\d{2}[.-]\\d{4}.*") || // Tarix formatı
                                line.matches(".*(www\\.|http).*") ||         // Link formatı
                                line.matches(".*[-+]?\\d+\\.\\d{2}.*")) {    // Rəqəmli məbləğ formatı
                            tableStarted = true;
                            currentRow.append(line);
                            nonTableLinesCount = 0;
                            log.debug("Cədvəl başlanğıcı tapıldı: {}", line);
                        } else {
                            log.debug("Cədvəl başlanğıcı kimi qəbul edilmədi: {}", line);
                        }
                        continue;
                    }

                    // Cədvəl başladıqdan sonra
                    if (tableStarted) {
                        // Tarix, link və ya məbləğ ilə başlayan yeni sətir
                        if (line.matches("\\d{2}[.-]\\d{2}[.-]\\d{4}.*") || // Tarix formatı
                                line.matches(".*(www\\.|http).*") ||         // Link formatı
                                line.matches(".*[-+]?\\d+\\.\\d{2}.*")) {    // Rəqəmli məbləğ formatı
                            if (currentRow.length() > 0) {
                                tableContent.append(currentRow.toString()).append("\n");
                                log.debug("Tamamlanmış sətir cədvələ əlavə olundu: {}", currentRow.toString());
                                currentRow.setLength(0);
                            }
                            currentRow.append(line);
                            nonTableLinesCount = 0;
                            log.debug("Cədvələ əlavə olunan yeni sətir: {}", line);
                        }
                        // Cədvələ aid ola biləcək digər sətirlər
                        else if (!line.isEmpty() &&
                                !line.contains("Page") &&
                                !line.contains("VÖEN") &&
                                !line.contains("tel:") &&
                                !line.contains("Bank") &&
                                !line.matches(".*[A-Z]{2}\\d{2}[A-Z]{4}\\d+.*")) { // IBAN filtiri
                            if (currentRow.length() > 0) {
                                currentRow.append(" ").append(line);
                                log.debug("Mövcud sətrə əlavə olundu: {}", line);
                            }
                            nonTableLinesCount = 0;
                        }
                        // Cədvələ aid olmayan sətirlər
                        else if (!line.isEmpty()) {
                            nonTableLinesCount++;
                            log.debug("Cədvələ aid olmayan sətir: {} (nonTableLinesCount: {})", line, nonTableLinesCount);
                            if (nonTableLinesCount >= 2) { // 2 ardıcıl cədvələ aid olmayan sətir
                                if (currentRow.length() > 0) {
                                    tableContent.append(currentRow.toString()).append("\n");
                                    log.debug("Cədvəl bitdi, son sətir əlavə olundu: {}", currentRow.toString());
                                }
                                tableStarted = false;
                                headerFound = false; // Yeni cədvəl üçün başlıq axtarışını sıfırla
                                currentRow.setLength(0);
                                nonTableLinesCount = 0;
                            }
                        }
                    }
                }
            }

            // Sonuncu sətri əlavə et
            if (currentRow.length() > 0) {
                tableContent.append(currentRow.toString()).append("\n");
                log.debug("Son sətir cədvələ əlavə olundu: {}", currentRow.toString());
            }

            String result = tableContent.toString().trim();
            log.info("PDFBox son çıxarılmış cədvəl: {}", result);
            return result.isEmpty() ? "" : result;
        } catch (Exception e) {
            log.error("PDFBox ilə cədvəl çıxarılmasında xəta: {}", e.getMessage());
            throw new IOException("PDFBox ilə cədvəl çıxarılmadı", e);
        }
    }

    private boolean isPotentialTableHeader(String line) {
        String[] words = line.split("\\s+");
        int tableKeywordsCount = 0;
        String[] tableKeywords = {"Tarix", "Əməliyyat", "Məbləğ", "Mədaxil", "Məxaric",
                "Balans", "Təyinat", "Kart", "Komissiya", "ƏDV"};

        for (String word : words) {
            for (String keyword : tableKeywords) {
                if (word.equalsIgnoreCase(keyword) || word.contains(keyword)) {
                    tableKeywordsCount++;
                    break;
                }
            }
        }

        return tableKeywordsCount >= 2; // Ən azı 2 sütun adına bənzər söz
    }

    public List<PdfEntity> getPdfsByFileName(String fileName) {
        try {
            return pdfRepository.findByFileName(fileName);
        } catch (Exception e) {
            log.error("Fayl adına görə PDF-lər siyahıya alınarkən xəta: {}", e.getMessage());
            return List.of();
        }
    }

    public List<PdfEntity> getAllPdfs() {
        try {
            return pdfRepository.findAll();
        } catch (Exception e) {
            log.error("Bütün PDF-lər siyahıya alınarkən xəta: {}", e.getMessage());
            return List.of();
        }
    }

    public void analyzeTextAsync(Long pdfId, String extractedText, String hash) {
        CompletableFuture.supplyAsync(() -> {
            try {
                AIAnalysisRequest request = AIAnalysisRequest.builder()
                        .pdfId(pdfId.toString()) // Long-dan String-ə çevrilmə
                        .extractedText(extractedText)
                        .analysisType("METADATA_COMPLETION")
                        .hash(hash)
                        .build();

                AIAnalysisResponse response = aiServiceClient.analyzeText(request);

                if (response != null && response.isSuccess() && response.getExtractedMetadata() != null) {
                    Optional<PdfEntity> optionalPdf = pdfRepository.findById(pdfId);
                    optionalPdf.ifPresent(pdf -> {
                        pdf.setMetadata(convertMetadataToJsonString(response.getExtractedMetadata()));
                        pdfRepository.save(pdf);
                        log.info("PDF metadata ID üçün yeniləndi: {}", pdfId);
                    });
                } else {
                    log.warn("AI təhlili PDF ID üçün uğursuz oldu: {}", pdfId);
                }

                return response;
            } catch (Exception e) {
                log.error("AI təhlilində xəta: {}", e.getMessage());
                return null;
            }
        }).exceptionally(ex -> {
            log.error("AI xidməti uğursuz oldu: {}", ex.getMessage());
            return null;
        });
    }

    private String convertMetadataToJsonString(Map<String, Object> metadata) {
        try {
            return new ObjectMapper().writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.error("Metadata JSON-a çevrilərkən xəta", e);
            return "{}";
        }
    }
}
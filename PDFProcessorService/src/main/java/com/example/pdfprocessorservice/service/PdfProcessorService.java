package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.client.AIClient;
import com.example.pdfprocessorservice.entity.HeaderEntity;
import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.entity.ReportEntity;
import com.example.pdfprocessorservice.exception.AiServiceException;
import com.example.pdfprocessorservice.exception.FileUploadException;
import com.example.pdfprocessorservice.exception.PdfProcessingException;
import com.example.pdfprocessorservice.repository.HeaderRepository;
import com.example.pdfprocessorservice.repository.PdfRepository;
import com.example.pdfprocessorservice.repository.ReportRepository;
import com.example.pdfprocessorservice.util.CustomPDFTextStripper;
import com.example.pdfprocessorservice.util.ImageProcessor;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.ITesseract;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessorService {

    private final PdfRepository pdfRepository;
    private final ReportRepository reportRepository;
    private final HeaderRepository headerRepository;
    private final AIClient aiClient;
    private final GoogleCloudStorageService googleCloudStorageService;
    private final DatabaseMigrationService databaseMigrationService;

    @PostConstruct
    public void init() {
        log.info("Starting database schema update...");
        databaseMigrationService.updateContentColumnType();
        log.info("Database schema update completed.");
    }

    public void processPdf(MultipartFile file, String language) {
        try (InputStream fileStream = file.getInputStream()) {

            String extractedText = extractTextFromPdf(fileStream);
            if (extractedText.isEmpty()) {
                extractedText = extractTextWithOCR(fileStream, language);
            }
            log.info("Extracted text length: {}", extractedText.length());

            List<String[]> tableData = extractTableData(extractedText);

            // Başlıkları veritabanından çekiyoruz
            List<HeaderEntity> headerEntities = headerRepository.findAll();
            List<String> dynamicHeaderKeywords = new ArrayList<>();
            for (HeaderEntity headerEntity : headerEntities) {
                dynamicHeaderKeywords.add(headerEntity.getHeaderName());
            }

            log.info("Extracted table data: {} rows", tableData.size());
            log.info("Extracted text: {}", extractedText);

            PdfEntity pdfEntity = pdfRepository.findByContent(extractedText);
            if (pdfEntity == null) {
                pdfEntity = new PdfEntity();
                pdfEntity.setFileName(file.getOriginalFilename());
                pdfEntity.setContent(extractedText);
                pdfEntity.setUploadDate(LocalDateTime.now());
                pdfEntity.setKeyword("This is keywords!");
                pdfRepository.save(pdfEntity);
                log.info("PDF entity saved with ID: {}", pdfEntity.getId());

                String localAnalysisResult = analyzePdfLocally(extractedText, dynamicHeaderKeywords);
                pdfEntity.setContent(localAnalysisResult);
                pdfRepository.save(pdfEntity);

                if (localAnalysisResult.isEmpty()) {
                    String aiResult = aiClient.analyzeText(extractedText);
                    log.info("AI analysis result: {}", aiResult);

                    ReportEntity reportEntity = new ReportEntity();
                    reportEntity.setPdfEntity(pdfEntity);
                    reportEntity.setAnalysisResult(aiResult);
                    reportRepository.save(reportEntity);
                    log.info("Report entity saved with ID: {}", reportEntity.getId());
                } else {
                    log.info("No AI service needed, PDF analyzed locally.");
                }

                try {
                    String uploadedFileLink = googleCloudStorageService.uploadFile(file);
                    log.info("Uploaded file link: {}", uploadedFileLink);
                } catch (Exception e) {
                    log.error("Error uploading PDF to Google Cloud", e);
                }

                processTableData(tableData);
            } else {
                log.info("PDF content already exists in database with ID: {}", pdfEntity.getId());
            }
        } catch (IOException e) {
            log.error("Failed to process PDF", e);
            throw new PdfProcessingException("Failed to process PDF", e);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while saving PDF", e);
            throw new FileUploadException("Data integrity violation while saving PDF", e);
        } catch (FeignException e) {
            log.error("AI service error", e);
            throw new AiServiceException("AI service error", e);
        } catch (TesseractException e) {
            throw new PdfProcessingException("Failed to process PDF", e);
        }
    }

    private String extractTextFromPdf(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            CustomPDFTextStripper pdfStripper = new CustomPDFTextStripper();
            String extractedText = pdfStripper.getText(document);

            // Bold başlıqları yoxlamaq
            Map<String, Boolean> boldTextMap = pdfStripper.getBoldTextMap();
            for (Map.Entry<String, Boolean> entry : boldTextMap.entrySet()) {
                if (entry.getValue()) {
                    log.info("Bold header detected: {}", entry.getKey());
                }
            }

            return extractedText;
        } catch (IOException e) {
            throw new PdfProcessingException("Failed to extract text from PDF", e);
        }
    }


    public List<String[]> extractTableData(String text) {
        List<String[]> tableData = new ArrayList<>();
        String[] lines = text.split("\n");
        int startIndex = -1;
        List<String> possibleHeaders = new ArrayList<>();

        // Dinamik başlıqlar üçün
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();

            if (isPossibleHeader(line,possibleHeaders)) {
                possibleHeaders.add(line);
                startIndex = i + 1;
                break;
            }
        }

        //
        if (startIndex != -1) {
            for (int i = startIndex; i < lines.length; i++) {
                String line = lines[i].trim();

                if (line.isEmpty() || !isValidRow(line)) continue;

                String[] columns = line.split("\\s{2,}");
                if (columns.length >= 6) {
                    tableData.add(columns);
                }
            }
        } else {
            log.error("Table headers not found.");
        }

        //
        if (!possibleHeaders.isEmpty()) {
            log.info("Found possible headers: {}", String.join(", ", possibleHeaders));
        }

        return tableData;
    }

//    private boolean isPossibleHeader(String line) {
//        try {
//            String[] headerKeywords = {"Tarix", "Təyinat", "Məbləğ", "Komissiya", "ƏDV", "Balans"};
//
//            for (String keyword : headerKeywords) {
//                if (line.contains(keyword)) {
//                    return true;
//                }
//            }
//            return false;
//        } catch (Exception e) {
//            throw new PdfProcessingException("Failed to parse PDF header", e);
//        }
//    }

    private boolean isPossibleHeader(String line, List<String> headerKeywords) {
        try {
            for (String keyword : headerKeywords) {
                if (line.contains(keyword)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new PdfProcessingException("PDF başlığı analiz edilemedi", e);
        }
    }



    private boolean isValidRow(String line) {
        try {
            String[] columns = line.split("\\s{2,}"); // Sütunları ayırılır
            return columns.length >= 6; //6 dan çox olmamalı
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to parse PDF row", e);
        }
    }

    private String analyzePdfLocally(String extractedText, List<String> headerKeywords) {
        try {
            String analysisResult = ""; // Local analiz üçün

            for (String keyword : headerKeywords) {
                if (extractedText.contains(keyword)) {
                    analysisResult += "Title found: " + keyword + "\n";
                }
            }

            return analysisResult;
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to analyze PDF", e);
        }
    }


//    private String analyzePdfLocally(String extractedText) {
//        try {
//            String analysisResult = ""; // Local analiz için
//
//            String[] headerKeywords = {"Tarix", "Təyinat", "Məbləğ", "Komissiya", "ƏDV", "Balans"};
//            for (String keyword : headerKeywords) {
//                if (extractedText.contains(keyword)) {
//                    analysisResult += "Title found: " + keyword + "\n";
//                }
//            }
//
//            return analysisResult;
//        } catch (Exception e) {
//            throw new PdfProcessingException("Failed to analyze PDF", e);
//        }
//    }

    private void processTableData(List<String[]> tableData) {
        try {
            for (String[] row : tableData) {
                log.info("Processing row: {}", String.join(", ", row));

                reportRepository.save(new ReportEntity());
            }
        } catch (Exception e) {
            throw new PdfProcessingException("Failed to process PDF", e);
        }
    }


    // Tesseract OCR ilə PDF vizuallaşdırıb mətni çıxarmaq
    private String extractTextWithOCR(InputStream pdfStream, String language) throws IOException, TesseractException {
        try (PDDocument document = PDDocument.load(pdfStream)) {
            PDFRenderer pdfRenderer = new PDFRenderer(document);
            StringBuilder extractedText = new StringBuilder();

            for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                BufferedImage image = pdfRenderer.renderImage(pageIndex);
                BufferedImage processedImage = preprocessImageForOCR(image);
                extractedText.append(extractTextFromImage(processedImage, language));
            }
            return extractedText.toString();
        } catch (IOException e) {
            throw new PdfProcessingException("Failed to extract text from PDF", e);
        }
    }

    private BufferedImage preprocessImageForOCR(BufferedImage image) {
        // Burada şəkilin kontrastı artırılıb, binarizasiya (s/q) edilir
        return new ImageProcessor().binarizeAndEnhance(image);
    }

    private String extractTextFromImage(BufferedImage image, String language) throws IOException {
        try {
            ITesseract tesseract = new Tesseract();
            tesseract.setLanguage(language);


            // Parametrləri birbaşa `setTessVariable()` olmadan konfiqurasiya faylına yazın
            tesseract.setDatapath("/usr/share/tesseract-ocr/4.00/tessdata"); // Tesseract data yolunu yoxlayın
            tesseract.setTessVariable("tessedit_char_whitelist", "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz,.-");

            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            throw new PdfProcessingException("Failed to extract text from image", e);
        }
    }


}
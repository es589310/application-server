package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.client.AIClient;
import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.entity.ReportEntity;
import com.example.pdfprocessorservice.exception.AiServiceException;
import com.example.pdfprocessorservice.exception.FileUploadException;
import com.example.pdfprocessorservice.exception.PdfProcessingException;
import com.example.pdfprocessorservice.repository.PdfRepository;
import com.example.pdfprocessorservice.repository.ReportRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
@Async
public class PdfProcessorService {

    private final PdfRepository pdfRepository;
    private final ReportRepository reportRepository;
    private final AIClient aiClient;
    private final GoogleCloudStorageService googleCloudStorageService;
    private final DatabaseMigrationService databaseMigrationService;

    @PostConstruct
    public void init(){
        log.info("Starting database schema update...");
        databaseMigrationService.updateContentColumnType();
        log.info("Database schema update completed.");
    }


    public void processPdf(MultipartFile file) throws Exception {
        try (InputStream fileStream = file.getInputStream()){

            String extractedText = extractTextFromPdf(fileStream); //PDFTextStripper ilə PDF-dən mətni çıxarır
            log.info("Extracted text length: {}", extractedText.length());

            List<String[]> tableData = extractTableData(extractedText);
            log.info("Extracted table data: {} rows", tableData.size());
            log.info("Extracted text: {}", extractedText);


            // PDFEntity-də mətnin olub olmamasını yoxlayır
            PdfEntity pdfEntity = pdfRepository.findByContent(extractedText);
            if (pdfEntity == null) { // Eyni pdf-in olub olmadığını yoxlayır
                pdfEntity = new PdfEntity();
                pdfEntity.setFileName(file.getOriginalFilename());
                pdfEntity.setContent(extractedText);
                pdfEntity.setUploadDate(LocalDateTime.now());
                pdfRepository.save(pdfEntity);
                log.info("PDF entity saved with ID: {}", pdfEntity.getId());
                log.error("PDF entity saved error: {}", pdfEntity.getId());
                log.error("Failed to save PDF entity: {}", pdfEntity);



                String localAnalysisResult = analyzePdfLocally(extractedText);
                pdfEntity.setContent(localAnalysisResult);

                pdfRepository.save(pdfEntity);


                // localanaliz boşdursa ai analizi edəcək, əks təqdirdə etməyəcək
                if (localAnalysisResult.isEmpty()) {
                    String aiResult = aiClient.analyzeText(extractedText);
                    log.info("65 - AI analysis result: {}", aiResult);

                    ReportEntity reportEntity = new ReportEntity();
                    reportEntity.setPdfEntity(pdfEntity);
                    reportEntity.setAnalysisResult(aiResult);
                    reportRepository.save(reportEntity);
                    log.info("Report entity saved with ID: {}", reportEntity.getId());
                } else {
                    log.info("No AI service needed, PDF analyzed locally.");
                }


                // PDF-in Google Cloud Storage-ə yüklənməsi
                String uploadedFileLink = googleCloudStorageService.uploadFile(file);
                log.info("Uploaded file link: {}", uploadedFileLink);
                log.error("Error uploading PDF to Google Cloud: {}", uploadedFileLink);


                processTableData(tableData); // Table dataları üstündə dövr etməli
            }else {
                log.info("PDF content already exists in database with ID: {}", pdfEntity.getId());
            }
        } catch (IOException e) {
            log.error("Failed to process PDF", e);
            throw new PdfProcessingException("71 - Failed to process PDF", e);
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while saving PDF", e);
            throw new FileUploadException("74 - Data integrity violation while saving PDF", e);
        } catch (FeignException e){
            log.error("AI service error", e);
            throw new AiServiceException("AI service error", e);
        }
    }



    private String extractTextFromPdf(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) { // PDF faylını götürür, içini açır və oxuyur
            PDFTextStripper pdfStripper = new PDFTextStripper(); // PDF-dən mətni çıxarmaq üçün ApachePdf Box-un PDFTextStripper-i
            return pdfStripper.getText(document);
        }
    }



    public List<String[]> extractTableData(String text) throws IOException {
        List<String[]> tableData = new ArrayList<>(); // Table məlumatlarını tutmaq üçün list hazırlanır

        String[] lines = text.split("\n"); //regex ilə mətni sətirlərə ayrırır

        // table başlıqlarının axtarışı
        int startIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].contains("Tarix") && lines[i].contains("Təyinat") &&
                    lines[i].contains("Məbləğ") && lines[i].contains("Komissiya") &&
                    lines[i].contains("ƏDV") && lines[i].contains("Balans")) {
                startIndex = i + 1;
                break;
            }
        }

        // Dataları çıxarmaq üçün
        if (startIndex != -1) {
            for (int i = startIndex; i < lines.length; i++) { // Sətirləri yoxlayır
                String line = lines[i].trim(); // sətirdəki lazımsız boşluqları silmək üçün
                if (line.isEmpty()) continue;

                // Sətiri sütuna ayırmalı
                String[] columns = line.split("\\s{2,}"); // İki və daha çox boşluğa görə bölünmə

                if (columns.length >= 6) {
                    tableData.add(columns);
                }
            }
        } else {
            log.error("No columns found in text");
        }

        return tableData;
    }



    public void processTableData(List<String[]> tableData){
        for (String[] row : tableData) { // Table datarında dövr etməli
            log.info("Table Row: {}", String.join(", ", row));
        }
    }



    private String analyzePdfLocally(String text) {
        int wordCount = text.split("\\s+").length;
        if (wordCount > 1000) {
            return "Bu PDF-də 1000-dən çox element var.";
        } else if (wordCount > 500) {
            return "Bu PDF 500 və 1000 arası elementi var.";
        } else {
            return "Bu PDF qısadır";
        }
    }




    @Async("taskExecutor")
    public void processPdfAsync(MultipartFile file) throws Exception{
        processPdf(file);
    }
}

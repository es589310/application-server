package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.client.AIClient;
import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.entity.ReportEntity;
import com.example.pdfprocessorservice.repository.PdfRepository;
import com.example.pdfprocessorservice.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessorService {

    private final PdfRepository pdfRepository;
    private final ReportRepository reportRepository;
    private final AIClient aiClient;
    private final GoogleCloudStorageService googleCloudStorageService;

    public void processPdf(MultipartFile file) throws Exception {
        try {
            String extractedText = extractTextFromPdf(file.getInputStream()); //PDFTextStripper ilə PDF-dən mətni çıxarır
            log.info("Extracted text length: {}", extractedText.length());

            List<String[]> tableData = extractTableData(file.getInputStream());
            log.info("Extracted table data: {} rows", tableData.size());

            // PDFEntity'de metin var mı kontrol et
            PdfEntity pdfEntity = pdfRepository.findByContent(extractedText);
            if (pdfEntity == null) { // Eğer veritabanında aynı içerik yoksa yeni PDF oluştur
                pdfEntity = new PdfEntity();
                pdfEntity.setFileName(file.getOriginalFilename()); // Dosya adını kaydet
                pdfEntity.setContent(extractedText); // Çıkarılan metni kaydet
                pdfEntity.setUploadDate(LocalDateTime.now()); // Yükleme tarihini kaydet
                pdfRepository.save(pdfEntity); // PDF verisini veritabanına kaydet
                log.info("PDF entity saved with ID: {}", pdfEntity.getId());

                // AI servisine gönder ve sonucu al
                String aiResult = aiClient.analyzeText(extractedText);
                log.info("AI analysis result: {}", aiResult);

                // AI sonucu ReportEntity olarak kaydet
                ReportEntity reportEntity = new ReportEntity();
                reportEntity.setPdfEntity(pdfEntity); // İlgili PDF ile ilişkilendir
                reportEntity.setAnalysisResult(aiResult); // Analiz sonucunu kaydet
                reportRepository.save(reportEntity); // Raporu veritabanına kaydet
                log.info("Report entity saved with ID: {}", reportEntity.getId());

                // PDF'i Google Cloud Storage'a yükle
                String uploadedFileLink = googleCloudStorageService.uploadFile(file);
                log.info("Uploaded file link: {}", uploadedFileLink); // Yüklenen dosyanın linkini logla

                // Table data işle
                processTableData(tableData); // Tablo verilerini işle
            }else {
                log.info("PDF content already exists in database with ID: {}", pdfEntity.getId());
            }
        } catch (IOException e) { // IOException durumunda hata fırlat
            log.error("Failed to process PDF", e);
            throw new RuntimeException("Failed to process PDF", e); // Hata mesajı ile birlikte fırlat
        } catch (DataIntegrityViolationException e) {
            log.error("Data integrity violation while saving PDF", e);
            throw new RuntimeException("Data integrity violation while saving PDF", e);
        }
    }

    private String extractTextFromPdf(InputStream pdfStream) throws IOException {
        try (PDDocument document = PDDocument.load(pdfStream)) { // PDF faylını götürür, içini açır və oxuyur
            PDFTextStripper pdfStripper = new PDFTextStripper(); // PDF-dən mətni çıxarmaq üçün ApachePdf Box-un PDFTextStripper-i
            return pdfStripper.getText(document);
        }
    }

    public List<String[]> extractTableData(InputStream pdfStream) throws IOException {
        List<String[]> tableData = new ArrayList<>(); // Tablo verilerini saklamak için liste

        try (PDDocument document = PDDocument.load(pdfStream)) { // PDF dosyasını yükle
            PDFTextStripper pdfStripper = new PDFTextStripper(); // PDF'den metin çıkar
            String text = pdfStripper.getText(document); // Metni al

            // Metni satırlara ayır
            String[] lines = text.split("\n");

            // Başlıkları bul
            int startIndex = -1;
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].contains("Tarix") && lines[i].contains("Təyinat") && lines[i].contains("Məbləğ") && lines[i].contains("Balans")) {
                    startIndex = i + 1; // Başlık satırının bir altından başla
                    break;
                }
            }

            // Verileri çıkar
            if (startIndex != -1) { // Başlık bulunmuşsa
                for (int i = startIndex; i < lines.length; i++) { // Satırlarda dolaş
                    String line = lines[i].trim(); // Satırı temizle
                    if (line.isEmpty()) continue; // Eğer satır boşsa, geç

                    // Satırı sütunlara ayır
                    String[] columns = line.split("\\s{2,}"); // İki veya daha fazla boşlukla ayır
                    if (columns.length >= 4) { // En az 4 sütun olmalı
                        tableData.add(columns); // Veriyi listeye ekle
                    }
                }
            }
        }

        return tableData; // Tablo verilerini döndür
    }

    public void processTableData(List<String[]> tableData) throws Exception {
        for (String[] row : tableData) { // Tablo verileri üzerinde dön
            log.info("Table Row: {}", String.join(", ", row)); // Her satırı logla
        }
    }
}

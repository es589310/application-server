package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.client.AIClient;
import com.example.pdfprocessorservice.entity.PdfEntity;
import com.example.pdfprocessorservice.repository.PdfRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessRead;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.beans.factory.aot.AotServices;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfProcessorService {

    private final MinioService minIOService;
    private final PdfRepository pdfRepository;
    private final AIClient aiServiceClient;
    private final Tesseract tesseract;

    // PDF dosyasını işleyen ana metod
    public PdfEntity processPdf(MultipartFile file) throws IOException {
        try (InputStream inputStream = file.getInputStream();
             PDDocument document = Loader.loadPDF(inputStream.readAllBytes())) {

            // Tabloları çıkar (Tesseract ile, başarısızsa PDFBox ile)
            String extractedText;
            try {
                extractedText = extractTablesWithTesseract(document);
                if (extractedText.isEmpty()) {
                    log.warn("Tesseract failed to extract tables, falling back to PDFBox");
                    extractedText = extractTablesWithPDFBox(document);
                }
            } catch (Exception e) {
                log.error("Tesseract failed: {}, using PDFBox as fallback", e.getMessage());
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

            // AI servisine gönder
            aiServiceClient.analyzeText(extractedText);

            return savedEntity;
        }
    }

    // PDF'den tabloları Tesseract ile çıkaran metod
    private String extractTablesWithTesseract(PDDocument document) throws IOException {
        StringBuilder tableContent = new StringBuilder();
        PDFRenderer pdfRenderer = new PDFRenderer(document);

        for (int page = 0; page < document.getNumberOfPages(); page++) {
            try {
                BufferedImage image = pdfRenderer.renderImageWithDPI(page, 300, ImageType.RGB);
                String text = extractWithTesseract(image);
                if (isTableContent(text)) {
                    tableContent.append(text).append("\n");
                }
            } catch (TesseractException e) {
                log.error("Tesseract OCR failed for page {}: {}", page, e.getMessage());
            }
        }
        return tableContent.toString();
    }

    // Basit tablo içeriği kontrolü
    private boolean isTableContent(String content) {
        return content.contains("|") || content.contains("\t") || content.lines().count() > 1;
    }

    // Tesseract ile OCR yapma
    public String extractWithTesseract(BufferedImage tableImage) throws TesseractException {
        return tesseract.doOCR(tableImage);
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

    // PDFBox ile tablo çıkarma (artık bağlı)
    private String extractTablesWithPDFBox(PDDocument document) throws IOException {
        PDFTextStripper stripper = new PDFTextStripper() {
            @Override
            protected void processTextPosition(TextPosition text) {
                String content = text.getUnicode();
                if (isTableContent(content)) {
                    super.processTextPosition(text);
                }
            }
        };

        StringBuilder tableContent = new StringBuilder();
        for (int page = 1; page <= document.getNumberOfPages(); page++) {
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            tableContent.append(stripper.getText(document));
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
}
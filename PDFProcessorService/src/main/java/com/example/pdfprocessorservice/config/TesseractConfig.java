package com.example.pdfprocessorservice.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractConfig {
    @Value("${tesseract.data-path:/app/tessdata}")
    private String tessdataPath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        // Varsayılan dil olarak İngilizce ayarlanabilir, gerektiğinde değiştirilebilir
        tesseract.setLanguage("eng");
        tesseract.setLanguage("aze");
        tesseract.setLanguage("rus");
        // İsteğe bağlı: Daha iyi tablo okuma için özel ayarlar
        tesseract.setPageSegMode(6); // Blok tabanlı segmentasyon
        return tesseract;
    }
}

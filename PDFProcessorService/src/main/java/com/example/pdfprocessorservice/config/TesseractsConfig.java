package com.example.pdfprocessorservice.config;

import net.sourceforge.tess4j.Tesseract;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TesseractsConfig {

    @Value("${tesseracts.data-path:src/main/resources/tessdata}")
    private String tessdataPath;

    @Bean
    public Tesseract tesseract() {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataPath);
        tesseract.setLanguage("eng+aze+aze_cyrl+rus");
        tesseract.setPageSegMode(6);
        return tesseract;
    }
}


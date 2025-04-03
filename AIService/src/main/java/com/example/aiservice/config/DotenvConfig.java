package com.example.aiservice.config;

import io.github.cdimascio.dotenv.Dotenv;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;


@Configuration
public class DotenvConfig {

    @PostConstruct
    public void loadDotenv() {
        try {
            Dotenv dotenv = Dotenv.configure()
                    .directory("/home/mrdoc/IdeaProjects/application-server/AIService") // Proje kök dizini
                    .ignoreIfMissing() // Dosya yoksa hata verme
                    .load();
            dotenv.entries().forEach(entry -> {
                System.out.println("Yüklenen .env değişkeni: " + entry.getKey() + "=" + entry.getValue());
                System.setProperty(entry.getKey(), entry.getValue());
            });
        } catch (Exception e) {
            System.err.println(".env dosyası yüklenirken hata oluştu: " + e.getMessage());
        }
    }
}
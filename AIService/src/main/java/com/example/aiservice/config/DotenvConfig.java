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
                    .directory("/home/mrdoc/IdeaProjects/application-server/AIService")
                    .ignoreIfMissing() // Fayl yoxdusa xəta verməməyi üçün
                    .load();
            dotenv.entries().forEach(entry -> {
                System.out.println("Yüklənən .env varaible: " + entry.getKey() + "=" + entry.getValue());
                System.setProperty(entry.getKey(), entry.getValue());
            });
        } catch (Exception e) {
            System.err.println(".env faylı yüklənərkən xəta oldu: " + e.getMessage());
        }
    }
}
package com.example.pdfprocessorservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10); // Temel thread sayısı
        executor.setMaxPoolSize(50); // Maksimum thread sayısı
        executor.setQueueCapacity(100); // Bekleyen işler için kuyruk kapasitesi
        executor.setThreadNamePrefix("Async-");
        executor.initialize();
        return executor;
    }
}

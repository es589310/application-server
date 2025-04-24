package com.example.aiservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AsyncConfig {
    @Bean(name = "aiTaskExecutor")
    public Executor aiTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4); // Aynı anda çalışacak iş parçacığı sayısı
        executor.setMaxPoolSize(10); // Maksimum iş parçacığı sayısı
        executor.setQueueCapacity(50); // Kuyruk kapasitesi
        executor.setThreadNamePrefix("AiTask-"); // İş parçacığı isim ön eki
        executor.setWaitForTasksToCompleteOnShutdown(true); // Kapanırken görevlerin tamamlanmasını bekle
        executor.setAwaitTerminationSeconds(60); // Maksimum bekleme süresi
        executor.initialize();
        return executor;
    }
}

package com.example.pdfprocessorservice.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("PDF Processor API")
                        .version("1.0")
                        .description("API for processing PDF files and analyzing their content."));
    }

    @Bean
    public GroupedOpenApi pdfApi() {
        return GroupedOpenApi.builder()
                .group("pdf-api")
                .packagesToScan("com.example.pdfprocessorservice.controller")
                .build();
    }
}

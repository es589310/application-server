package com.example.pdfprocessorservice.config;

import io.swagger.v3.oas.models.ExternalDocumentation;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI catalogServiceAPI(){
        return new OpenAPI()
                .info(new Info().title("Catalog Service API")
                        .description("Catalog halında ümumi qeyd edən servis API")
                        .version("v.0.0.1")
                        .license(new License().name("Apache 2.0")))
                .externalDocs(new ExternalDocumentation()
                        .description("Siz Catalog üzrə məhsul yığma xidmətinin Wiki Sənədlərinə müraciət edə bilərsiniz")
                        .url("http://catalogs-service-dummy-url.com/docs"));
    }

//    @Bean
//    public OpenAPI customOpenAPI() {
//        return new OpenAPI()
//                .info(new Info()
//                        .title("API Documentation")
//                        .version("1.0")
//                        .description("API Documentation for the Security Demo Application")
//                )
//                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
//                .components(new io.swagger.v3.oas.models.Components()
//                        .addSecuritySchemes("bearerAuth",
//                                new SecurityScheme()
//                                        .type(SecurityScheme.Type.HTTP)
//                                        .scheme("bearer")
//                                        .bearerFormat("JWT")
//                        )
//                );
//    }
}

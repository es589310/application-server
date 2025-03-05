package com.example.pdfprocessorservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PdfProcessorServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PdfProcessorServiceApplication.class, args);
	}

}

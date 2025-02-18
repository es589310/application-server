package com.example.pdfprocessorservice.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfStorageService {

    public String uploadFile(byte[] file, String fileName) {
        return "PDF file: " + Arrays.toString(file) + fileName;
    }

}

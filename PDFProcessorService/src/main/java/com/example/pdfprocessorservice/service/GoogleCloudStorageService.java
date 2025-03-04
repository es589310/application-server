package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.exception.FileUploadException;
import com.example.pdfprocessorservice.exception.GoogleCloudStorageException;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCloudStorageService {

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    public String uploadFile(MultipartFile file) throws IOException {
        String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            byte[] fileBytes = file.getBytes();

            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName)
                    .setContentType(file.getContentType())
                    .build();

            try (InputStream fileInputStream = new ByteArrayInputStream(fileBytes)) {
                Blob blob = storage.create(blobInfo, fileInputStream);
                log.info("Dosya {} bucket'e yüklendi: {}", fileName, bucketName);
                return blob.getMediaLink();
            }
        } catch (IOException e) {
            log.error("Google Cloud Storage'a dosya yüklenemedi: {}", fileName, e);
            throw new FileUploadException("Dosya yükleme hatası: " + fileName, e);
        } catch (Exception e) {
            log.error("Dosya yükleme sırasında beklenmeyen hata: {}", fileName, e);
            throw new GoogleCloudStorageException("Dosya yükleme sırasında beklenmeyen hata: " + fileName, e);
        }
    }
}
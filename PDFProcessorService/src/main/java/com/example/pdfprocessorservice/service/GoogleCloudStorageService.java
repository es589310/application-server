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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleCloudStorageService {

    @Value("${spring.cloud.gcp.storage.bucket}")
    private String bucketName;

    private final Storage storage = StorageOptions.getDefaultInstance().getService();

    // müvəqqəti fayl saxlamaq üçün
    private final String TEMP_DIR = "tmp/pdfs/";

    public String uploadFile(MultipartFile file) throws IOException {
        // Faylın adı üçün
        String fileName = UUID.randomUUID() + "-" + file.getOriginalFilename();

        // müvəqqəti fayl saxlamaq üçün adresi yoxlayıb qəbul etməli
        File tempDir = new File(TEMP_DIR);
        if (!tempDir.exists()) {
            boolean created = tempDir.mkdirs(); // Dizin oluşturuluyor
            if (!created) {
                log.error("Unable to create directory for temp files: {}", TEMP_DIR);
                throw new IOException("Failed to create temp directory");
            }
        }

        // müvəqqəti fayl saxlama
        File tempFile = new File(tempDir, fileName);
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(file.getBytes());
        } catch (IOException e) {
            log.error("Failed to write file to temp directory: {}", fileName, e);
            throw new FileUploadException("Failed to write file to temp directory: " + fileName, e);
        }

        try {
            // Google Cloud Storage-ə faylı əlavə etmək
            BlobInfo blobInfo = BlobInfo.newBuilder(bucketName, fileName)
                    .setContentType(file.getContentType())
                    .build();

            Blob blob = storage.create(blobInfo, file.getBytes());  // BLob ilə faylı göndərir
            log.info("File {} uploaded to bucket {}", fileName, bucketName);

            return blob.getMediaLink();
        } catch (IOException e) {
            log.error("Failed to upload file to Google Cloud Storage: {}", fileName, e);
            throw new FileUploadException("File upload failed for: " + fileName, e);
        } catch (Exception e) {
            log.error("Unexpected error occurred during file upload: {}", fileName, e);
            throw new GoogleCloudStorageException("Unexpected error occurred while uploading file: " + fileName, e);
        }
    }

}

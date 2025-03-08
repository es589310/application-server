package com.example.pdfprocessorservice.service;

import com.example.pdfprocessorservice.exception.FileUploadException;
import com.example.pdfprocessorservice.exception.GoogleCloudStorageException;
import com.google.cloud.storage.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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

            BlobId blobId = BlobId.of(bucketName, fileName);
            BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(file.getContentType()).build();
            try (InputStream fileInputStream = new ByteArrayInputStream(fileBytes)) {
                Blob blob = storage.create(blobInfo, fileInputStream);
                log.info("File uploaded to {} bucket: {}", fileName, bucketName);
                return blob.getSelfLink();
            }
        } catch (IOException e) {
            log.error("Unable to upload file to Google Cloud Storage: {}", fileName, e);
            throw new FileUploadException("File upload error: " + fileName, e);
        } catch (Exception e) {
            log.error("Unexpected error during file upload: {}", fileName, e);
            throw new GoogleCloudStorageException("Unexpected error during file upload: " + fileName, e);
        }
    }
}
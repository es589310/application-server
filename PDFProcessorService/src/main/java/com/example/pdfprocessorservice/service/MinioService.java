package com.example.pdfprocessorservice.service;

import io.minio.MinioClient;
import io.minio.errors.MinioException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MinioService {

    private final MinioClient minioClient;

    @Value("${minio.bucket-name}")
    private String bucketName;

    // Fayl yükləmə metodu
    public String uploadFile(MultipartFile file) throws IOException {
        try {
            // Bucket yoxlaması
            boolean bucketExists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
                return "Bucket again created";
            }

            // Unikal fayl adı yaradılır
            String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // MultipartFile-dan InputStream alınır
            try (InputStream fileStream = file.getInputStream()) {
                minioClient.putObject(
                        io.minio.PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(uniqueFileName)
                                .stream(fileStream, file.getSize(), -1)
                                .build());
            }

            // Verilənlər bazasında yalnız fayl adını saxlayacağıq
            return uniqueFileName;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("MinIO yükləməsi uğursuz oldu: " + e.getMessage(), e);
        }
    }

    // Fayl endirmə metodu
    public byte[] downloadFile(String objectName) throws IOException {
        try {
            // MinIO-dan faylı al və resursları təmizlə
            try (InputStream stream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("MinIO-dan fayl endirilməsi uğursuz oldu: " + e.getMessage(), e);
        }
    }
}
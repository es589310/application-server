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

    // Dosya yükleme metodu
    public String uploadFile(MultipartFile file) throws IOException {
        try {
            // Bucket kontrolü
            boolean bucketExists = minioClient.bucketExists(
                    io.minio.BucketExistsArgs.builder().bucket(bucketName).build());
            if (!bucketExists) {
                minioClient.makeBucket(
                        io.minio.MakeBucketArgs.builder().bucket(bucketName).build());
            }

            // Benzersiz dosya adı oluştur
            String uniqueFileName = UUID.randomUUID() + "_" + file.getOriginalFilename();

            // MultipartFile'dan InputStream alıyoruz
            try (InputStream fileStream = file.getInputStream()) {
                minioClient.putObject(
                        io.minio.PutObjectArgs.builder()
                                .bucket(bucketName)
                                .object(uniqueFileName)
                                .stream(fileStream, file.getSize(), -1)
                                .build());
            }

            // Veritabanında sadece dosya adını saklayacağız
            return uniqueFileName;
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("MinIO upload failed: " + e.getMessage(), e);
        }
    }

    // Dosya indirme metodu
    public byte[] downloadFile(String objectName) throws IOException {
        try {
            // MinIO'dan dosyayı al ve kaynakları temizle
            try (InputStream stream = minioClient.getObject(
                    io.minio.GetObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build())) {
                return stream.readAllBytes();
            }
        } catch (MinioException | InvalidKeyException | NoSuchAlgorithmException e) {
            throw new IOException("Failed to download file from MinIO: " + e.getMessage(), e);
        }
    }
}
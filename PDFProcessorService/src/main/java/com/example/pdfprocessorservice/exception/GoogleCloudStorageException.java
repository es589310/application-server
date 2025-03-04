package com.example.pdfprocessorservice.exception;

public class GoogleCloudStorageException extends RuntimeException{
    public GoogleCloudStorageException(String message) {
        super(message);
    }

    public GoogleCloudStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}

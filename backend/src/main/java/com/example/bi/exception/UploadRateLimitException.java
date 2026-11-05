package com.example.bi.exception;

public class UploadRateLimitException extends RuntimeException {
    public UploadRateLimitException(String message) {
        super(message);
    }
}


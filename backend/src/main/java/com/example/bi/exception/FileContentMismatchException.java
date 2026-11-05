package com.example.bi.exception;

public class FileContentMismatchException extends RuntimeException {
    public FileContentMismatchException(String message) {
        super(message);
    }
}


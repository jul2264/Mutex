package com.example.bi.exception;

public class FileValidationException extends RuntimeException {
    private final String code;

    public FileValidationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

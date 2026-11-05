package com.example.bi.exception;

public class ImportInProgressException extends RuntimeException {
    public ImportInProgressException(String message) {
        super(message);
    }
}

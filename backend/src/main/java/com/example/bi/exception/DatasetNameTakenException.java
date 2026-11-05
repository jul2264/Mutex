package com.example.bi.exception;

public class DatasetNameTakenException extends RuntimeException {
    public DatasetNameTakenException(String message) {
        super(message);
    }
}

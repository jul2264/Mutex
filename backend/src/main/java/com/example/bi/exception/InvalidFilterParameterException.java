package com.example.bi.exception;

public class InvalidFilterParameterException extends RuntimeException {
    public InvalidFilterParameterException(String message) {
        super(message);
    }
}


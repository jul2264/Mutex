package com.example.bi.exception;

import com.example.bi.dto.FieldErrorDto;

import java.util.List;

public class ValidationException extends RuntimeException {

    private final List<FieldErrorDto> errors;

    public ValidationException(String message, List<FieldErrorDto> errors) {
        super(message);
        this.errors = errors != null ? errors : List.of();
    }

    public List<FieldErrorDto> getErrors() {
        return errors;
    }
}


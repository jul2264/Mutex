package com.example.bi.web;

import com.example.bi.dto.FieldErrorDto;
import com.example.bi.exception.DatasetNotFoundException;
import com.example.bi.exception.EmailAlreadyRegisteredException;
import com.example.bi.exception.ValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(DatasetNotFoundException.class)
    public ProblemDetail handleDatasetNotFound(DatasetNotFoundException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Dataset Not Found");
        problem.setType(URI.create("https://example.com/errors/DATASET_NOT_FOUND"));
        problem.setProperty("code", "DATASET_NOT_FOUND");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(EmailAlreadyRegisteredException.class)
    public ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Email Already Registered");
        problem.setType(URI.create("https://example.com/errors/EMAIL_ALREADY_REGISTERED"));
        problem.setProperty("code", "EMAIL_ALREADY_REGISTERED");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(ValidationException.class)
    public ProblemDetail handleValidationException(ValidationException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://example.com/errors/VALIDATION_FAILED"));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("requestId", requestId);
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        List<FieldErrorDto> errors = new ArrayList<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            errors.add(new FieldErrorDto(fe.getField(), "INVALID_" + fe.getField().toUpperCase(), fe.getDefaultMessage()));
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed for one or more fields");
        problem.setTitle("Validation Failed");
        problem.setType(URI.create("https://example.com/errors/VALIDATION_FAILED"));
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("requestId", requestId);
        problem.setProperty("errors", errors);
        return problem;
    }

    public static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}

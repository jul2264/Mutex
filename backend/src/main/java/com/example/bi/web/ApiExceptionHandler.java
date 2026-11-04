package com.example.bi.web;

import com.example.bi.exception.DatasetNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
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

    private String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}


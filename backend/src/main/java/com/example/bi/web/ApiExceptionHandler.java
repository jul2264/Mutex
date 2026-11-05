package com.example.bi.web;

import com.example.bi.dto.FieldErrorDto;
import com.example.bi.exception.*;
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

    @ExceptionHandler(FileEmptyException.class)
    public ProblemDetail handleFileEmpty(FileEmptyException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("File Empty");
        problem.setType(URI.create("https://example.com/errors/FILE_EMPTY"));
        problem.setProperty("code", "FILE_EMPTY");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(FileTooLargeException.class)
    public ProblemDetail handleFileTooLarge(FileTooLargeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE, ex.getMessage());
        problem.setTitle("File Too Large");
        problem.setType(URI.create("https://example.com/errors/FILE_TOO_LARGE"));
        problem.setProperty("code", "FILE_TOO_LARGE");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ProblemDetail handleUnsupportedFileType(UnsupportedFileTypeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setTitle("Unsupported File Type");
        problem.setType(URI.create("https://example.com/errors/UNSUPPORTED_FILE_TYPE"));
        problem.setProperty("code", "UNSUPPORTED_FILE_TYPE");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(FileContentMismatchException.class)
    public ProblemDetail handleFileContentMismatch(FileContentMismatchException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setTitle("File Content Mismatch");
        problem.setType(URI.create("https://example.com/errors/FILE_CONTENT_MISMATCH"));
        problem.setProperty("code", "FILE_CONTENT_MISMATCH");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(EncodingUnsupportedException.class)
    public ProblemDetail handleEncodingUnsupported(EncodingUnsupportedException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage());
        problem.setTitle("Encoding Unsupported");
        problem.setType(URI.create("https://example.com/errors/ENCODING_UNSUPPORTED"));
        problem.setProperty("code", "ENCODING_UNSUPPORTED");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(DatasetNameTakenException.class)
    public ProblemDetail handleDatasetNameTaken(DatasetNameTakenException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Dataset Name Taken");
        problem.setType(URI.create("https://example.com/errors/DATASET_NAME_TAKEN"));
        problem.setProperty("code", "DATASET_NAME_TAKEN");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(ImportInProgressException.class)
    public ProblemDetail handleImportInProgress(ImportInProgressException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Import In Progress");
        problem.setType(URI.create("https://example.com/errors/IMPORT_IN_PROGRESS"));
        problem.setProperty("code", "IMPORT_IN_PROGRESS");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(UploadRateLimitException.class)
    public ProblemDetail handleUploadRateLimit(UploadRateLimitException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage());
        problem.setTitle("Upload Rate Limit Exceeded");
        problem.setType(URI.create("https://example.com/errors/RATE_LIMIT_EXCEEDED"));
        problem.setProperty("code", "RATE_LIMIT_EXCEEDED");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(ServerBusyException.class)
    public ProblemDetail handleServerBusy(ServerBusyException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Server Busy");
        problem.setType(URI.create("https://example.com/errors/SERVER_BUSY"));
        problem.setProperty("code", "SERVER_BUSY");
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

    @ExceptionHandler(ImportNotConfirmableException.class)
    public ProblemDetail handleImportNotConfirmable(ImportNotConfirmableException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Import Not Confirmable");
        problem.setType(URI.create("https://example.com/errors/IMPORT_NOT_CONFIRMABLE"));
        problem.setProperty("code", "IMPORT_NOT_CONFIRMABLE");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(ImportNotCancelableException.class)
    public ProblemDetail handleImportNotCancelable(ImportNotCancelableException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Import Not Cancelable");
        problem.setType(URI.create("https://example.com/errors/IMPORT_NOT_CANCELABLE"));
        problem.setProperty("code", "IMPORT_NOT_CANCELABLE");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(StrictPolicyBlockedException.class)
    public ProblemDetail handleStrictPolicyBlocked(StrictPolicyBlockedException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Strict Policy Blocked");
        problem.setType(URI.create("https://example.com/errors/STRICT_POLICY_BLOCKED"));
        problem.setProperty("code", "STRICT_POLICY_BLOCKED");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(NothingToImportException.class)
    public ProblemDetail handleNothingToImport(NothingToImportException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Nothing To Import");
        problem.setType(URI.create("https://example.com/errors/NOTHING_TO_IMPORT"));
        problem.setProperty("code", "NOTHING_TO_IMPORT");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(DatasetNameMismatchException.class)
    public ProblemDetail handleDatasetNameMismatch(DatasetNameMismatchException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Dataset Name Mismatch");
        problem.setType(URI.create("https://example.com/errors/DATASET_NAME_MISMATCH"));
        problem.setProperty("code", "DATASET_NAME_MISMATCH");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(InvalidDateRangeException.class)
    public ProblemDetail handleInvalidDateRange(InvalidDateRangeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Date Range");
        problem.setType(URI.create("https://example.com/errors/INVALID_DATE_RANGE"));
        problem.setProperty("code", "INVALID_DATE_RANGE");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(InvalidFilterParameterException.class)
    public ProblemDetail handleInvalidFilterParameter(InvalidFilterParameterException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Filter Parameter");
        problem.setType(URI.create("https://example.com/errors/INVALID_FILTER_PARAMETER"));
        problem.setProperty("code", "INVALID_FILTER_PARAMETER");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(InvalidSortException.class)
    public ProblemDetail handleInvalidSort(InvalidSortException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Invalid Sort Parameter");
        problem.setType(URI.create("https://example.com/errors/INVALID_SORT_PARAMETER"));
        problem.setProperty("code", "INVALID_SORT_PARAMETER");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    @ExceptionHandler(com.example.bi.exception.ExportTooLargeException.class)
    public ProblemDetail handleExportTooLarge(com.example.bi.exception.ExportTooLargeException ex, HttpServletRequest request) {
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Export Too Large");
        problem.setType(URI.create("https://example.com/errors/EXPORT_TOO_LARGE"));
        problem.setProperty("code", "EXPORT_TOO_LARGE");
        problem.setProperty("requestId", requestId);
        problem.setProperty("count", ex.getCount());
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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception: ", ex);
        String requestId = resolveRequestId(request);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        problem.setType(URI.create("https://example.com/errors/INTERNAL_ERROR"));
        problem.setProperty("code", "INTERNAL_ERROR");
        problem.setProperty("requestId", requestId);
        return problem;
    }

    public static String resolveRequestId(HttpServletRequest request) {
        String header = request.getHeader("X-Request-Id");
        return (header != null && !header.isBlank()) ? header : UUID.randomUUID().toString();
    }
}

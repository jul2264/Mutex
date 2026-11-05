package com.example.bi.dto;

public record RowIssueDto(
        String column,
        String severity,
        String code,
        String message,
        String rawValue
) {}


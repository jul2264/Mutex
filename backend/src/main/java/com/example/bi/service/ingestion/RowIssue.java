package com.example.bi.service.ingestion;

import com.example.bi.domain.IssueSeverity;

public record RowIssue(
        int sourceRowNumber,
        String columnName,
        IssueSeverity severity,
        String code,
        String message,
        String rawValue
) {}


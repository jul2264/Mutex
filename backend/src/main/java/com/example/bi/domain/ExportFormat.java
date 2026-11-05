package com.example.bi.domain;

import java.util.Locale;

public enum ExportFormat {
    CSV,
    XLSX;

    public static ExportFormat fromString(String value) {
        if (value == null || value.isBlank()) {
            return CSV;
        }
        try {
            return ExportFormat.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported export format: '" + value + "'. Must be 'csv' or 'xlsx'.");
        }
    }
}

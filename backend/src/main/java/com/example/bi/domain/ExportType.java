package com.example.bi.domain;

import java.util.Locale;

public enum ExportType {
    RECORDS,
    SUMMARY;

    public static ExportType fromString(String value) {
        if (value == null || value.isBlank()) {
            return RECORDS;
        }
        try {
            return ExportType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported export type: '" + value + "'. Must be 'records' or 'summary'.");
        }
    }
}

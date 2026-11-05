package com.example.bi.service.ingestion;

public final class FormulaSanitizer {

    private FormulaSanitizer() {}

    /**
     * Sanitizes text fields against spreadsheet formula injection (CSV/XLSX injection).
     * Prefixes a single quote if the string begins with =, +, -, @, \t, or \r.
     * Numeric values should not be passed to this method.
     */
    public static String sanitizeText(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r') {
            return "'" + value;
        }
        return value;
    }
}


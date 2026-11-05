package com.example.bi.service.report;

/**
 * Sanitizes spreadsheet export text columns against formula injection (CSV/XLSX injection).
 * <p>
 * Per BR-20 and ADR D-16: Text columns starting with {@code =}, {@code +}, {@code -},
 * {@code @}, tab ({@code \t}), or carriage return ({@code \r}) are prefixed with a single quote ({@code '}).
 * Numeric columns must NEVER be sanitized with this class.
 */
public final class FormulaSafe {

    private FormulaSafe() {
    }

    /**
     * Sanitizes the given text string. If it begins with a dangerous character (or leading whitespace followed by one),
     * a single quote is prepended.
     *
     * @param text the raw text value
     * @return the formula-safe text value
     */
    public static String sanitize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        // Find first character that is not a space (' ')
        int i = 0;
        while (i < text.length() && text.charAt(i) == ' ') {
            i++;
        }
        if (i >= text.length()) {
            return text;
        }

        char firstChar = text.charAt(i);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                || firstChar == '\t' || firstChar == '\r') {
            return "'" + text;
        }

        return text;
    }
}

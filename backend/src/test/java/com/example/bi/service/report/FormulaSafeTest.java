package com.example.bi.service.report;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class FormulaSafeTest {

    @Test
    @DisplayName("Null or empty string is returned unchanged")
    void nullOrEmptyUnchanged() {
        assertThat(FormulaSafe.sanitize(null)).isNull();
        assertThat(FormulaSafe.sanitize("")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "=1+1",
        "+cmd|' /C calc'!A0",
        "-5+3",
        "@SUM(A1:A10)",
        "\tmalicious",
        "\rmalicious"
    })
    @DisplayName("Prefixes single quote when text starts with dangerous formula character")
    void prefixesSingleQuoteOnDangerousChars(String input) {
        String sanitized = FormulaSafe.sanitize(input);
        assertThat(sanitized).startsWith("'");
        assertThat(sanitized).isEqualTo("'" + input);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Normal Product",
        "Widget A-100",
        "12345",
        "Laptop (15-inch)",
        "Acme Corp."
    })
    @DisplayName("Safe text is returned unchanged")
    void safeTextUnchanged(String input) {
        assertThat(FormulaSafe.sanitize(input)).isEqualTo(input);
    }

    @Test
    @DisplayName("Leading whitespace before dangerous character is handled")
    void leadingWhitespaceBeforeFormula() {
        assertThat(FormulaSafe.sanitize("  =cmd")).isEqualTo("'  =cmd");
    }
}

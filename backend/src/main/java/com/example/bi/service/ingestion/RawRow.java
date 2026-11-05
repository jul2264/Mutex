package com.example.bi.service.ingestion;

import java.util.Map;

public record RawRow(
        int sourceRowNumber,
        Map<String, String> cells,
        boolean isBlank
) {}

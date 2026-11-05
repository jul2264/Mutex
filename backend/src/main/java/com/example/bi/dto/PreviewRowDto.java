package com.example.bi.dto;

import java.util.List;
import java.util.Map;

public record PreviewRowDto(
        int rowNumber,
        String status,
        RowValuesDto values,
        Map<String, Object> rawValues,
        List<RowIssueDto> issues
) {}


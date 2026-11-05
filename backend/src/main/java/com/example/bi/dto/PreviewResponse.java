package com.example.bi.dto;

import java.util.List;

public record PreviewResponse(
        String source,
        PreviewCountsDto counts,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<PreviewRowDto> content
) {}


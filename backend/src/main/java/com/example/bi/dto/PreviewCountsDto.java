package com.example.bi.dto;

public record PreviewCountsDto(
        int total,
        int valid,
        int invalid,
        int duplicateCandidate,
        int warnings
) {}


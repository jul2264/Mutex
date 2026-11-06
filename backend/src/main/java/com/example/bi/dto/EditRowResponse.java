package com.example.bi.dto;

public record EditRowResponse(
        PreviewRowDto row,
        PreviewCountsDto counts
) {}


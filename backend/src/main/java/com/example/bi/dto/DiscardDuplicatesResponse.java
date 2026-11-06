package com.example.bi.dto;

public record DiscardDuplicatesResponse(
        int discardedCount,
        PreviewCountsDto counts
) {}

package com.example.bi.dto;

public record BulkFixResponse(
        int totalProcessed,
        int repairedToValid,
        int remainingInvalid,
        int untouchedLargeQuantityCount,
        PreviewCountsDto counts
) {}


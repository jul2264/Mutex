package com.example.bi.dto;

public record BulkFixRequest(
        String negativePriceStrategy,
        String decimalQuantityStrategy,
        String zeroQuantityStrategy,
        String dateFixStrategy,
        String customDate,
        Boolean fillMissingProduct,
        String productPlaceholder,
        Boolean fillMissingCategory,
        String categoryPlaceholder,
        Boolean fillMissingRegion,
        String regionPlaceholder
) {
    public boolean isNegativePriceAbs() {
        return "ABS_VALUE".equalsIgnoreCase(negativePriceStrategy);
    }

    public boolean isDecimalQuantityRound() {
        return "ROUND_HALF_UP".equalsIgnoreCase(decimalQuantityStrategy);
    }

    public boolean isDecimalQuantityTruncate() {
        return "TRUNCATE".equalsIgnoreCase(decimalQuantityStrategy);
    }

    public boolean isZeroQuantitySetOne() {
        return "SET_ONE".equalsIgnoreCase(zeroQuantityStrategy);
    }

    public boolean isDateLatestValid() {
        return "LATEST_VALID".equalsIgnoreCase(dateFixStrategy);
    }

    public boolean isDateCustom() {
        return "CUSTOM".equalsIgnoreCase(dateFixStrategy);
    }

    public boolean shouldFillProduct() {
        return fillMissingProduct != null && fillMissingProduct;
    }

    public boolean shouldFillCategory() {
        return fillMissingCategory != null && fillMissingCategory;
    }

    public boolean shouldFillRegion() {
        return fillMissingRegion != null && fillMissingRegion;
    }
}


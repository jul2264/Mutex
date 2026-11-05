package com.example.bi.service.ingestion;

import com.example.bi.domain.RowStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record ParsedRow(
        int sourceRowNumber,
        RowStatus status,
        String orderId,
        LocalDate orderDate,
        String productName,
        String category,
        String region,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal unitCost,
        String rowHash,
        Map<String, String> rawValues,
        List<RowIssue> issues
) {
    public boolean isValid() {
        return status == RowStatus.VALID;
    }
}

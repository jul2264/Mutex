package com.example.bi.dto;

public record RowValuesDto(
        String orderId,
        String orderDate,
        String productName,
        String category,
        String region,
        Integer quantity,
        String unitPrice,
        String unitCost
) {}


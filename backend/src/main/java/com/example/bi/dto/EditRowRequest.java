package com.example.bi.dto;

public record EditRowRequest(
        String orderId,
        String orderDate,
        String productName,
        String category,
        String region,
        String quantity,
        String unitPrice,
        String unitCost
) {}

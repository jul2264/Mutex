package com.example.bi.dto;

import java.math.BigDecimal;

public record SalesTotals(
        BigDecimal revenue,
        BigDecimal grossProfit,
        long unitsSold,
        long orders,
        long lineItems
) {}


package com.example.bi.dto;

public record Metric(String value, String unavailableReason) {
    public static Metric of(String value) {
        return new Metric(value, null);
    }

    public static Metric unavailable(String reason) {
        return new Metric(null, reason);
    }
}


package com.example.bi.dto;

public record CsrfResponse(
        String token,
        String headerName,
        String parameterName
) {
}


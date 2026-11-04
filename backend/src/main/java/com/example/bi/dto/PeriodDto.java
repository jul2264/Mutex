package com.example.bi.dto;

import java.time.LocalDate;

public record PeriodDto(
        LocalDate from,
        LocalDate to,
        LocalDate previousFrom,
        LocalDate previousTo
) {}


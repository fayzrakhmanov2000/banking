package ru.ildar.safebox.application.web.dto;

import ru.ildar.safebox.application.domain.ApplicationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ApplicationResponse(
        UUID id,
        UUID clientId,
        UUID cellId,
        ApplicationStatus status,
        LocalDate rentalFrom,
        LocalDate rentalTo,
        BigDecimal price,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

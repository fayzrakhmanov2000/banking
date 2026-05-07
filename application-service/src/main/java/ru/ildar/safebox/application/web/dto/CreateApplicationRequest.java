package ru.ildar.safebox.application.web.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

public record CreateApplicationRequest(
        @NotNull UUID clientId,
        @NotNull UUID cellId,
        @NotNull @Future LocalDate rentalFrom,
        @NotNull @Future LocalDate rentalTo
) {}

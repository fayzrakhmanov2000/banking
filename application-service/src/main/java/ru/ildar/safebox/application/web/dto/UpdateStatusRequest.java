package ru.ildar.safebox.application.web.dto;

import jakarta.validation.constraints.NotNull;
import ru.ildar.safebox.application.domain.ApplicationStatus;

public record UpdateStatusRequest(@NotNull ApplicationStatus status) {}

package ru.ildar.safebox.application.web.dto;

import java.time.Instant;

public record ErrorResponse(String code, String message, String traceId, Instant timestamp) {}

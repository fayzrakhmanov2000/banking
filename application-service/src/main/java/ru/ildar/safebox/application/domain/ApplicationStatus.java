package ru.ildar.safebox.application.domain;

public enum ApplicationStatus {
    DRAFT,
    VALIDATION_IN_PROGRESS,
    WAITING_PAYMENT,
    PAID,
    CONFIRMED,
    CANCELLED,
    FAILED
}

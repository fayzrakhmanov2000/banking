package ru.ildar.safebox.application.kafka.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentCompletedEvent(
        String eventId,
        UUID applicationId,
        BigDecimal amount,
        Instant occurredAt
) {}

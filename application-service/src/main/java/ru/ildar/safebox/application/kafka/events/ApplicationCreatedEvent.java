package ru.ildar.safebox.application.kafka.events;

import java.time.Instant;
import java.util.UUID;

public record ApplicationCreatedEvent(
        String eventId,
        UUID applicationId,
        UUID clientId,
        UUID cellId,
        Instant occurredAt
) {}

package ru.ildar.safebox.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.ildar.safebox.application.domain.*;
import ru.ildar.safebox.application.exception.ApplicationNotFoundException;
import ru.ildar.safebox.application.exception.InvalidStatusTransitionException;
import ru.ildar.safebox.application.kafka.events.ApplicationCreatedEvent;
import ru.ildar.safebox.application.web.dto.CreateApplicationRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class ApplicationService {

    private final ApplicationRepository applications;
    private final OutboxEventRepository outbox;
    private final ObjectMapper objectMapper;
    private final Counter created;
    private final Counter confirmed;
    private final Counter cancelled;
    private final String topic;

    public ApplicationService(ApplicationRepository applications,
                              OutboxEventRepository outbox,
                              ObjectMapper objectMapper,
                              MeterRegistry registry,
                              @Value("${app.kafka.topics.application-events}") String topic) {
        this.applications = applications;
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.topic = topic;
        this.created = registry.counter("applications.created.count");
        this.confirmed = registry.counter("applications.confirmed.count");
        this.cancelled = registry.counter("applications.cancelled.count");
    }

    @Transactional
    public ApplicationEntity create(CreateApplicationRequest req) {
        ApplicationEntity e = new ApplicationEntity();
        e.setClientId(req.clientId());
        e.setCellId(req.cellId());
        e.setRentalFrom(req.rentalFrom());
        e.setRentalTo(req.rentalTo());
        e.setStatus(ApplicationStatus.DRAFT);
        e.setPrice(BigDecimal.ZERO);
        ApplicationEntity saved = applications.save(e);

        publishToOutbox(saved.getId(), "ApplicationCreated",
                new ApplicationCreatedEvent(UUID.randomUUID().toString(),
                        saved.getId(), saved.getClientId(), saved.getCellId(), Instant.now()));
        created.increment();
        return saved;
    }

    @Transactional(readOnly = true)
    public ApplicationEntity get(UUID id) {
        return applications.findById(id).orElseThrow(() -> new ApplicationNotFoundException(id));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationEntity> search(UUID clientId, ApplicationStatus status, Pageable pageable) {
        return applications.search(clientId, status, pageable);
    }

    @Transactional
    public ApplicationEntity changeStatus(UUID id, ApplicationStatus next) {
        ApplicationEntity e = get(id);
        if (!isAllowed(e.getStatus(), next)) {
            throw new InvalidStatusTransitionException(e.getStatus(), next);
        }
        e.setStatus(next);
        if (next == ApplicationStatus.CONFIRMED) confirmed.increment();
        if (next == ApplicationStatus.CANCELLED) cancelled.increment();
        return e;
    }

    @Transactional
    public ApplicationEntity confirm(UUID id) {
        return changeStatus(id, ApplicationStatus.WAITING_PAYMENT);
    }

    private boolean isAllowed(ApplicationStatus from, ApplicationStatus to) {
        return switch (from) {
            case DRAFT -> to == ApplicationStatus.VALIDATION_IN_PROGRESS || to == ApplicationStatus.WAITING_PAYMENT || to == ApplicationStatus.CANCELLED;
            case VALIDATION_IN_PROGRESS -> to == ApplicationStatus.WAITING_PAYMENT || to == ApplicationStatus.FAILED || to == ApplicationStatus.CANCELLED;
            case WAITING_PAYMENT -> to == ApplicationStatus.PAID || to == ApplicationStatus.CANCELLED || to == ApplicationStatus.FAILED;
            case PAID -> to == ApplicationStatus.CONFIRMED || to == ApplicationStatus.CANCELLED;
            default -> false;
        };
    }

    private void publishToOutbox(UUID aggregateId, String type, Object payload) {
        try {
            OutboxEvent o = new OutboxEvent();
            o.setAggregateId(aggregateId);
            o.setEventType(type);
            o.setTopic(topic);
            o.setPayload(objectMapper.writeValueAsString(payload));
            o.setStatus(OutboxEvent.Status.PENDING);
            outbox.save(o);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Cannot serialize event", ex);
        }
    }
}

package ru.ildar.safebox.application.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ildar.safebox.application.domain.ApplicationStatus;
import ru.ildar.safebox.application.domain.ProcessedEvent;
import ru.ildar.safebox.application.domain.ProcessedEventRepository;
import ru.ildar.safebox.application.kafka.events.PaymentCompletedEvent;
import ru.ildar.safebox.application.service.ApplicationService;

import java.util.UUID;

@Component
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final String GROUP = "application-service";

    private final ProcessedEventRepository processed;
    private final ApplicationService service;
    private final ObjectMapper objectMapper;
    private final Counter completed;

    public PaymentEventConsumer(ProcessedEventRepository processed,
                                ApplicationService service,
                                ObjectMapper objectMapper,
                                MeterRegistry registry) {
        this.processed = processed;
        this.service = service;
        this.objectMapper = objectMapper;
        this.completed = registry.counter("payments.completed.count");
    }

    @KafkaListener(topics = "${app.kafka.topics.payment-events}", groupId = GROUP)
    @Transactional
    public void onPayment(String raw, Acknowledgment ack) {
        try {
            PaymentCompletedEvent event = objectMapper.readValue(raw, PaymentCompletedEvent.class);
            MDC.put("traceId", event.eventId());
            try {
                processed.save(new ProcessedEvent(event.eventId(), GROUP));
                processed.flush();
            } catch (DataIntegrityViolationException dup) {
                log.info("Duplicate event {}, skip", event.eventId());
                ack.acknowledge();
                return;
            }
            service.changeStatus(event.applicationId(), ApplicationStatus.PAID);
            completed.increment();
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Consumer error: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        } finally {
            MDC.remove("traceId");
        }
    }
}

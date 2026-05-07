package ru.ildar.safebox.application.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.ildar.safebox.application.domain.OutboxEvent;
import ru.ildar.safebox.application.domain.OutboxEventRepository;

import java.time.Instant;
import java.util.List;

@Component
@EnableScheduling
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repo;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxEventRepository repo, KafkaTemplate<String, String> kafka) {
        this.repo = repo;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 2000)
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repo.findPending(PageRequest.of(0, 50));
        for (OutboxEvent e : batch) {
            try {
                kafka.send(e.getTopic(), e.getAggregateId().toString(), e.getPayload()).get();
                e.setStatus(OutboxEvent.Status.SENT);
                e.setSentAt(Instant.now());
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {}: {}", e.getId(), ex.getMessage());
                e.setRetryCount(e.getRetryCount() + 1);
                if (e.getRetryCount() >= 5) {
                    e.setStatus(OutboxEvent.Status.FAILED);
                }
            }
        }
    }
}

package ru.ildar.safebox.application.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    public enum Status { PENDING, SENT, FAILED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(nullable = false, length = 64)
    private String topic;

    @Column(nullable = false, columnDefinition = "text")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public void setAggregateId(UUID v) { this.aggregateId = v; }
    public String getEventType() { return eventType; }
    public void setEventType(String v) { this.eventType = v; }
    public String getTopic() { return topic; }
    public void setTopic(String v) { this.topic = v; }
    public String getPayload() { return payload; }
    public void setPayload(String v) { this.payload = v; }
    public Status getStatus() { return status; }
    public void setStatus(Status v) { this.status = v; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int v) { this.retryCount = v; }
    public Instant getSentAt() { return sentAt; }
    public void setSentAt(Instant v) { this.sentAt = v; }
}

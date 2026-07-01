package com.clara.challenge.watchdog.persistence;

import com.clara.challenge.watchdog.domain.EventResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

@Entity
@Table(schema = "clarops_challenge_schema", name = "trace_event")
public class TraceEvent {

  @Id
  @Column(name = "event_id", nullable = false, length = 120)
  private String eventId;

  @Column(name = "trace_id", nullable = false, length = 120)
  private String traceId;

  @Column(name = "event_name", nullable = false, length = 120)
  private String eventName;

  @Enumerated(EnumType.STRING)
  @Column(name = "result", nullable = false, length = 20)
  private EventResult result;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "next_expected_event", length = 120)
  private String nextExpectedEvent;

  @Column(name = "next_event_ttl_seconds")
  private Integer nextEventTtlSeconds;

  @Column(name = "final_event", nullable = false)
  private boolean finalEvent;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata", nullable = false, columnDefinition = "jsonb")
  private JsonNode metadata;

  protected TraceEvent() {}

  public TraceEvent(
      String eventId,
      String traceId,
      String eventName,
      EventResult result,
      Instant occurredAt,
      Instant receivedAt,
      String nextExpectedEvent,
      Integer nextEventTtlSeconds,
      boolean finalEvent,
      JsonNode metadata) {
    this.eventId = eventId;
    this.traceId = traceId;
    this.eventName = eventName;
    this.result = result;
    this.occurredAt = occurredAt;
    this.receivedAt = receivedAt;
    this.nextExpectedEvent = nextExpectedEvent;
    this.nextEventTtlSeconds = nextEventTtlSeconds;
    this.finalEvent = finalEvent;
    this.metadata = Objects.requireNonNull(metadata, "metadata must not be null");
  }

  public String getEventId() {
    return eventId;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getEventName() {
    return eventName;
  }

  public EventResult getResult() {
    return result;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public Instant getReceivedAt() {
    return receivedAt;
  }

  public String getNextExpectedEvent() {
    return nextExpectedEvent;
  }

  public Integer getNextEventTtlSeconds() {
    return nextEventTtlSeconds;
  }

  public boolean isFinalEvent() {
    return finalEvent;
  }

  public JsonNode getMetadata() {
    return metadata;
  }
}

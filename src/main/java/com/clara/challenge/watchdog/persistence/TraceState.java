package com.clara.challenge.watchdog.persistence;

import com.clara.challenge.watchdog.domain.EventResult;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(schema = "clarops_challenge_schema", name = "trace_state")
public class TraceState {

  @Id
  @Column(name = "trace_id", nullable = false, length = 120)
  private String traceId;

  @Column(name = "last_event_id", nullable = false, length = 120)
  private String lastEventId;

  @Column(name = "last_event_name", nullable = false, length = 120)
  private String lastEventName;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_event_result", nullable = false, length = 20)
  private EventResult lastEventResult;

  @Column(name = "last_event_occurred_at", nullable = false)
  private Instant lastEventOccurredAt;

  @Column(name = "last_event_received_at", nullable = false)
  private Instant lastEventReceivedAt;

  @Column(name = "next_expected_event", length = 120)
  private String nextExpectedEvent;

  @Column(name = "next_expected_before")
  private Instant nextExpectedBefore;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "events_received", nullable = false)
  private int eventsReceived;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected TraceState() {}

  public TraceState(
      String traceId,
      String lastEventId,
      String lastEventName,
      EventResult lastEventResult,
      Instant lastEventOccurredAt,
      Instant lastEventReceivedAt,
      String nextExpectedEvent,
      Instant nextExpectedBefore,
      Instant completedAt,
      int eventsReceived,
      Instant createdAt,
      Instant updatedAt) {
    this.traceId = traceId;
    this.lastEventId = lastEventId;
    this.lastEventName = lastEventName;
    this.lastEventResult = lastEventResult;
    this.lastEventOccurredAt = lastEventOccurredAt;
    this.lastEventReceivedAt = lastEventReceivedAt;
    this.nextExpectedEvent = nextExpectedEvent;
    this.nextExpectedBefore = nextExpectedBefore;
    this.completedAt = completedAt;
    this.eventsReceived = eventsReceived;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String getTraceId() {
    return traceId;
  }

  public String getLastEventId() {
    return lastEventId;
  }

  public String getLastEventName() {
    return lastEventName;
  }

  public EventResult getLastEventResult() {
    return lastEventResult;
  }

  public Instant getLastEventOccurredAt() {
    return lastEventOccurredAt;
  }

  public Instant getLastEventReceivedAt() {
    return lastEventReceivedAt;
  }

  public String getNextExpectedEvent() {
    return nextExpectedEvent;
  }

  public Instant getNextExpectedBefore() {
    return nextExpectedBefore;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public int getEventsReceived() {
    return eventsReceived;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getVersion() {
    return version;
  }

  public void recordLatestEvent(
      String lastEventId,
      String lastEventName,
      EventResult lastEventResult,
      Instant lastEventOccurredAt,
      Instant lastEventReceivedAt,
      String nextExpectedEvent,
      Instant nextExpectedBefore,
      Instant completedAt,
      int eventsReceived,
      Instant updatedAt) {
    this.lastEventId = lastEventId;
    this.lastEventName = lastEventName;
    this.lastEventResult = lastEventResult;
    this.lastEventOccurredAt = lastEventOccurredAt;
    this.lastEventReceivedAt = lastEventReceivedAt;
    this.nextExpectedEvent = nextExpectedEvent;
    this.nextExpectedBefore = nextExpectedBefore;
    this.completedAt = completedAt;
    this.eventsReceived = eventsReceived;
    this.updatedAt = updatedAt;
  }
}

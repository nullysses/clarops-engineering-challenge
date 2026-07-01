package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.domain.EventResult;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record EventRequest(
    String eventId,
    String traceId,
    String eventName,
    EventResult result,
    Instant occurredAt,
    String nextExpectedEvent,
    Integer nextEventTtlSeconds,
    Boolean finalEvent,
    JsonNode metadata) {

  public EventRequest {
    finalEvent = Boolean.TRUE.equals(finalEvent);
  }
}

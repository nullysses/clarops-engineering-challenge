package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.domain.EventResult;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record EventRequest(
    @NotBlank(message = "eventId must be present and non-blank") String eventId,
    @NotBlank(message = "traceId must be present and non-blank") String traceId,
    @NotBlank(message = "eventName must be present and non-blank") String eventName,
    @NotNull(message = "result must be present") EventResult result,
    @NotNull(message = "occurredAt must be present") Instant occurredAt,
    String nextExpectedEvent,
    @Positive(message = "nextEventTtlSeconds must be greater than zero")
        Integer nextEventTtlSeconds,
    Boolean finalEvent,
    JsonNode metadata) {

  public EventRequest {
    finalEvent = Boolean.TRUE.equals(finalEvent);
  }

  @AssertTrue(
      message = "nextExpectedEvent and nextEventTtlSeconds must either both be present or both be absent")
  public boolean isExpectedEventPairValid() {
    return (nextExpectedEvent == null && nextEventTtlSeconds == null)
        || (nextExpectedEvent != null && nextEventTtlSeconds != null);
  }

  @AssertTrue(message = "nextExpectedEvent must not be blank when present")
  public boolean isNextExpectedEventNonBlankWhenPresent() {
    return nextExpectedEvent == null || !nextExpectedEvent.isBlank();
  }

  @AssertTrue(message = "finalEvent must not define another expected event")
  public boolean isFinalEventWithoutExpectation() {
    return !finalEvent || (nextExpectedEvent == null && nextEventTtlSeconds == null);
  }

  @AssertTrue(message = "metadata must be a JSON object when present")
  public boolean isMetadataObjectWhenPresent() {
    return metadata == null || metadata.isObject();
  }
}

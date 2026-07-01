package com.clara.challenge.watchdog.domain;

import java.time.Instant;
import java.util.Objects;

public class TraceStatusCalculator {

  public TraceStatus calculate(
      Instant completedAt, String nextExpectedEvent, Instant nextExpectedBefore, Instant now) {
    boolean hasExpectedEvent = nextExpectedEvent != null;
    boolean hasDeadline = nextExpectedBefore != null;

    if (hasExpectedEvent != hasDeadline) {
      throw new IllegalStateException(
          "Expected event and deadline must both be present or both be absent");
    }

    if (completedAt != null) {
      return TraceStatus.COMPLETED;
    }

    if (!hasExpectedEvent) {
      return TraceStatus.STARTED;
    }

    Objects.requireNonNull(now, "now must not be null");
    return now.isBefore(nextExpectedBefore)
        ? TraceStatus.WAITING_OTHER_EVENT
        : TraceStatus.TTL_EXPIRED_FOR_EVENT;
  }
}

package com.clara.challenge.watchdog.service;

import com.clara.challenge.watchdog.api.TraceStatusResponse;
import com.clara.challenge.watchdog.api.exception.WatchdogNotFoundException;
import com.clara.challenge.watchdog.domain.TraceStatus;
import com.clara.challenge.watchdog.domain.TraceStatusCalculator;
import com.clara.challenge.watchdog.persistence.TraceState;
import com.clara.challenge.watchdog.persistence.TraceStateRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;

@Service
public class TraceStatusService {

  private final TraceStateRepository traceStateRepository;
  private final TraceStatusCalculator traceStatusCalculator;
  private final Clock clock;

  public TraceStatusService(
      TraceStateRepository traceStateRepository,
      TraceStatusCalculator traceStatusCalculator,
      Clock clock) {
    this.traceStateRepository = traceStateRepository;
    this.traceStatusCalculator = traceStatusCalculator;
    this.clock = clock;
  }

  public TraceStatusResponse getStatus(String traceId) {
    TraceState traceState =
        traceStateRepository
            .findById(traceId)
            .orElseThrow(() -> new WatchdogNotFoundException("Trace not found: " + traceId));

    TraceStatus status =
        traceStatusCalculator.calculate(
            traceState.getCompletedAt(),
            traceState.getNextExpectedEvent(),
            traceState.getNextExpectedBefore(),
            Instant.now(clock));

    return new TraceStatusResponse(
        traceState.getTraceId(),
        status,
        traceState.getLastEventId(),
        traceState.getLastEventName(),
        traceState.getLastEventResult(),
        traceState.getNextExpectedEvent(),
        traceState.getNextExpectedBefore(),
        traceState.getEventsReceived(),
        traceState.getCompletedAt());
  }
}

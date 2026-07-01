package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.domain.EventResult;
import com.clara.challenge.watchdog.domain.TraceStatus;
import java.time.Instant;

public record TraceStatusResponse(
    String traceId,
    TraceStatus status,
    String lastEventId,
    String lastEventName,
    EventResult lastEventResult,
    String nextExpectedEvent,
    Instant nextExpectedBefore,
    int eventsReceived,
    Instant completedAt) {}

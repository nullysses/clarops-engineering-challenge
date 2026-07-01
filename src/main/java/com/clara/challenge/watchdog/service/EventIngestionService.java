package com.clara.challenge.watchdog.service;

import com.clara.challenge.watchdog.api.EventIngestionResponse;
import com.clara.challenge.watchdog.api.EventRequest;
import com.clara.challenge.watchdog.api.exception.WatchdogConflictException;
import com.clara.challenge.watchdog.domain.EventResult;
import com.clara.challenge.watchdog.persistence.TraceEvent;
import com.clara.challenge.watchdog.persistence.TraceEventRepository;
import com.clara.challenge.watchdog.persistence.TraceState;
import com.clara.challenge.watchdog.persistence.TraceStateRepository;
import jakarta.persistence.OptimisticLockException;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeFactory;

@Service
public class EventIngestionService {

  private final TraceStateRepository traceStateRepository;
  private final TraceEventRepository traceEventRepository;
  private final Clock clock;
  private final TransactionTemplate transactionTemplate;

  public EventIngestionService(
      TraceStateRepository traceStateRepository,
      TraceEventRepository traceEventRepository,
      Clock clock,
      PlatformTransactionManager transactionManager) {
    this.traceStateRepository = traceStateRepository;
    this.traceEventRepository = traceEventRepository;
    this.clock = clock;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public EventIngestionResponse ingest(EventRequest request) {
    Instant receivedAt = Instant.now(clock);
    LogicalEventPayload requestPayload = LogicalEventPayload.from(request, normalizeMetadata(request));
    AttemptContext context = new AttemptContext();

    try {
      return executeIngestionAttempt(request, requestPayload, receivedAt, context);
    } catch (OptimisticLockingFailureException | OptimisticLockException exception) {
      return resolveOptimisticLockFailureAfterRollback(request, requestPayload, exception);
    } catch (DataIntegrityViolationException exception) {
      return resolveIntegrityFailureAfterRollback(request, requestPayload, receivedAt, context, exception);
    }
  }

  private EventIngestionResponse executeIngestionAttempt(
      EventRequest request,
      LogicalEventPayload requestPayload,
      Instant receivedAt,
      AttemptContext context) {
    return Objects.requireNonNull(
        transactionTemplate.execute(
            status -> {
              Optional<TraceEvent> existingEvent =
                  traceEventRepository.findById(request.eventId());
              if (existingEvent.isPresent()) {
                return resolveExistingEvent(requestPayload, existingEvent.get());
              }

              TraceState traceState =
                  traceStateRepository
                      .findById(request.traceId())
                      .map(existingTrace -> acceptForExistingTrace(request, receivedAt, existingTrace))
                      .orElseGet(() -> createTraceState(request, receivedAt, context));

              persistAcceptedEvent(request, requestPayload.metadata(), receivedAt);
              flushAcceptedEvent(traceState);

              return new EventIngestionResponse(request.eventId(), request.traceId(), false);
            }));
  }

  private EventIngestionResponse resolveIntegrityFailureAfterRollback(
      EventRequest request,
      LogicalEventPayload requestPayload,
      Instant receivedAt,
      AttemptContext context,
      DataIntegrityViolationException exception) {
    Optional<TraceEvent> eventInsertedByConcurrentTransaction = readEventInNewTransaction(request.eventId());
    if (eventInsertedByConcurrentTransaction.isPresent()) {
      return resolveExistingEvent(requestPayload, eventInsertedByConcurrentTransaction.get());
    }

    if (context.attemptedTraceCreation() && traceExistsInNewTransaction(request.traceId())) {
      AttemptContext retryContext = new AttemptContext();
      try {
        return executeIngestionAttempt(request, requestPayload, receivedAt, retryContext);
      } catch (OptimisticLockingFailureException | OptimisticLockException retryException) {
        return resolveOptimisticLockFailureAfterRollback(request, requestPayload, retryException);
      } catch (DataIntegrityViolationException retryException) {
        Optional<TraceEvent> retryEvent = readEventInNewTransaction(request.eventId());
        if (retryEvent.isPresent()) {
          return resolveExistingEvent(requestPayload, retryEvent.get());
        }
        throw retryException;
      }
    }

    throw exception;
  }

  private EventIngestionResponse resolveOptimisticLockFailureAfterRollback(
      EventRequest request, LogicalEventPayload requestPayload, RuntimeException exception) {
    Optional<TraceEvent> eventInsertedByConcurrentTransaction = readEventInNewTransaction(request.eventId());
    if (eventInsertedByConcurrentTransaction.isPresent()) {
      TraceEvent existingEvent = eventInsertedByConcurrentTransaction.get();
      if (!requestPayload.equals(LogicalEventPayload.from(existingEvent))) {
        throw new WatchdogConflictException("Event ID already exists with different logical content");
      }
      return new EventIngestionResponse(request.eventId(), request.traceId(), true);
    }
    throw concurrentTraceUpdateConflict(exception);
  }

  private EventIngestionResponse resolveExistingEvent(
      LogicalEventPayload requestPayload, TraceEvent existingEvent) {
    if (!requestPayload.equals(LogicalEventPayload.from(existingEvent))) {
      throw new WatchdogConflictException("Event ID already exists with different logical content");
    }
    return new EventIngestionResponse(existingEvent.getEventId(), existingEvent.getTraceId(), true);
  }

  private TraceState createTraceState(
      EventRequest request, Instant receivedAt, AttemptContext context) {
    context.markAttemptedTraceCreation();

    ExpectedEventOutcome outcome = expectedEventOutcome(request, receivedAt);
    TraceState traceState =
        new TraceState(
            request.traceId(),
            request.eventId(),
            request.eventName(),
            request.result(),
            request.occurredAt(),
            receivedAt,
            outcome.nextExpectedEvent(),
            outcome.nextExpectedBefore(),
            outcome.completedAt(),
            1,
            receivedAt,
            receivedAt);

    return traceStateRepository.saveAndFlush(traceState);
  }

  private TraceState acceptForExistingTrace(
      EventRequest request, Instant receivedAt, TraceState traceState) {
    validatePersistedExpectationPair(traceState);

    if (traceState.getCompletedAt() != null) {
      throw new WatchdogConflictException("Trace is already completed");
    }

    if (traceState.getNextExpectedEvent() != null) {
      if (!receivedAt.isBefore(traceState.getNextExpectedBefore())) {
        throw new WatchdogConflictException("Expected event arrived after the TTL deadline");
      }
      if (!traceState.getNextExpectedEvent().equals(request.eventName())) {
        throw new WatchdogConflictException(
            "Unexpected event. Expected " + traceState.getNextExpectedEvent());
      }
    }

    ExpectedEventOutcome outcome = expectedEventOutcome(request, receivedAt);
    traceState.recordLatestEvent(
        request.eventId(),
        request.eventName(),
        request.result(),
        request.occurredAt(),
        receivedAt,
        outcome.nextExpectedEvent(),
        outcome.nextExpectedBefore(),
        outcome.completedAt(),
        traceState.getEventsReceived() + 1,
        receivedAt);
    return traceState;
  }

  private void persistAcceptedEvent(EventRequest request, JsonNode metadata, Instant receivedAt) {
    TraceEvent traceEvent =
        new TraceEvent(
            request.eventId(),
            request.traceId(),
            request.eventName(),
            request.result(),
            request.occurredAt(),
            receivedAt,
            request.nextExpectedEvent(),
            request.nextEventTtlSeconds(),
            Boolean.TRUE.equals(request.finalEvent()),
            metadata);
    traceEventRepository.save(traceEvent);
  }

  private void flushAcceptedEvent(TraceState traceState) {
    traceStateRepository.saveAndFlush(traceState);
    traceEventRepository.flush();
  }

  private ExpectedEventOutcome expectedEventOutcome(EventRequest request, Instant receivedAt) {
    if (Boolean.TRUE.equals(request.finalEvent())) {
      return new ExpectedEventOutcome(null, null, receivedAt);
    }
    if (request.nextExpectedEvent() != null) {
      return new ExpectedEventOutcome(
          request.nextExpectedEvent(),
          receivedAt.plusSeconds(request.nextEventTtlSeconds()),
          null);
    }
    return new ExpectedEventOutcome(null, null, null);
  }

  private void validatePersistedExpectationPair(TraceState traceState) {
    boolean hasExpectedEvent = traceState.getNextExpectedEvent() != null;
    boolean hasDeadline = traceState.getNextExpectedBefore() != null;
    if (hasExpectedEvent != hasDeadline) {
      throw new IllegalStateException(
          "Expected event and deadline must both be present or both be absent");
    }
  }

  private Optional<TraceEvent> readEventInNewTransaction(String eventId) {
    return Objects.requireNonNull(
        transactionTemplate.execute(status -> traceEventRepository.findById(eventId)));
  }

  private boolean traceExistsInNewTransaction(String traceId) {
    return Boolean.TRUE.equals(
        transactionTemplate.execute(status -> traceStateRepository.existsById(traceId)));
  }

  private JsonNode normalizeMetadata(EventRequest request) {
    JsonNode metadata = request.metadata();
    return metadata == null ? JsonNodeFactory.instance.objectNode() : metadata.deepCopy();
  }

  private WatchdogConflictException concurrentTraceUpdateConflict(Exception exception) {
    return new WatchdogConflictException("Trace was updated concurrently");
  }

  private record ExpectedEventOutcome(
      String nextExpectedEvent, Instant nextExpectedBefore, Instant completedAt) {}

  private static final class AttemptContext {
    private boolean attemptedTraceCreation;

    void markAttemptedTraceCreation() {
      attemptedTraceCreation = true;
    }

    boolean attemptedTraceCreation() {
      return attemptedTraceCreation;
    }
  }

  private record LogicalEventPayload(
      String eventId,
      String traceId,
      String eventName,
      EventResult result,
      Instant occurredAt,
      String nextExpectedEvent,
      Integer nextEventTtlSeconds,
      boolean finalEvent,
      JsonNode metadata) {

    static LogicalEventPayload from(EventRequest request, JsonNode metadata) {
      return new LogicalEventPayload(
          request.eventId(),
          request.traceId(),
          request.eventName(),
          request.result(),
          request.occurredAt(),
          request.nextExpectedEvent(),
          request.nextEventTtlSeconds(),
          Boolean.TRUE.equals(request.finalEvent()),
          metadata);
    }

    static LogicalEventPayload from(TraceEvent event) {
      return new LogicalEventPayload(
          event.getEventId(),
          event.getTraceId(),
          event.getEventName(),
          event.getResult(),
          event.getOccurredAt(),
          event.getNextExpectedEvent(),
          event.getNextEventTtlSeconds(),
          event.isFinalEvent(),
          event.getMetadata());
    }
  }
}

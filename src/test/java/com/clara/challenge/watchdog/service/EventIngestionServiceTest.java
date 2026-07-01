package com.clara.challenge.watchdog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

class EventIngestionServiceTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final String TRACE_ID = "trace-unit-001";
  private static final String EVENT_ID = "evt-unit-001";
  private static final Instant OCCURRED_AT = Instant.parse("2026-06-15T09:59:00Z");
  private static final Instant RECEIVED_AT = Instant.parse("2026-06-15T10:00:00Z");
  private static final Instant DIFFERENT_RECEIVED_AT = Instant.parse("2026-06-15T10:05:00Z");
  private static final Instant CREATED_AT = Instant.parse("2026-06-15T09:50:00Z");
  private static final Instant UPDATED_AT = Instant.parse("2026-06-15T09:55:00Z");

  private TraceStateRepository traceStateRepository;
  private TraceEventRepository traceEventRepository;
  private PlatformTransactionManager transactionManager;
  private CountingClock clock;
  private EventIngestionService service;

  @BeforeEach
  void setUp() {
    traceStateRepository = mock(TraceStateRepository.class);
    traceEventRepository = mock(TraceEventRepository.class);
    transactionManager = mock(PlatformTransactionManager.class);
    clock = new CountingClock(RECEIVED_AT);

    when(transactionManager.getTransaction(any(TransactionDefinition.class)))
        .thenAnswer(invocation -> new SimpleTransactionStatus());
    when(traceStateRepository.saveAndFlush(any(TraceState.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(traceEventRepository.save(any(TraceEvent.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service =
        new EventIngestionService(
            traceStateRepository, traceEventRepository, clock, transactionManager);
  }

  @Test
  void shouldCreateTraceStateAndHistory_WhenFirstEventIsAccepted() {
    // Arrange
    EventRequest request = basicRequest();
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.empty());

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertFalse(response.duplicate());
    assertEquals(EVENT_ID, response.eventId());
    assertEquals(TRACE_ID, response.traceId());

    ArgumentCaptor<TraceState> stateCaptor = ArgumentCaptor.forClass(TraceState.class);
    verify(traceStateRepository, times(2)).saveAndFlush(stateCaptor.capture());
    assertEquals(TRACE_ID, stateCaptor.getAllValues().getFirst().getTraceId());

    ArgumentCaptor<TraceEvent> eventCaptor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(traceEventRepository).save(eventCaptor.capture());
    assertEquals(EVENT_ID, eventCaptor.getValue().getEventId());
    assertEquals(TRACE_ID, eventCaptor.getValue().getTraceId());
    verify(traceEventRepository).flush();
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(transactionManager, never()).rollback(any(TransactionStatus.class));
  }

  @Test
  void shouldStoreStartedFactsAndServerTimestamps_WhenFirstEventHasNoExpectation() {
    // Arrange
    EventRequest request = basicRequest();
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.empty());

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertFalse(response.duplicate());

    TraceState state = firstSavedTraceState();
    assertEquals(1, state.getEventsReceived());
    assertNull(state.getCompletedAt());
    assertNull(state.getNextExpectedEvent());
    assertNull(state.getNextExpectedBefore());
    assertEquals(RECEIVED_AT, state.getCreatedAt());
    assertEquals(RECEIVED_AT, state.getUpdatedAt());
    assertEquals(RECEIVED_AT, state.getLastEventReceivedAt());

    TraceEvent event = savedTraceEvent();
    assertEquals(EVENT_ID, event.getEventId());
    assertEquals(TRACE_ID, event.getTraceId());
    assertEquals("APPLICATION_RECEIVED", event.getEventName());
    assertEquals(EventResult.SUCCESS, event.getResult());
    assertEquals(OCCURRED_AT, event.getOccurredAt());
    assertEquals(RECEIVED_AT, event.getReceivedAt());
    assertNull(event.getNextExpectedEvent());
    assertNull(event.getNextEventTtlSeconds());
    assertFalse(event.isFinalEvent());
    assertTrue(event.getMetadata().isObject());
    assertEquals(emptyMetadata(), event.getMetadata());
    assertEquals(1, clock.instantCalls());
  }

  @Test
  void shouldCalculateDeadlineFromReceivedAt_WhenFirstEventDefinesExpectation() {
    // Arrange
    EventRequest request =
        request(
            EVENT_ID,
            TRACE_ID,
            "APPLICATION_RECEIVED",
            EventResult.SUCCESS,
            OCCURRED_AT.minusSeconds(600),
            "RULES_EVALUATED",
            120,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.empty());

    // Act
    service.ingest(request);

    // Assert
    TraceState state = firstSavedTraceState();
    assertEquals("RULES_EVALUATED", state.getNextExpectedEvent());
    assertEquals(RECEIVED_AT.plusSeconds(120), state.getNextExpectedBefore());
    assertEquals(OCCURRED_AT.minusSeconds(600), state.getLastEventOccurredAt());
    assertEquals(1, clock.instantCalls());
  }

  @Test
  void shouldCompleteTraceAndPersistFinalHistory_WhenFirstEventIsFinal() {
    // Arrange
    EventRequest request =
        request(
            EVENT_ID,
            TRACE_ID,
            "FLOW_COMPLETED",
            EventResult.SUCCESS,
            OCCURRED_AT,
            null,
            null,
            true,
            metadata("{\"final\":true}"));
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.empty());

    // Act
    service.ingest(request);

    // Assert
    TraceState state = firstSavedTraceState();
    assertEquals(RECEIVED_AT, state.getCompletedAt());
    assertNull(state.getNextExpectedEvent());
    assertNull(state.getNextExpectedBefore());

    TraceEvent event = savedTraceEvent();
    assertEquals(EVENT_ID, event.getEventId());
    assertEquals(TRACE_ID, event.getTraceId());
    assertEquals("FLOW_COMPLETED", event.getEventName());
    assertEquals(EventResult.SUCCESS, event.getResult());
    assertEquals(OCCURRED_AT, event.getOccurredAt());
    assertEquals(RECEIVED_AT, event.getReceivedAt());
    assertTrue(event.isFinalEvent());
    assertEquals(metadata("{\"final\":true}"), event.getMetadata());
  }

  @Test
  void shouldUpdateExistingTraceAndPersistHistory_WhenExpectedEventIsAccepted() {
    // Arrange
    TraceState existingTrace = waitingTrace(RECEIVED_AT.plusSeconds(60));
    EventRequest request =
        request(
            "evt-unit-002",
            TRACE_ID,
            "RULES_EVALUATED",
            EventResult.SUCCESS,
            OCCURRED_AT.plusSeconds(30),
            "DECISION_SENT",
            90,
            false,
            metadata("{\"ruleSet\":\"standard\"}"));
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(existingTrace));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertFalse(response.duplicate());
    assertEquals(request.eventId(), existingTrace.getLastEventId());
    assertEquals("RULES_EVALUATED", existingTrace.getLastEventName());
    assertEquals(EventResult.SUCCESS, existingTrace.getLastEventResult());
    assertEquals(OCCURRED_AT.plusSeconds(30), existingTrace.getLastEventOccurredAt());
    assertEquals(RECEIVED_AT, existingTrace.getLastEventReceivedAt());
    assertEquals(2, existingTrace.getEventsReceived());
    assertEquals("DECISION_SENT", existingTrace.getNextExpectedEvent());
    assertEquals(RECEIVED_AT.plusSeconds(90), existingTrace.getNextExpectedBefore());
    assertNull(existingTrace.getCompletedAt());

    TraceEvent event = savedTraceEvent();
    assertEquals(request.eventId(), event.getEventId());
    assertEquals(TRACE_ID, event.getTraceId());
    assertEquals("RULES_EVALUATED", event.getEventName());
    assertEquals(EventResult.SUCCESS, event.getResult());
    assertEquals(OCCURRED_AT.plusSeconds(30), event.getOccurredAt());
    assertEquals(RECEIVED_AT, event.getReceivedAt());
    assertEquals("DECISION_SENT", event.getNextExpectedEvent());
    assertEquals(90, event.getNextEventTtlSeconds());
    assertFalse(event.isFinalEvent());
    assertEquals(metadata("{\"ruleSet\":\"standard\"}"), event.getMetadata());
    verify(traceEventRepository).flush();
  }

  @Test
  void shouldClearExpectation_WhenAcceptedExistingEventDefinesNoNewExpectation() {
    // Arrange
    TraceState existingTrace = waitingTrace(RECEIVED_AT.plusSeconds(60));
    EventRequest request =
        request(
            "evt-unit-002",
            TRACE_ID,
            "RULES_EVALUATED",
            EventResult.SUCCESS,
            OCCURRED_AT.plusSeconds(30),
            null,
            null,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(existingTrace));

    // Act
    service.ingest(request);

    // Assert
    assertNull(existingTrace.getNextExpectedEvent());
    assertNull(existingTrace.getNextExpectedBefore());
    assertNull(existingTrace.getCompletedAt());
    assertEquals(2, existingTrace.getEventsReceived());
  }

  @Test
  void shouldCompleteExistingTrace_WhenExpectedFinalEventIsAccepted() {
    // Arrange
    TraceState existingTrace = waitingTrace(RECEIVED_AT.plusSeconds(60));
    EventRequest request =
        request(
            "evt-unit-002",
            TRACE_ID,
            "RULES_EVALUATED",
            EventResult.SUCCESS,
            OCCURRED_AT.plusSeconds(30),
            null,
            null,
            true,
            emptyMetadata());
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(existingTrace));

    // Act
    service.ingest(request);

    // Assert
    assertEquals(RECEIVED_AT, existingTrace.getCompletedAt());
    assertNull(existingTrace.getNextExpectedEvent());
    assertNull(existingTrace.getNextExpectedBefore());
    assertEquals(2, existingTrace.getEventsReceived());
    assertTrue(savedTraceEvent().isFinalEvent());
  }

  @Test
  void shouldFollowSameLifecycleRules_WhenResultIsError() {
    // Arrange
    EventRequest request =
        request(
            EVENT_ID,
            TRACE_ID,
            "APPLICATION_RECEIVED",
            EventResult.ERROR,
            OCCURRED_AT,
            "RULES_EVALUATED",
            60,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.empty());

    // Act
    service.ingest(request);

    // Assert
    TraceState state = firstSavedTraceState();
    assertEquals(EventResult.ERROR, state.getLastEventResult());
    assertEquals("RULES_EVALUATED", state.getNextExpectedEvent());
    assertEquals(RECEIVED_AT.plusSeconds(60), state.getNextExpectedBefore());
    assertNull(state.getCompletedAt());
  }

  @Test
  void shouldReturnDuplicateWithoutReadingTrace_WhenExactDuplicateExists() {
    // Arrange
    EventRequest request = basicRequest();
    TraceEvent existingEvent = traceEventFrom(request, DIFFERENT_RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    assertEquals(EVENT_ID, response.eventId());
    verify(traceStateRepository, never()).findById(any());
    verify(traceStateRepository, never()).saveAndFlush(any());
    verify(traceEventRepository, never()).save(any());
    verify(traceEventRepository, never()).flush();
    verify(transactionManager).commit(any(TransactionStatus.class));
    verify(transactionManager, never()).rollback(any(TransactionStatus.class));
  }

  @Test
  void shouldThrowConflict_WhenExistingEventPayloadDiffers() {
    // Arrange
    EventRequest request = basicRequest();
    TraceEvent existingEvent =
        new TraceEvent(
            EVENT_ID,
            TRACE_ID,
            "DIFFERENT_EVENT",
            EventResult.SUCCESS,
            OCCURRED_AT,
            DIFFERENT_RECEIVED_AT,
            null,
            null,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    verifyNoInteractions(traceStateRepository);
    verify(traceEventRepository, never()).save(any());
    verify(traceEventRepository, never()).flush();
  }

  @Test
  void shouldTreatOmittedMetadataAndEmptyObjectAsDuplicate_WhenComparingPayload() {
    // Arrange
    EventRequest request =
        request(
            EVENT_ID,
            TRACE_ID,
            "APPLICATION_RECEIVED",
            EventResult.SUCCESS,
            OCCURRED_AT,
            null,
            null,
            null,
            null);
    TraceEvent existingEvent = traceEventFrom(request, DIFFERENT_RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    verify(traceStateRepository, never()).findById(any());
  }

  @Test
  void shouldTreatMetadataFieldOrderAsDuplicate_WhenComparingPayload() {
    // Arrange
    EventRequest request =
        request(
            EVENT_ID,
            TRACE_ID,
            "APPLICATION_RECEIVED",
            EventResult.SUCCESS,
            OCCURRED_AT,
            null,
            null,
            false,
            metadata("{\"country\":\"MX\",\"entityId\":\"company-123\"}"));
    TraceEvent existingEvent =
        traceEventFrom(
            request,
            DIFFERENT_RECEIVED_AT,
            metadata("{\"entityId\":\"company-123\",\"country\":\"MX\"}"));
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
  }

  @Test
  void shouldIgnoreReceivedAt_WhenComparingDuplicatePayload() {
    // Arrange
    EventRequest request = basicRequest();
    TraceEvent existingEvent =
        traceEventFrom(request, RECEIVED_AT.plusSeconds(300), emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
  }

  @Test
  void shouldReturnDuplicateWithoutReadingTrace_WhenAssociatedTraceWouldBeCompleted() {
    // Arrange
    EventRequest request = basicRequest();
    TraceEvent existingEvent = traceEventFrom(request, DIFFERENT_RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(completedTrace()));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    verify(traceStateRepository, never()).findById(TRACE_ID);
  }

  @Test
  void shouldReturnDuplicateWithoutReadingTrace_WhenAssociatedTraceWouldBeExpired() {
    // Arrange
    EventRequest request = basicRequest();
    TraceEvent existingEvent = traceEventFrom(request, DIFFERENT_RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(EVENT_ID)).thenReturn(Optional.of(existingEvent));
    when(traceStateRepository.findById(TRACE_ID))
        .thenReturn(Optional.of(waitingTrace(RECEIVED_AT.minusSeconds(1))));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    verify(traceStateRepository, never()).findById(TRACE_ID);
  }

  @Test
  void shouldRejectUnexpectedEventName_WhenTraceIsWaitingForDifferentEvent() {
    // Arrange
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "UNEXPECTED_EVENT");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldRejectExpectedEvent_WhenReceivedAtEqualsDeadline() {
    // Arrange
    TraceState traceState = waitingTrace(RECEIVED_AT);
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldRejectExpectedEvent_WhenReceivedAtAfterDeadline() {
    // Arrange
    TraceState traceState = waitingTrace(RECEIVED_AT.minusSeconds(1));
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldRejectNewEvent_WhenTraceIsCompleted() {
    // Arrange
    TraceState traceState = completedTrace();
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "POST_COMPLETION_EVENT");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldThrowIllegalStateException_WhenPersistedExpectationHasNameWithoutDeadline() {
    // Arrange
    TraceState traceState =
        new TraceState(
            TRACE_ID,
            "evt-existing",
            "APPLICATION_RECEIVED",
            EventResult.SUCCESS,
            OCCURRED_AT,
            UPDATED_AT,
            "RULES_EVALUATED",
            null,
            null,
            1,
            CREATED_AT,
            UPDATED_AT);
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(IllegalStateException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldThrowIllegalStateException_WhenPersistedExpectationHasDeadlineWithoutName() {
    // Arrange
    TraceState traceState =
        new TraceState(
            TRACE_ID,
            "evt-existing",
            "APPLICATION_RECEIVED",
            EventResult.SUCCESS,
            OCCURRED_AT,
            UPDATED_AT,
            null,
            RECEIVED_AT.plusSeconds(60),
            null,
            1,
            CREATED_AT,
            UPDATED_AT);
    TraceSnapshot before = TraceSnapshot.from(traceState);
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    when(traceEventRepository.findById(request.eventId())).thenReturn(Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));

    // Act / Assert
    assertThrows(IllegalStateException.class, () -> service.ingest(request));
    before.assertMatches(traceState);
    verifyRejectedEventWasNotPersisted();
  }

  @Test
  void shouldReturnDuplicateAfterRollback_WhenOptimisticLockFailureAndMatchingEventAppears() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    TraceEvent matchingEvent = traceEventFrom(request, RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.of(matchingEvent));
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(new OptimisticLockingFailureException("stale trace"))
        .when(traceEventRepository)
        .flush();

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    assertEquals(request.eventId(), response.eventId());
    assertRecoveryEventReadHappenedAfterRollbackFromFlush(request.eventId());
  }

  @Test
  void shouldThrowConflictAfterRollback_WhenOptimisticLockFailureAndConflictingEventAppears() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    TraceEvent conflictingEvent =
        new TraceEvent(
            request.eventId(),
            TRACE_ID,
            "DIFFERENT_EVENT",
            EventResult.SUCCESS,
            OCCURRED_AT,
            RECEIVED_AT,
            null,
            null,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.of(conflictingEvent));
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(new OptimisticLockingFailureException("stale trace"))
        .when(traceEventRepository)
        .flush();

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    assertRecoveryEventReadHappenedAfterRollbackFromFlush(request.eventId());
  }

  @Test
  void shouldThrowConcurrencyConflictAfterRollback_WhenOptimisticLockFailureAndEventAbsent() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(new OptimisticLockException("stale trace")).when(traceEventRepository).flush();

    // Act / Assert
    WatchdogConflictException exception =
        assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    assertEquals("Trace was updated concurrently", exception.getMessage());
    assertRecoveryEventReadHappenedAfterRollbackFromFlush(request.eventId());
  }

  @Test
  void shouldReturnDuplicateAfterRollback_WhenIntegrityViolationAndMatchingEventAppears() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    TraceEvent matchingEvent = traceEventFrom(request, RECEIVED_AT, emptyMetadata());
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.of(matchingEvent));
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(new DataIntegrityViolationException("duplicate event"))
        .when(traceEventRepository)
        .save(any(TraceEvent.class));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    assertRecoveryEventReadHappenedAfterRollbackFromEventSave(request.eventId());
  }

  @Test
  void shouldThrowConflictAfterRollback_WhenIntegrityViolationAndConflictingEventAppears() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    TraceEvent conflictingEvent =
        new TraceEvent(
            request.eventId(),
            TRACE_ID,
            "DIFFERENT_EVENT",
            EventResult.SUCCESS,
            OCCURRED_AT,
            RECEIVED_AT,
            null,
            null,
            false,
            emptyMetadata());
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.of(conflictingEvent));
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(new DataIntegrityViolationException("duplicate event"))
        .when(traceEventRepository)
        .save(any(TraceEvent.class));

    // Act / Assert
    assertThrows(WatchdogConflictException.class, () -> service.ingest(request));
    assertRecoveryEventReadHappenedAfterRollbackFromEventSave(request.eventId());
  }

  @Test
  void shouldRetryOnceWithSameReceivedAt_WhenTraceCreationRaceCreatesTrace() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState concurrentlyCreatedTrace = activeTrace();
    DataIntegrityViolationException creationFailure =
        new DataIntegrityViolationException("trace already exists");
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.empty(), Optional.empty());
    when(traceStateRepository.findById(TRACE_ID))
        .thenReturn(Optional.empty(), Optional.of(concurrentlyCreatedTrace));
    when(traceStateRepository.existsById(TRACE_ID)).thenReturn(true);
    when(traceStateRepository.saveAndFlush(any(TraceState.class)))
        .thenThrow(creationFailure)
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertFalse(response.duplicate());
    assertEquals(request.eventId(), response.eventId());
    assertEquals(request.eventId(), concurrentlyCreatedTrace.getLastEventId());
    assertEquals(RECEIVED_AT, concurrentlyCreatedTrace.getLastEventReceivedAt());
    assertEquals(RECEIVED_AT, concurrentlyCreatedTrace.getUpdatedAt());
    assertEquals(RECEIVED_AT, savedTraceEvent().getReceivedAt());
    assertEquals(1, clock.instantCalls());
    verify(traceStateRepository).existsById(TRACE_ID);
    verify(traceEventRepository, times(3)).findById(request.eventId());
    verify(traceStateRepository, times(2)).findById(TRACE_ID);
    verify(traceEventRepository).save(any(TraceEvent.class));
    assertTraceCreationRetrySucceededSequence(request.eventId());
  }

  @Test
  void shouldReturnDuplicate_WhenTraceCreationRetryOptimisticLockFailureAndMatchingEventVisible() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState concurrentlyCreatedTrace = activeTrace();
    TraceEvent matchingEvent = traceEventFrom(request, RECEIVED_AT, emptyMetadata());
    DataIntegrityViolationException creationFailure =
        new DataIntegrityViolationException("trace already exists");
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(matchingEvent));
    when(traceStateRepository.findById(TRACE_ID))
        .thenReturn(Optional.empty(), Optional.of(concurrentlyCreatedTrace));
    when(traceStateRepository.existsById(TRACE_ID)).thenReturn(true);
    when(traceStateRepository.saveAndFlush(any(TraceState.class)))
        .thenThrow(creationFailure)
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(new OptimisticLockingFailureException("stale trace"))
        .when(traceEventRepository)
        .flush();

    // Act
    EventIngestionResponse response = service.ingest(request);

    // Assert
    assertTrue(response.duplicate());
    assertEquals(request.eventId(), response.eventId());
    assertEquals(1, clock.instantCalls());
    verify(traceEventRepository, times(4)).findById(request.eventId());
    verify(traceStateRepository, times(2)).findById(TRACE_ID);
    verify(traceStateRepository).existsById(TRACE_ID);
    verify(transactionManager, times(2)).rollback(any(TransactionStatus.class));
    assertTraceCreationRetryOptimisticRecoverySequence(request.eventId());
  }

  @Test
  void shouldPropagateIntegrityViolation_WhenRetryHasUnexplainedIntegrityFailure() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState concurrentlyCreatedTrace = activeTrace();
    DataIntegrityViolationException creationFailure =
        new DataIntegrityViolationException("trace already exists");
    DataIntegrityViolationException retryFailure =
        new DataIntegrityViolationException("unexplained retry failure");
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    when(traceStateRepository.findById(TRACE_ID))
        .thenReturn(Optional.empty(), Optional.of(concurrentlyCreatedTrace));
    when(traceStateRepository.existsById(TRACE_ID)).thenReturn(true);
    when(traceStateRepository.saveAndFlush(any(TraceState.class)))
        .thenThrow(creationFailure)
        .thenAnswer(invocation -> invocation.getArgument(0));
    doThrow(retryFailure).when(traceEventRepository).save(any(TraceEvent.class));

    // Act / Assert
    DataIntegrityViolationException exception =
        assertThrows(DataIntegrityViolationException.class, () -> service.ingest(request));
    assertSame(retryFailure, exception);
    assertEquals(1, clock.instantCalls());
    assertTraceCreationRetryIntegrityPropagationSequence(request.eventId());
    verify(traceEventRepository, times(4)).findById(request.eventId());
    verify(traceStateRepository, times(2)).findById(TRACE_ID);
  }

  @Test
  void shouldPropagateIntegrityViolationWithoutRetry_WhenViolationIsUnrelatedToTraceCreation() {
    // Arrange
    EventRequest request = request("evt-unit-002", TRACE_ID, "RULES_EVALUATED");
    TraceState traceState = waitingTrace(RECEIVED_AT.plusSeconds(60));
    DataIntegrityViolationException failure =
        new DataIntegrityViolationException("unrelated integrity failure");
    when(traceEventRepository.findById(request.eventId()))
        .thenReturn(Optional.empty(), Optional.empty());
    when(traceStateRepository.findById(TRACE_ID)).thenReturn(Optional.of(traceState));
    doThrow(failure).when(traceEventRepository).save(any(TraceEvent.class));

    // Act / Assert
    DataIntegrityViolationException exception =
        assertThrows(DataIntegrityViolationException.class, () -> service.ingest(request));
    assertSame(failure, exception);
    verify(traceStateRepository, never()).existsById(any());
    verify(traceStateRepository, times(1)).findById(TRACE_ID);
    assertRecoveryEventReadHappenedAfterRollbackFromEventSave(request.eventId());
  }

  private EventRequest basicRequest() {
    return request(EVENT_ID, TRACE_ID, "APPLICATION_RECEIVED");
  }

  private EventRequest request(String eventId, String traceId, String eventName) {
    return request(
        eventId,
        traceId,
        eventName,
        EventResult.SUCCESS,
        OCCURRED_AT,
        null,
        null,
        false,
        emptyMetadata());
  }

  private EventRequest request(
      String eventId,
      String traceId,
      String eventName,
      EventResult result,
      Instant occurredAt,
      String nextExpectedEvent,
      Integer nextEventTtlSeconds,
      Boolean finalEvent,
      JsonNode metadata) {
    return new EventRequest(
        eventId,
        traceId,
        eventName,
        result,
        occurredAt,
        nextExpectedEvent,
        nextEventTtlSeconds,
        finalEvent,
        metadata);
  }

  private TraceState activeTrace() {
    return new TraceState(
        TRACE_ID,
        "evt-existing",
        "APPLICATION_RECEIVED",
        EventResult.SUCCESS,
        OCCURRED_AT.minusSeconds(120),
        UPDATED_AT,
        null,
        null,
        null,
        1,
        CREATED_AT,
        UPDATED_AT);
  }

  private TraceState waitingTrace(Instant deadline) {
    return new TraceState(
        TRACE_ID,
        "evt-existing",
        "APPLICATION_RECEIVED",
        EventResult.SUCCESS,
        OCCURRED_AT.minusSeconds(120),
        UPDATED_AT,
        "RULES_EVALUATED",
        deadline,
        null,
        1,
        CREATED_AT,
        UPDATED_AT);
  }

  private TraceState completedTrace() {
    return new TraceState(
        TRACE_ID,
        "evt-existing",
        "FLOW_COMPLETED",
        EventResult.SUCCESS,
        OCCURRED_AT.minusSeconds(120),
        UPDATED_AT,
        null,
        null,
        UPDATED_AT,
        1,
        CREATED_AT,
        UPDATED_AT);
  }

  private TraceEvent traceEventFrom(EventRequest request, Instant receivedAt, JsonNode metadata) {
    return new TraceEvent(
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
  }

  private TraceState firstSavedTraceState() {
    ArgumentCaptor<TraceState> captor = ArgumentCaptor.forClass(TraceState.class);
    verify(traceStateRepository, times(2)).saveAndFlush(captor.capture());
    return captor.getAllValues().getFirst();
  }

  private TraceEvent savedTraceEvent() {
    ArgumentCaptor<TraceEvent> captor = ArgumentCaptor.forClass(TraceEvent.class);
    verify(traceEventRepository).save(captor.capture());
    TraceEvent event = captor.getValue();
    assertNotNull(event.getMetadata());
    return event;
  }

  private void verifyRejectedEventWasNotPersisted() {
    verify(traceEventRepository, never()).save(any(TraceEvent.class));
    verify(traceEventRepository, never()).flush();
    verify(traceStateRepository, never()).saveAndFlush(any(TraceState.class));
  }

  private void assertRecoveryEventReadHappenedAfterRollbackFromFlush(String eventId) {
    InOrder inOrder = inOrder(transactionManager, traceEventRepository);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceEventRepository).flush();
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
  }

  private void assertRecoveryEventReadHappenedAfterRollbackFromEventSave(String eventId) {
    InOrder inOrder = inOrder(transactionManager, traceEventRepository);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceEventRepository).save(any(TraceEvent.class));
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
  }

  private void assertTraceCreationRetrySucceededSequence(String eventId) {
    InOrder inOrder = inOrder(transactionManager, traceEventRepository, traceStateRepository);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceStateRepository).saveAndFlush(any(TraceState.class));
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceStateRepository).existsById(TRACE_ID);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceEventRepository).save(any(TraceEvent.class));
    inOrder.verify(traceStateRepository).saveAndFlush(any(TraceState.class));
    inOrder.verify(traceEventRepository).flush();
  }

  private void assertTraceCreationRetryOptimisticRecoverySequence(String eventId) {
    InOrder inOrder = inOrder(transactionManager, traceEventRepository, traceStateRepository);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceStateRepository).saveAndFlush(any(TraceState.class));
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceStateRepository).existsById(TRACE_ID);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceEventRepository).save(any(TraceEvent.class));
    inOrder.verify(traceStateRepository).saveAndFlush(any(TraceState.class));
    inOrder.verify(traceEventRepository).flush();
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
  }

  private void assertTraceCreationRetryIntegrityPropagationSequence(String eventId) {
    InOrder inOrder = inOrder(transactionManager, traceEventRepository, traceStateRepository);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceStateRepository).saveAndFlush(any(TraceState.class));
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceStateRepository).existsById(TRACE_ID);
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
    inOrder.verify(traceStateRepository).findById(TRACE_ID);
    inOrder.verify(traceEventRepository).save(any(TraceEvent.class));
    inOrder.verify(transactionManager).rollback(any(TransactionStatus.class));
    inOrder.verify(transactionManager).getTransaction(any(TransactionDefinition.class));
    inOrder.verify(traceEventRepository).findById(eventId);
  }

  private static JsonNode emptyMetadata() {
    return JsonNodeFactory.instance.objectNode();
  }

  private static JsonNode metadata(String json) {
    return JSON.readTree(json);
  }

  private static final class CountingClock extends Clock {

    private final Instant instant;
    private int instantCalls;

    private CountingClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      instantCalls++;
      return instant;
    }

    int instantCalls() {
      return instantCalls;
    }
  }

  private record TraceSnapshot(
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

    static TraceSnapshot from(TraceState traceState) {
      return new TraceSnapshot(
          traceState.getLastEventId(),
          traceState.getLastEventName(),
          traceState.getLastEventResult(),
          traceState.getLastEventOccurredAt(),
          traceState.getLastEventReceivedAt(),
          traceState.getNextExpectedEvent(),
          traceState.getNextExpectedBefore(),
          traceState.getCompletedAt(),
          traceState.getEventsReceived(),
          traceState.getUpdatedAt());
    }

    void assertMatches(TraceState traceState) {
      assertEquals(lastEventId, traceState.getLastEventId());
      assertEquals(lastEventName, traceState.getLastEventName());
      assertEquals(lastEventResult, traceState.getLastEventResult());
      assertEquals(lastEventOccurredAt, traceState.getLastEventOccurredAt());
      assertEquals(lastEventReceivedAt, traceState.getLastEventReceivedAt());
      assertEquals(nextExpectedEvent, traceState.getNextExpectedEvent());
      assertEquals(nextExpectedBefore, traceState.getNextExpectedBefore());
      assertEquals(completedAt, traceState.getCompletedAt());
      assertEquals(eventsReceived, traceState.getEventsReceived());
      assertEquals(updatedAt, traceState.getUpdatedAt());
    }
  }
}

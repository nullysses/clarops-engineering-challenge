package com.clara.challenge.watchdog.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TraceStatusCalculatorTest {

  private static final Instant NOW = Instant.parse("2026-06-15T10:00:00Z");
  private final TraceStatusCalculator calculator = new TraceStatusCalculator();

  @Test
  void shouldReturnCompleted_WhenCompletedAtIsPresent() {
    // Arrange
    Instant completedAt = Instant.parse("2026-06-15T09:59:00Z");

    // Act
    TraceStatus status = calculator.calculate(completedAt, null, null, NOW);

    // Assert
    assertEquals(TraceStatus.COMPLETED, status);
  }

  @Test
  void shouldReturnCompleted_WhenCompletedTraceHasStaleExpectation() {
    // Arrange
    Instant completedAt = Instant.parse("2026-06-15T09:59:00Z");
    Instant staleDeadline = Instant.parse("2026-06-15T09:59:30Z");

    // Act
    TraceStatus status = calculator.calculate(completedAt, "RULES_EVALUATED", staleDeadline, NOW);

    // Assert
    assertEquals(TraceStatus.COMPLETED, status);
  }

  @Test
  void shouldThrowIllegalStateException_WhenCompletedTraceHasExpectedEventWithoutDeadline() {
    // Arrange
    Instant completedAt = Instant.parse("2026-06-15T09:59:00Z");
    String nextExpectedEvent = "RULES_EVALUATED";

    // Act / Assert
    assertThrows(
        IllegalStateException.class,
        () -> calculator.calculate(completedAt, nextExpectedEvent, null, NOW));
  }

  @Test
  void shouldThrowIllegalStateException_WhenCompletedTraceHasDeadlineWithoutExpectedEvent() {
    // Arrange
    Instant completedAt = Instant.parse("2026-06-15T09:59:00Z");
    Instant staleDeadline = Instant.parse("2026-06-15T09:59:30Z");

    // Act / Assert
    assertThrows(
        IllegalStateException.class,
        () -> calculator.calculate(completedAt, null, staleDeadline, NOW));
  }

  @Test
  void shouldReturnStarted_WhenActiveTraceHasNoExpectation() {
    // Arrange
    Instant completedAt = null;

    // Act
    TraceStatus status = calculator.calculate(completedAt, null, null, NOW);

    // Assert
    assertEquals(TraceStatus.STARTED, status);
  }

  @Test
  void shouldReturnWaitingOtherEvent_WhenExpectationIsBeforeDeadline() {
    // Arrange
    Instant deadline = Instant.parse("2026-06-15T10:00:01Z");

    // Act
    TraceStatus status = calculator.calculate(null, "RULES_EVALUATED", deadline, NOW);

    // Assert
    assertEquals(TraceStatus.WAITING_OTHER_EVENT, status);
  }

  @Test
  void shouldReturnTtlExpiredForEvent_WhenExpectationIsExactlyAtDeadline() {
    // Arrange
    Instant deadline = NOW;

    // Act
    TraceStatus status = calculator.calculate(null, "RULES_EVALUATED", deadline, NOW);

    // Assert
    assertEquals(TraceStatus.TTL_EXPIRED_FOR_EVENT, status);
  }

  @Test
  void shouldReturnTtlExpiredForEvent_WhenExpectationIsAfterDeadline() {
    // Arrange
    Instant deadline = Instant.parse("2026-06-15T09:59:59Z");

    // Act
    TraceStatus status = calculator.calculate(null, "RULES_EVALUATED", deadline, NOW);

    // Assert
    assertEquals(TraceStatus.TTL_EXPIRED_FOR_EVENT, status);
  }

  @Test
  void shouldThrowIllegalStateException_WhenExpectedEventIsPresentWithoutDeadline() {
    // Arrange
    String nextExpectedEvent = "RULES_EVALUATED";

    // Act / Assert
    assertThrows(
        IllegalStateException.class,
        () -> calculator.calculate(null, nextExpectedEvent, null, NOW));
  }

  @Test
  void shouldThrowIllegalStateException_WhenDeadlineIsPresentWithoutExpectedEvent() {
    // Arrange
    Instant deadline = Instant.parse("2026-06-15T10:00:01Z");

    // Act / Assert
    assertThrows(
        IllegalStateException.class, () -> calculator.calculate(null, null, deadline, NOW));
  }
}

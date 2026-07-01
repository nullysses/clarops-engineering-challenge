-- ============================================================
-- Clarops Challenge — Initial Schema
-- Distributed event tracking, TTL expiration, and operational
-- flow analysis.
-- Idempotent — safe to re-execute.
-- Identifiers must be provided by the application layer.
-- ============================================================
CREATE
  SCHEMA IF NOT EXISTS clarops_challenge_schema;
SET
search_path TO clarops_challenge_schema;

-- -------------------------
-- health
-- Single-row table used by the health endpoint.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS health(
      id BIGSERIAL PRIMARY KEY,
      message VARCHAR(255) NOT NULL
    );

INSERT
  INTO
    health(message) SELECT
      'clarops sr engineer challenge'
    WHERE
      NOT EXISTS(
        SELECT
          1
        FROM
          health
      );

-- -------------------------
-- trace_state
-- Current trace facts used to calculate status. No mutable status
-- column is stored; status is derived by the application.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS trace_state(
      trace_id VARCHAR(120) PRIMARY KEY,
      last_event_id VARCHAR(120) NOT NULL,
      last_event_name VARCHAR(120) NOT NULL,
      last_event_result VARCHAR(20) NOT NULL,
      last_event_occurred_at TIMESTAMPTZ NOT NULL,
      last_event_received_at TIMESTAMPTZ NOT NULL,
      next_expected_event VARCHAR(120),
      next_expected_before TIMESTAMPTZ,
      completed_at TIMESTAMPTZ,
      events_received INTEGER NOT NULL,
      created_at TIMESTAMPTZ NOT NULL,
      updated_at TIMESTAMPTZ NOT NULL,
      version BIGINT NOT NULL DEFAULT 0,
      CONSTRAINT trace_state_last_event_result_check CHECK(last_event_result IN('SUCCESS', 'ERROR')),
      CONSTRAINT trace_state_expectation_pair_check CHECK(
        (
          next_expected_event IS NULL
          AND next_expected_before IS NULL
        )
        OR(
          next_expected_event IS NOT NULL
          AND next_expected_before IS NOT NULL
        )
      ),
      CONSTRAINT trace_state_deadline_after_last_event_check CHECK(
        next_expected_before IS NULL
        OR next_expected_before > last_event_received_at
      ),
      CONSTRAINT trace_state_completed_without_expectation_check CHECK(
        completed_at IS NULL
        OR(
          next_expected_event IS NULL
          AND next_expected_before IS NULL
        )
      ),
      CONSTRAINT trace_state_completed_after_last_event_check CHECK(
        completed_at IS NULL
        OR completed_at >= last_event_received_at
      ),
      CONSTRAINT trace_state_events_received_positive_check CHECK(events_received > 0),
      CONSTRAINT trace_state_version_non_negative_check CHECK(version >= 0)
    );

-- -------------------------
-- trace_event
-- Accepted event history. The logical request payload is stored so
-- duplicate requests can be compared without relying on a hash.
-- -------------------------
CREATE
  TABLE
    IF NOT EXISTS trace_event(
      event_id VARCHAR(120) PRIMARY KEY,
      trace_id VARCHAR(120) NOT NULL,
      event_name VARCHAR(120) NOT NULL,
      result VARCHAR(20) NOT NULL,
      occurred_at TIMESTAMPTZ NOT NULL,
      received_at TIMESTAMPTZ NOT NULL,
      next_expected_event VARCHAR(120),
      next_event_ttl_seconds INTEGER,
      final_event BOOLEAN NOT NULL DEFAULT FALSE,
      metadata JSONB NOT NULL DEFAULT '{}'::JSONB,
      CONSTRAINT trace_event_trace_id_fk FOREIGN KEY(trace_id) REFERENCES trace_state(trace_id),
      CONSTRAINT trace_event_result_check CHECK(result IN('SUCCESS', 'ERROR')),
      CONSTRAINT trace_event_expectation_pair_check CHECK(
        (
          next_expected_event IS NULL
          AND next_event_ttl_seconds IS NULL
        )
        OR(
          next_expected_event IS NOT NULL
          AND next_event_ttl_seconds IS NOT NULL
        )
      ),
      CONSTRAINT trace_event_ttl_positive_check CHECK(
        next_event_ttl_seconds IS NULL
        OR next_event_ttl_seconds > 0
      ),
      CONSTRAINT trace_event_final_without_expectation_check CHECK(
        final_event IS FALSE
        OR(
          next_expected_event IS NULL
          AND next_event_ttl_seconds IS NULL
        )
      ),
      CONSTRAINT trace_event_metadata_object_check CHECK(JSONB_TYPEOF(metadata) = 'object')
    );

CREATE
  INDEX
    IF NOT EXISTS idx_trace_event_trace_id_received_at
      ON trace_event(trace_id, received_at);

# Tasks

## Task 1 — Confirm assumptions and API decisions

**Status:** Complete

* Define how TTL is calculated.
* Define the exact expiration boundary.
* Define duplicate `eventId` behavior.
* Define unexpected event behavior.
* Define late expected-event behavior.
* Define behavior for events received after completion.
* Define ordering semantics: service arrival order versus `occurredAt`.
* Define HTTP responses for validation errors, conflicts, and missing traces.
* Document all decisions in `README.md`.

## Task 2 — Define the database model and DDL

**Status:** Complete

* Add a `trace_state` table for current trace facts.
* Add a `trace_event` table for accepted event history.
* Represent completion using `completed_at`.
* Add primary keys, foreign keys, constraints, and indexes.
* Store `metadata` as PostgreSQL `JSONB`.
* Enforce valid event results.
* Enforce paired expected-event fields.
* Enforce positive TTL values.
* Enforce completed traces without pending expectations.
* Add a `version` column for optimistic locking of existing trace rows.
* Use the `trace_id` primary key as the final safeguard against concurrent trace creation.
* Update `docker/init-scripts/db/01-init-schema.sql`.
* Verify schema initialization from a clean Docker volume.

## Task 3 — Add domain enums and API contracts

**Status:** Complete

* Define `EventResult` with `SUCCESS` and `ERROR`.
* Define `TraceStatus` with:
  * `STARTED`;
  * `WAITING_OTHER_EVENT`;
  * `TTL_EXPIRED_FOR_EVENT`;
  * `COMPLETED`.
* Define `EventRequest` for `POST /events`.
* Define the response contract for newly accepted and idempotent events.
* Define `TraceStatusResponse`.
* Define a consistent `ApiErrorResponse`.

## Task 4 — Implement request validation and error handling

**Status:** Complete

* Validate required fields.
* Validate allowed `result` values.
* Validate `occurredAt` as an ISO-8601 timestamp.
* Require `nextExpectedEvent` and `nextEventTtlSeconds` to be provided together.
* Reject TTL values less than or equal to zero.
* Reject `finalEvent = true` with another expected event.
* Return consistent validation errors.
* Add global exception handling for `400`, `404`, and `409`.

## Task 5 — Implement the persistence layer

**Status:** Complete

* Create the JPA entity for `TraceState`.
* Map `completed_at` as the completion fact.
* Map the optimistic-locking `version` column using `@Version`.
* Create the JPA entity for `TraceEvent`.
* Map metadata to PostgreSQL `JSONB`.
* Add `TraceStateRepository`.
* Add `TraceEventRepository`.
* Add event lookup by `eventId`.
* Add trace lookup by `traceId`.
* Support comparison of persisted logical event fields, including structured metadata.

## Task 6 — Implement trace-status calculation

**Status:** Complete

* Define a UTC `Clock` bean.
* Inject `Clock` into time-dependent services.
* Calculate status from persisted facts rather than a stored status column.
* Return `COMPLETED` when `completedAt` is present.
* Return `STARTED` when the trace is active without an expected event.
* Return `WAITING_OTHER_EVENT` when an expectation exists and the deadline has not passed.
* Return `TTL_EXPIRED_FOR_EVENT` when `now >= nextExpectedBefore`.
* Unit-test this logic independently from Spring.

## Task 7 — Implement event ingestion

**Status:** Complete

* Process event ingestion in one transaction.
* Look up an existing event by `eventId` before modifying trace state.
* Treat an exact logical duplicate as idempotent.
* Reject reuse of an `eventId` with different logical content.
* Create trace state for the first accepted event.
* Persist accepted event history.
* Advance a trace when the expected event arrives.
* Reject unexpected event names.
* Reject expected events arriving at or after the deadline.
* Reject new non-duplicate events after completion.
* Update trace state for:
  * final events;
  * events defining another expectation;
  * events leaving the trace active without an expectation.
* Clear pending expectations when the flow completes.
* Use optimistic locking to detect conflicting updates to existing traces.
* Explicitly translate or retry primary-key conflicts caused by concurrent creation of the same `traceId`.

## Task 8 — Implement HTTP endpoints

**Status:** Complete

* Add `POST /events`, exposed externally as `POST /api/events`.
* Add `GET /traces/{traceId}/status`, exposed externally as `GET /api/traces/{traceId}/status`.
* Return `201 Created` for newly accepted events.
* Return `200 OK` for exact idempotent duplicates.
* Return `400 Bad Request` for invalid contracts.
* Return `409 Conflict` for conflicting duplicates and invalid state transitions.
* Return `404 Not Found` for unknown traces.
* Return current status with:
  * latest event information;
  * current expectation;
  * expectation deadline;
  * accepted event count;
  * completion information.

## Task 9 — Add unit tests

**Status:** Complete

Testing standard:

* Test names follow `shouldExpectedBehavior_WhenCondition`.
* Each test validates one business rule.
* Tests use Arrange / Act / Assert.
* Time-dependent tests use a fixed or controllable `Clock`.
* Tests focus on domain behavior rather than Spring wiring.
* Tests do not invent behavior that is not required or documented as an assumption.

Required scenarios:

* First event creates a trace.
* Event without another expectation produces `STARTED`.
* Event with a next expectation produces `WAITING_OTHER_EVENT`.
* Status before the deadline remains waiting.
* Status exactly at the deadline is expired.
* Status after the deadline is expired.
* Final event produces `COMPLETED`.
* Completed trace never reports expiration.
* Exact duplicate is idempotent.
* Conflicting duplicate is rejected.
* Structured metadata comparison is independent of JSON field order.
* Unexpected event is rejected.
* Late expected event is rejected.
* Event after completion is rejected.
* Conflicting optimistic-lock update is not silently accepted.

Verification completed:

* `EventIngestionServiceTest` contains 30 focused tests.
* The full Maven suite contains 41 tests with 0 failures and 0 errors.
* Transaction rollback ordering, bounded retry behavior, duplicate short-circuiting, and timestamp reuse were reviewed.
* The test file was committed to `develop` and reviewed before Task 9 was marked complete.

## Task 10 — Add Hurl end-to-end tests

**Status:** Complete

* Add `hurl/started-flow.hurl`.
* Add `hurl/waiting-other-event-flow.hurl`.
* Add `hurl/completed-flow.hurl`.
* Add `hurl/ttl-expired-flow.hurl`.
* Add selected conflict scenarios for:
  * exact duplicate event;
  * conflicting duplicate event;
  * unexpected event;
  * late event;
  * event after completion.
* Validate only public HTTP API responses.
* Avoid direct database assertions.
* Use unique event and trace identifiers per scenario.
* Keep TTL scenarios deterministic and resistant to timing flakiness.
* Repeat all scenarios from a clean environment during Task 12 final verification.

Verification completed:

* Hurl 8.0.0 parsed and executed all four required files.
* The first run completed 20 requests with 0 failures.
* A repeat run with a distinct `run_id` completed the same 20 requests with 0 failures.
* The repeat run confirmed identifier isolation across executions.
* `delay: 3000ms` is used for the deterministic expiration scenario.
* No API defect was discovered.
* Clean-volume execution remains explicitly assigned to Task 12.

## Task 11 — Complete documentation

**Status:** Complete

Update `README.md` with:

* Problem understanding.
* Assumptions.
* Technical design.
* Database model.
* TTL calculation.
* State-transition rules.
* Duplicate, unexpected, late, and post-completion behavior.
* Concurrency limitations and safeguards.
* Request and response examples.
* Setup instructions.
* Unit-test instructions.
* Hurl-test instructions.
* Known limitations.
* Possible production improvements.

Update `AI_USAGE.md` with:

* Tools used.
* Main prompts.
* Unit-test prompt.
* Hurl-test prompt.
* AI-assisted areas.
* Accepted suggestions.
* Rejected suggestions.
* Manual corrections.
* Review performed on generated output.

Completion notes:

* `README.md` documents the implemented problem understanding, assumptions, technical design,
  database model, state transitions, duplicate/conflict behavior, setup, unit tests, Hurl tests,
  limitations, and production improvements.
* `AI_USAGE.md` records the prompts, accepted and rejected suggestions, manual corrections,
  generated-output review, Task 9 results, Task 10 results, and Task 12 final verification record.
* Documentation was reviewed against the implementation for `/api` routes, DTO fields, status values,
  error codes, TTL semantics, duplicate equality, metadata normalization, concurrency safeguards,
  unit-test counts, and Hurl request counts.

## Task 12 — Final verification

**Status:** Complete

* Run `./mvnw clean spotless:apply verify`.
* Run all unit tests.
* Reset Docker volumes.
* Start the application from a clean database.
* Confirm `/api/health` works.
* Run every Hurl scenario.
* Repeat the core API scenarios manually if necessary.
* Review `README.md`, `TASKS.md`, `AI_USAGE.md`, code, DDL, and tests for consistency.
* Confirm that all generated code can be explained and defended.
* Confirm that the repository contains every required deliverable.

Completion notes:

* `./mvnw clean spotless:apply verify` completed successfully with 41 tests, 0 failures, 0 errors,
  and 0 skipped tests.
* `./mvnw -q test` completed successfully; Surefire reports 41 tests, 0 failures, 0 errors, and 0
  skipped tests.
* The PostgreSQL Docker volume was deleted and recreated; the clean schema was verified from
  `docker/init-scripts/db/01-init-schema.sql`.
* `/api/health` returned HTTP 200 with `clarops sr engineer challenge`.
* Hurl 8.0.0 executed 4 files and 20 public API requests from the clean database with 0 failures.
* Manual API repetition was not needed because the Hurl scenarios fully exercised the documented
  public flows.

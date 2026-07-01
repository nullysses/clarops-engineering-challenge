# Tasks

## Task 1 — Analyze requirements and define assumptions

* Define how TTL is calculated.
* Define the exact expiration boundary.
* Define duplicate `eventId` behavior.
* Define unexpected event behavior.
* Define late expected-event behavior.
* Define behavior for events received after completion.
* Define event ordering semantics.
* Define HTTP responses for validation, conflicts, and missing traces.
* Document all decisions in `README.md`.

## Task 2 — Define the database model and DDL

* Define a table for current trace state.
* Define a table for received event history.
* Define primary keys, foreign keys, constraints, and indexes.
* Store flexible event metadata as PostgreSQL `JSONB`.
* Add optimistic locking or another concurrency safeguard for trace updates.
* Update `docker/init-scripts/db/01-init-schema.sql`.
* Verify schema initialization from a clean Docker volume.

## Task 3 — Implement API contracts and validation

* Define the request contract for `POST /events`.
* Define the response contract for accepted events.
* Define the response contract for `GET /traces/{traceId}/status`.
* Validate required fields.
* Validate allowed `result` values.
* Validate paired next-event fields.
* Reject contradictory requests, such as a final event defining another expected event.
* Define a consistent API error response.

## Task 4 — Implement trace status calculation

* Implement `STARTED`.
* Implement `WAITING_OTHER_EVENT`.
* Implement `TTL_EXPIRED_FOR_EVENT`.
* Implement `COMPLETED`.
* Calculate status from persisted trace facts rather than storing redundant status values.
* Inject `Clock` so time-dependent behavior can be tested deterministically.

## Task 5 — Implement event ingestion

* Detect duplicate event IDs.
* Create trace state for the first accepted event.
* Persist accepted event history.
* Advance a trace when the expected event arrives.
* Reject unexpected events according to the documented assumptions.
* Reject late events according to the documented assumptions.
* Prevent non-duplicate events after trace completion.
* Apply event persistence and trace updates in one transaction.
* Protect concurrent trace updates from inconsistent state.

## Task 6 — Implement trace status endpoint

* Load trace state by `traceId`.
* Return `404` when the trace does not exist.
* Calculate TTL expiration when the endpoint is called.
* Return the latest event information.
* Return the current expectation and deadline when applicable.
* Return the number of accepted events.

## Task 7 — Add unit tests

Testing standard:

* Test names follow `shouldExpectedBehavior_WhenCondition`.
* Each test validates one business rule.
* Tests use Arrange / Act / Assert structure.
* Time-dependent tests use a fixed or controllable `Clock`.
* Tests focus on domain behavior rather than Spring wiring.
* Tests do not invent behavior that is not required or documented as an assumption.

Required scenarios:

* First event creates a trace.
* Event without another expectation produces `STARTED`.
* Event with a next expectation produces `WAITING_OTHER_EVENT`.
* Status before the deadline remains waiting.
* Status at or after the deadline is expired.
* Final event produces `COMPLETED`.
* Completed trace never reports expiration.
* Duplicate event behavior.
* Unexpected event behavior.
* Late event behavior.
* Event after completion behavior.

## Task 8 — Add Hurl end-to-end tests

* Add a scenario that reaches `STARTED`.
* Add a scenario that reaches `WAITING_OTHER_EVENT`.
* Add a scenario that reaches `COMPLETED`.
* Add a scenario that reaches `TTL_EXPIRED_FOR_EVENT`.
* Add scenarios for selected duplicate, unexpected, and late-event decisions.
* Validate only the public HTTP API.
* Avoid direct database assertions unless explicitly justified.
* Ensure all Hurl scenarios pass from a clean environment.

## Task 9 — Complete documentation

Update `README.md` with:

* Problem understanding.
* Assumptions.
* Technical design.
* Database model.
* TTL calculation.
* State-transition rules.
* Duplicate, unexpected, late, and post-completion behavior.
* Trade-offs.
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

## Task 10 — Final verification

* Run `./mvnw clean spotless:apply verify`.
* Run all unit tests.
* Run all Hurl scenarios.
* Reset Docker volumes.
* Start the project from a clean database.
* Repeat the health check.
* Repeat all API scenarios.
* Review all generated code and documentation for consistency.
* Confirm that the repository contains every required deliverable.

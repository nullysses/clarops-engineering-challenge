# AI Usage

This document records how AI tools were used during the challenge. It will be updated as implementation progresses.

## Tools Used

* ChatGPT
* Codex

## Main Prompts

### Prompt 1 — Repository and requirement analysis

> Review the Clara Clarops engineering challenge repository. Identify the explicit requirements, ambiguous areas, likely evaluation criteria, implementation risks, and an appropriate MVP design.

### Prompt 2 — Technical design

> Propose a simple Spring Boot and PostgreSQL design for the distributed event watchdog challenge. Avoid Kafka, schedulers, migration frameworks, distributed locks, and other out-of-scope infrastructure. Focus on event ingestion, trace state, TTL expiration, completion, persistence, and testability.

### Prompt 3 — Task breakdown

> Split the challenge into small, explicit implementation tasks suitable for an AI-assisted workflow. Avoid broad tasks that require the AI to infer the entire solution. Include database design, validation, state transitions, tests, Hurl scenarios, documentation, and final verification.

The Codex-generated task breakdown was compared against the existing `TASKS.md`.

### Prompt 4 — Apply clarified design decisions ans Task 2 implementation

> Before implementing Task 2, update your working assumptions to match these decisions:
>
> * Use two tables: `trace_state` for current trace facts and `trace_event` for accepted event history.
> * Do not persist a mutable trace-status column. Derive status from completion and expectation facts.
> * Represent completion using `completed_at`, not both `completed` and `completed_at`.
> * TTL is calculated from the service acceptance time using an injected UTC `Clock`.
> * A trace expires when `now >= nextExpectedBefore`.
> * Optimistic locking protects updates to existing trace rows only.
> * Concurrent creation of the same `traceId` must be handled through the database primary-key constraint and explicit application-level conflict handling or retry.
> * Do not rely on a payload hash as the sole mechanism for duplicate detection.
> * Exact duplicates must be identified by comparing the complete logical request payload, including structured JSON metadata.
> * JSON object field order must not affect duplicate equality.
> * A reused `eventId` with different logical content returns `409 Conflict`.
> * Expected-event fields must be paired.
> * TTL values must be positive.
> * Completed traces cannot retain a pending expectation.
> * An expected event arriving at or after the deadline is late and must not revive the trace.
> * New non-duplicate events after completion return `409 Conflict`.
> * The application context path is already `/api`; controllers should map `/events` and `/traces/{traceId}/status`.
>
> Review the existing `README.md`, `TASKS.md`, and `AI_USAGE.md` before generating code.
>
> For the next step, implement only Task 2: define the database model and update `docker/init-scripts/db/01-init-schema.sql`.
>
> Do not implement Java entities, repositories, controllers, services, or tests yet.
>
> Before editing, summarize:
>
> 1. the proposed tables and columns;
> 2. every primary key, foreign key, check constraint, and index;
> 3. how the schema supports duplicate detection, trace status calculation, and concurrency;
> 4. any remaining ambiguity.
>
> Then make the DDL changes and report exactly what changed.

### Prompt 5 — Implement Task 3: domain enums and API contracts

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, and the Task 2 DDL before editing.
>
> Implement only Task 3: domain enums and API contracts.
>
> Create:
>
> * `EventResult` with:
>
>   * `SUCCESS`
>   * `ERROR`
> * `TraceStatus` with:
>
>   * `STARTED`
>   * `WAITING_OTHER_EVENT`
>   * `TTL_EXPIRED_FOR_EVENT`
>   * `COMPLETED`
> * `EventRequest` for `POST /events`
> * a response contract for newly accepted and idempotent events
> * `TraceStatusResponse`
> * `ApiErrorResponse`
>
> Requirements:
>
> * Use Java 21.
> * Prefer records for API request and response contracts.
> * Keep API DTOs separate from JPA entities.
> * Use `Instant` for timestamps.
> * Use `JsonNode` or another structured Jackson type for metadata; do not use raw JSON strings.
> * `finalEvent` should default to `false` when omitted.
> * Do not add controllers, services, repositories, JPA entities, exception handlers, validation annotations, tests, or persistence code yet.
> * Do not modify the DDL unless a direct inconsistency is discovered.
> * Do not introduce MapStruct or additional dependencies.
>
> Suggested `EventRequest` fields:
>
> * `String eventId`
> * `String traceId`
> * `String eventName`
> * `EventResult result`
> * `Instant occurredAt`
> * `String nextExpectedEvent`
> * `Integer nextEventTtlSeconds`
> * `Boolean finalEvent`
> * `JsonNode metadata`
>
> Suggested event response fields:
>
> * `String eventId`
> * `String traceId`
> * `boolean duplicate`
>
> Suggested trace-status response fields:
>
> * `String traceId`
> * `TraceStatus status`
> * `String lastEventId`
> * `String lastEventName`
> * `EventResult lastEventResult`
> * `String nextExpectedEvent`
> * `Instant nextExpectedBefore`
> * `int eventsReceived`
> * `Instant completedAt`
>
> Suggested error response fields:
>
> * `String code`
> * `String message`
> * `Instant timestamp`
>
> Before editing:
>
> 1. summarize the proposed package structure;
> 2. list the exact files to create;
> 3. identify any contract ambiguity.
>
> Then implement only Task 3 and report:
>
> 1. files created or modified;
> 2. design decisions made;
> 3. commands run;
> 4. any unresolved issues.

## AI-Assisted Areas

So far, AI assistance has been used for:

* Requirement analysis.
* Identification of ambiguous behavior.
* Initial technical design.
* Database-model recommendations.
* Task decomposition.
* Unit-test strategy.
* Hurl-test strategy.
* Documentation structure.
* Local environment setup guidance.
* Task 2 PostgreSQL DDL generation.
* Definition of database constraints, foreign keys, and indexes for trace state and event history.
* Task 3 domain enum and API contract generation.
* Java 21 record-based request and response contract design.
* Resolution of the project-specific Jackson 3 `JsonNode` package.

Implementation assistance will continue to be documented as it occurs.

## Accepted Suggestions

The following suggestions were accepted for the implementation plan:

* Keep the solution within the requested MVP scope.
* Use a small layered structure separating API, domain logic, persistence, and configuration.
* Use PostgreSQL for both event history and current trace state.
* Preserve accepted event history for auditability.
* Maintain a separate current-state record for efficient status queries.
* Store metadata as PostgreSQL `JSONB`.
* Evaluate TTL lazily when status is queried.
* Avoid a scheduler because the challenge explicitly permits lazy expiration.
* Avoid Kafka, SQS, Pub/Sub, Flyway, Liquibase, distributed locks, and other out-of-scope infrastructure.
* Inject `Clock` for deterministic TTL calculations and tests.
* Keep trace-status calculation in a Spring-independent domain component.
* Derive the externally reported status from persisted facts rather than storing a redundant mutable status field.
* Use `completed_at` rather than a completion boolean so completion state and timestamp are represented together.
* Keep event ingestion transactional so accepted event history and current trace state cannot be updated independently.
* Reject late events during ingestion as well as reporting expiration during status queries.
* Use database constraints and transactional updates to reduce inconsistent state.
* Treat database primary keys and unique constraints as the final safeguards for concurrent trace creation and duplicate event IDs.
* Compare duplicate requests using persisted logical fields, including structured JSON metadata, rather than relying only on a payload hash.
* Enforce paired expectation fields and completion invariants with database `CHECK` constraints.
* Use the existing `/api` context path while mapping controllers to `/events` and `/traces/{traceId}/status`.
* Return a consistent API error structure for validation, conflict, and missing-resource responses.
* Create planning and AI-usage documentation before implementing application code.
* Split API contracts, request validation, persistence, status calculation, event ingestion, and controllers into separate tasks.
* Explicitly define domain enums and response contracts.
* Name the required Hurl scenario files.
* Separate domain unit tests from Spring integration concerns.
* Add explicit test scenarios for exact and conflicting duplicates.
* Use `trace_state` for current trace facts and `trace_event` for accepted event history.
* Enforce valid event results, paired expectation fields, positive TTL values, completion invariants, positive event counts, non-negative optimistic-lock versions, and object-only metadata at the database level.
* Use `trace_event.event_id` as the first lookup for duplicate detection while keeping exact-versus-conflicting comparison in application code.
* Use PostgreSQL `JSONB` semantics so JSON object field order does not affect metadata equality.
* Add an index on `trace_event(trace_id, received_at)` for ordered trace-history access.
* Keep domain enums under `com.clara.challenge.watchdog.domain`.
* Keep public API records under `com.clara.challenge.watchdog.api`, separate from future JPA entities.
* Use Java records for request and response contracts.
* Use `Instant` for API timestamps.
* Use Jackson's structured `JsonNode` type for metadata rather than raw JSON strings.
* Normalize an omitted `finalEvent` value to `false`.
* Use one `EventIngestionResponse` contract for both newly accepted and idempotent duplicate events, distinguished by a `duplicate` flag.

## Initial Design Decisions

These decisions must remain consistent across the code, tests, and documentation:

* TTL will be calculated from the time the service accepts the event rather than from client-provided `occurredAt`.
* A trace is expired when the current time is equal to or later than the deadline.
* An exact duplicate event is treated as idempotent.
* A reused `eventId` with different logical content is treated as a conflict.
* Duplicate equality will be determined by comparing the complete logical request payload, including structured metadata, rather than Java entity equality, raw serialized JSON, or a payload hash alone.
* An unexpected event does not advance the trace.
* An expected event arriving at or after expiration is rejected and does not revive the trace.
* A completed trace does not accept new non-duplicate events.
* A first event may also be a final event.
* Event `result` and trace lifecycle are independent.
* Metadata is persisted but is not interpreted for state transitions.
* Unknown traces return `404 Not Found`.
* Arrival order is authoritative for state transitions; `occurredAt` is retained as event information.
* Completion is represented by `completed_at`; a non-null value means the trace is completed.
* The database will enforce that expected-event names and deadlines are either both present or both absent.
* A completed trace cannot retain a pending expected event.
* Event-history records will enforce that next-event fields are paired and that TTL values are positive.
* Accepted-event counts must be positive.
* Optimistic locking will detect conflicting updates to existing traces.
* Database primary-key constraints will detect concurrent creation of the same `traceId`; the application must explicitly translate or retry those failures.
* The externally exposed endpoints are `/api/events` and `/api/traces/{traceId}/status` because the application defines `/api` as its servlet context path.
* API request and response contracts are Java records and remain independent from persistence entities.
* API timestamps use `Instant`.
* Metadata uses `tools.jackson.databind.JsonNode`, matching the Jackson 3 packages resolved by Spring Boot 4.
* Omitted `finalEvent` values are normalized to `false` in the `EventRequest` compact constructor.
* Event ingestion responses expose `eventId`, `traceId`, and whether the request was an idempotent duplicate.

These decisions may be revised if implementation reveals a stronger alternative. Any revision will be recorded below.

## Rejected Suggestions

The following suggestions or possible approaches were rejected:

* Kafka or another message broker, because external event infrastructure is out of scope.
* A scheduler or background expiration job, because lazy TTL evaluation is sufficient.
* Flyway or Liquibase, because the challenge explicitly requests a plain SQL initialization script.
* Distributed locking, because it is out of scope for the MVP.
* Storing only event history and rebuilding trace state on every request, because it adds unnecessary query and transition complexity.
* Storing only current trace state, because it loses event history and weakens duplicate detection and auditability.
* Persisting a mutable status field, because it can become inconsistent with completion and TTL facts.
* Storing both `completed` and `completed_at`, because the boolean would duplicate information already represented by the timestamp.
* Relying only on optimistic locking for concurrent creation of a new trace, because no existing row is available to lock.
* Treating optimistic locking as sufficient for concurrent trace creation, because `@Version` cannot protect a row that does not yet exist.
* Relying on a payload hash as the sole source of truth for duplicate equality, because canonicalization and hash generation add unnecessary complexity for this MVP.
* Treating duplicate requests as equal using entity equality or raw serialized JSON, because metadata field order could produce false conflicts.
* Adding an additional Git implementation branch, because development will continue directly on the fork's `develop` branch.
* Installing SDKMAN, because the development environment already provides a valid Java 21 installation.
* Treating a clean database reset as something Codex should execute automatically, because removing Docker volumes is a destructive local-environment operation that requires manual execution.
* Accepting the partial pending-deadline index without review; it will be retained only if its value is documented, because the MVP status endpoint looks up traces by primary key rather than scanning pending traces.
* Using `com.fasterxml.jackson.databind.JsonNode`, because this Spring Boot 4 project resolves Jackson 3 under the `tools.jackson.databind` package.
* Adding validation annotations, controllers, services, repositories, JPA entities, exception handlers, or tests during Task 3, because those belong to later scoped tasks.

## Manual Corrections and Adjustments

* Confirmed that the existing Debian OpenJDK 21 installation satisfies the project requirements; SDKMAN is optional.
* Confirmed that Docker Desktop integration with WSL can run the supplied PostgreSQL setup.
* Confirmed the untouched application starts successfully.
* Confirmed the baseline health endpoint returns:

```text
clarops sr engineer challenge
```

* Corrected the initial workflow suggestion to use a separate implementation branch; development will continue directly on `develop`.
* Clarified that optimistic locking protects updates to an existing trace but does not by itself prevent concurrent creation of the same `traceId`.
* Clarified that concurrent creation conflicts require explicit handling of the `trace_id` primary-key violation.
* Clarified that duplicate detection requires comparison of the logical request payload, including structured metadata, rather than only handling a database uniqueness violation.
* Replaced the proposed `completed` boolean with `completed_at` to preserve the completion timestamp and avoid redundant state.
* Added database constraints for paired expectation fields, positive TTL values, valid event results, positive event counts, and completed traces without pending expectations.
* Rejected the proposed standalone `payload_hash` as unnecessary for the MVP; duplicate requests will be compared using persisted logical fields.
* Reviewed the Codex requirement analysis and found no material contradiction with the documented assumptions or proposed MVP design.
* Reviewed the proposed API layering, transaction flow, status calculation, validation rules, and testing strategy and accepted them with the corrections above.
* Added database primary-key conflict handling for concurrent trace creation.
* Replaced payload-hash-based duplicate detection with comparison of persisted logical fields, including structured metadata.
* Clarified that completion is represented by `completed_at`.
* Clarified that an event is late when it arrives at or after the deadline.
* Added explicit post-completion Hurl coverage.
* Added JSON field-order independence to duplicate-comparison tests.
* Preserved Task 1 as complete and expanded the remaining work from ten broad tasks into twelve smaller implementation tasks.
* Codex implemented Task 2 only, modifying `docker/init-scripts/db/01-init-schema.sql` and not generating Java code, repositories, controllers, services, or tests.
* Reviewed Codex's Task 2 summary and confirmed that it followed the requested scope.
* Confirmed that the generated DDL contains separate current-state and event-history tables and does not add a mutable status column, completion boolean, or payload hash.
* Identified the partial index on pending expectation deadlines as requiring manual justification or removal because the current API does not scan traces by deadline.
* Accepted normalization of missing metadata to an empty JSON object only on the condition that the README and duplicate-comparison behavior document that absent metadata and `{}` are logically equivalent.
* Recorded that `git diff --check -- docker/init-scripts/db/01-init-schema.sql` passed.
* Clean database initialization and health-endpoint validation remain manual pending checks because Codex correctly declined to delete Docker volumes automatically.
* Codex implemented Task 3 only, creating the two domain enums and four API records requested by the task.
* Confirmed that API contracts remain separate from future persistence entities.
* The initial compile exposed an incorrect Jackson 2 package assumption: `com.fasterxml.jackson.databind.JsonNode`.
* Codex corrected the metadata type to `tools.jackson.databind.JsonNode`, which matches the Jackson 3 packages provided by Spring Boot 4.
* Reviewed `EventRequest` and confirmed that omitted `finalEvent` values are normalized to `false`.
* Reviewed `EventIngestionResponse`, `TraceStatusResponse`, and `ApiErrorResponse` and confirmed that their fields match the documented API design.
* Confirmed that no validation annotations, controllers, services, repositories, JPA entities, persistence logic, tests, or DDL changes were added during Task 3.
* Recorded that `git diff --check` passed.
* Recorded that `./mvnw -q -DskipTests compile` passed after the Jackson package correction.

## Task 2 Implementation Record

Codex generated the Task 2 DDL after receiving the clarified design prompt.

Generated changes:

* Added `trace_state` for current trace facts.
* Added `trace_event` for accepted event history.
* Added `completed_at`, expectation fields, latest-event facts, accepted-event count, audit timestamps, and optimistic-lock versioning.
* Stored metadata as PostgreSQL `JSONB`.
* Added primary keys, a foreign key, check constraints, and trace-history indexing.
* Did not add a mutable status column, a redundant completion boolean, a payload hash, or application code.

Manual review still required:

* Inspect the complete SQL diff.
* Confirm all paired-field and completion constraints express the intended invariants.
* Decide whether to retain or remove the partial deadline index.
* Confirm and document metadata normalization semantics.
* Reinitialize PostgreSQL from a clean Docker volume.
* Start the application and confirm `/api/health`.
* Mark Task 2 complete only after clean initialization succeeds.

## Task 3 Implementation Record

Codex generated the Task 3 domain enums and API contracts after receiving the scoped Task 3 prompt.

Files created:

* `src/main/java/com/clara/challenge/watchdog/domain/EventResult.java`
* `src/main/java/com/clara/challenge/watchdog/domain/TraceStatus.java`
* `src/main/java/com/clara/challenge/watchdog/api/EventRequest.java`
* `src/main/java/com/clara/challenge/watchdog/api/EventIngestionResponse.java`
* `src/main/java/com/clara/challenge/watchdog/api/TraceStatusResponse.java`
* `src/main/java/com/clara/challenge/watchdog/api/ApiErrorResponse.java`

Accepted implementation details:

* `EventResult` defines `SUCCESS` and `ERROR`.
* `TraceStatus` defines `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, and `COMPLETED`.
* API contracts are Java records.
* Timestamps use `Instant`.
* Metadata uses structured Jackson 3 `JsonNode`.
* `EventRequest` defaults omitted `finalEvent` to `false`.
* `EventIngestionResponse` represents both new and idempotent ingestion outcomes through its `duplicate` field.
* `TraceStatusResponse` includes the latest event facts, current expectation, deadline, accepted event count, and completion timestamp.
* `ApiErrorResponse` includes a code, message, and timestamp.

Manual correction:

* The first implementation used the Jackson 2 package `com.fasterxml.jackson.databind.JsonNode`, which did not compile in this Spring Boot 4 project.
* The import was corrected to `tools.jackson.databind.JsonNode`.
* Compilation then passed with `./mvnw -q -DskipTests compile`.

Scope review:

* No controllers, services, repositories, JPA entities, exception handlers, validation annotations, tests, persistence code, dependency changes, or DDL changes were added.
* Task 3 is complete after source review and successful compilation.

## Manual Review Responsibilities

All AI-assisted output will be reviewed for:

* Consistency with the documented assumptions.
* Correct transaction boundaries.
* Correct handling of duplicate and conflicting events.
* Structured comparison of duplicate payloads, including metadata.
* Correct handling of concurrent creation and update of trace state.
* Correct deadline and boundary calculations.
* Database constraints matching application rules.
* Deterministic time-dependent tests.
* HTTP status codes and response contracts.
* Hurl scenarios matching public behavior.
* Unnecessary abstractions or dependencies.
* Code that cannot be explained during the technical interview.

## Pending Updates

This document must be updated after implementation to include:

* Prompts used for later generated or revised Java implementation code.
* Results of the clean database initialization for the generated DDL.
* Prompts used for unit tests.
* Prompts used for Hurl tests.
* AI-generated code that was accepted.
* AI-generated code that was rejected.
* Bugs or inconsistencies found during manual review.
* Final corrections made before submission.

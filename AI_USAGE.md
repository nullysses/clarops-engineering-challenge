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

### Prompt 4 — Apply clarified design decisions and implement Task 2

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

### Prompt 6 — Implement Task 4: request validation and API error handling

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, Task 2 DDL, and Task 3 API contracts before editing.
>
> Implement only Task 4: request validation and API error handling.
>
> #### Required validation
>
> Add Bean Validation constraints to `EventRequest`:
>
> * `eventId` must be present and non-blank.
> * `traceId` must be present and non-blank.
> * `eventName` must be present and non-blank.
> * `result` must be present.
> * `occurredAt` must be present.
> * `nextExpectedEvent` and `nextEventTtlSeconds` must either both be present or both be absent.
> * When present, `nextExpectedEvent` must not be blank.
> * When present, `nextEventTtlSeconds` must be greater than zero.
> * `finalEvent = true` must not define another expected event.
> * When metadata is present, it must be a JSON object rather than an array or scalar.
>
> Invalid enum values, malformed timestamps, malformed JSON, and incompatible JSON types must produce `400 Bad Request`.
>
> Preserve the existing behavior where omitted `finalEvent` becomes `false`.
>
> #### Cross-field validation
>
> Prefer a small, explicit solution.
>
> Standard Jakarta Validation annotations should handle individual fields. Cross-field rules may use clearly named `@AssertTrue` methods on `EventRequest` unless a custom class-level constraint produces substantially clearer code.
>
> Do not put service or persistence behavior in validation code.
>
> #### Exceptions
>
> Define minimal exceptions that later tasks can reuse:
>
> * a not-found exception mapped to `404 Not Found`;
> * a conflict exception mapped to `409 Conflict`.
>
> Keep them under an appropriate watchdog domain or API exception package.
>
> Do not implement the business conditions that throw these exceptions yet.
>
> #### Global exception handling
>
> Add a `@RestControllerAdvice` that returns the existing `ApiErrorResponse`.
>
> Handle at least:
>
> * `MethodArgumentNotValidException` → `400 Bad Request`;
> * `ConstraintViolationException` → `400 Bad Request`;
> * `HttpMessageNotReadableException` → `400 Bad Request`;
> * the new not-found exception → `404 Not Found`;
> * the new conflict exception → `409 Conflict`.
>
> Use stable error codes such as:
>
> * `VALIDATION_ERROR`;
> * `INVALID_REQUEST`;
> * `NOT_FOUND`;
> * `CONFLICT`.
>
> For validation failures, return a concise, useful message. Because `ApiErrorResponse` currently contains one message rather than a list of field errors, choose one deterministic validation message rather than changing the response contract.
>
> Do not expose stack traces, internal exception class names, SQL details, or raw Jackson parser messages.
>
> #### Scope restrictions
>
> Do not add:
>
> * controllers;
> * services;
> * repositories;
> * JPA entities;
> * persistence logic;
> * status calculation;
> * event-ingestion logic;
> * tests;
> * Hurl files;
> * new dependencies;
> * DDL changes, unless a direct inconsistency is discovered.
>
> Continue using Jackson 3 types already resolved by the project, including `tools.jackson.databind.JsonNode`.
>
> #### Before editing
>
> Summarize:
>
> 1. the validation approach;
> 2. the exact files to create or modify;
> 3. the proposed exception types and error codes;
> 4. any ambiguity in how validation messages should be selected.
>
> Then implement only Task 4.
>
> #### After editing
>
> Report:
>
> 1. files created or modified;
> 2. validation rules implemented;
> 3. exception mappings implemented;
> 4. commands run;
> 5. compilation or formatting corrections made;
> 6. unresolved issues.
>
> Run at minimum:
>
> ```bash
> git diff --check
> ./mvnw -q -DskipTests compile
> ```

### Prompt 7 — Review and correct Task 4

> Review only the Task 4 changes.
>
> Check:
>
> * `EventRequest` validation annotations and cross-field rules;
> * deterministic selection of one validation message;
> * handling of both field-level and class-level validation errors;
> * malformed JSON, invalid enums, and invalid timestamps returning the generic `INVALID_REQUEST` message;
> * `WatchdogNotFoundException` mapping to `404`;
> * `WatchdogConflictException` mapping to `409`;
> * absence of catch-all `Exception` or `RuntimeException` handlers;
> * no stack traces, parser details, SQL details, or internal exception names exposed;
> * no out-of-scope files or dependencies.
>
> Make corrections only if a concrete defect is found.
>
> Then run:
>
> ```bash
> git diff --check
> ./mvnw -q -DskipTests compile
> ```
>
> Report findings, corrections, and remaining risks.

### Prompt 8 — Implement Task 5: persistence layer

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, Task 2 DDL, and existing Task 3–4 code before editing.
>
> Implement only Task 5: the JPA persistence layer.
>
> #### Required entities
>
> Create JPA entities matching the existing DDL exactly:
>
> * `TraceState`
> * `TraceEvent`
>
> Place them under:
>
> ```text
> com.clara.challenge.watchdog.persistence
> ```
>
> Do not rename database tables or columns unless a concrete DDL inconsistency is found.
>
> Both entities must use:
>
> ```text
> schema = "clarops_challenge_schema"
> ```
>
> #### TraceState requirements
>
> Map all columns currently defined in `trace_state`, including:
>
> * `traceId` as the natural primary key;
> * latest accepted event fields;
> * current expected event and deadline;
> * `completedAt`;
> * accepted event count;
> * creation and update timestamps;
> * optimistic-lock version.
>
> Map the DDL `version` column with `@Version`.
>
> Do not add a persisted mutable trace-status field or completion boolean.
>
> Do not implement status calculation or state-transition business logic yet.
>
> #### TraceEvent requirements
>
> Map all columns currently defined in `trace_event`, including:
>
> * `eventId` as the natural primary key;
> * `traceId`;
> * event name;
> * event result;
> * `occurredAt`;
> * service `receivedAt`;
> * optional next expected event;
> * optional TTL;
> * `finalEvent`;
> * metadata.
>
> Map `EventResult` as a string enum.
>
> Map metadata as PostgreSQL `JSONB` using the Hibernate/Jackson support already available in the project. Continue using Jackson 3 types such as:
>
> ```java
> tools.jackson.databind.JsonNode
> ```
>
> Do not add a new JSON library or persistence dependency.
>
> Preserve structured JSON semantics so object-field order does not affect later duplicate comparison.
>
> #### Entity design
>
> * Keep API records separate from JPA entities.
> * Avoid Lombok `@Data`.
> * Avoid public setters for every field unless JPA or the planned service requires them.
> * Provide a protected no-argument constructor for JPA.
> * Provide explicit constructors or factory methods sufficient for later service implementation.
> * Do not place HTTP behavior, validation responses, status calculation, or transaction orchestration inside entities.
> * Do not create bidirectional entity relationships unless they provide clear value.
>
> Prefer storing `traceId` directly on `TraceEvent` rather than introducing a mandatory object relationship that could complicate insertion ordering or serialization. The database foreign key remains authoritative.
>
> #### Required repositories
>
> Create:
>
> * `TraceStateRepository`
> * `TraceEventRepository`
>
> Use Spring Data JPA.
>
> The repositories must support:
>
> * lookup of a trace by `traceId`;
> * lookup of an event by `eventId`;
> * ordered event-history lookup by `traceId` only if it is directly useful and matches the existing index.
>
> Do not create custom SQL, locking queries, or retry behavior yet unless required for correct basic mapping.
>
> `JpaRepository.findById` is sufficient for natural-key lookup; do not add redundant repository methods solely to rename it.
>
> #### Consistency requirements
>
> Confirm that entity nullability, lengths, enum representation, timestamps, JSON mapping, and column names agree with the existing SQL.
>
> Do not depend on Hibernate schema generation. The project must continue using:
>
> ```yaml
> spring.jpa.hibernate.ddl-auto: none
> ```
>
> Do not modify the DDL unless a real mismatch is found. If one is found, report it before changing either side.
>
> #### Scope restrictions
>
> Do not add:
>
> * controllers;
> * ingestion services;
> * status services;
> * `Clock` configuration;
> * transaction orchestration;
> * duplicate-comparison business logic;
> * optimistic-lock retry handling;
> * exception translation;
> * unit tests;
> * Hurl files;
> * new dependencies.
>
> Do not mark Task 5 complete in `TASKS.md`; report the result for manual review first.
>
> #### Before editing
>
> Summarize:
>
> 1. the exact entity and repository files to create;
> 2. how each entity maps to the existing DDL;
> 3. the proposed JSONB mapping;
> 4. whether any DDL/entity inconsistency exists;
> 5. any persistence-design ambiguity.
>
> Then implement only Task 5.
>
> #### After editing
>
> Report:
>
> 1. files created or modified;
> 2. entity mappings and repository interfaces added;
> 3. JSONB and enum mapping decisions;
> 4. commands run;
> 5. compilation or formatting corrections;
> 6. unresolved issues or mappings that require manual review.
>
> Run at minimum:
>
> ```bash
> git diff --check
> ./mvnw -q -DskipTests compile
> ```

### Prompt 9 — Review and correct Task 5

> Review only the Task 5 persistence changes.
>
> Verify:
>
> * every entity column exactly matches the DDL name, length, nullability, and type;
> * `TraceState.version` uses `@Version`;
> * `TraceEvent.result` uses `EnumType.STRING`;
> * JSONB uses Jackson 3 `JsonNode` and Hibernate JSON mapping correctly;
> * no mutable status column or completion boolean exists;
> * no bidirectional JPA relationship was introduced;
> * entity constructors and mutation methods do not implement business transition rules;
> * protected no-argument constructors exist for JPA;
> * equality/hash-code methods do not include mutable entity state;
> * metadata cannot reach PostgreSQL as null, or the responsibility for normalization is explicitly deferred to Task 7;
> * repository methods are valid Spring Data derived queries;
> * no out-of-scope code or dependency was added.
>
> Make corrections only for concrete defects.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q -DskipTests compile
> ```
>
> Report findings, corrections, and remaining persistence risks.

### Prompt 10 — Implement Task 6: trace-status calculation

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, DDL, API contracts, validation code, and persistence entities before editing.
>
> Implement only Task 6: trace-status calculation and deterministic time configuration.
>
> Do not mark Task 6 complete in `TASKS.md`. The implementation will be reviewed now, but final completion will remain pending until the public API can be exercised manually after Tasks 7 and 8.
>
> #### Required configuration
>
> Add a Spring bean that provides:
>
> ```java
> Clock.systemUTC()
> ```
>
> Place it in a small configuration class under an appropriate configuration package.
>
> All time-dependent watchdog logic must receive a `Clock` through dependency injection rather than calling:
>
> ```java
> Instant.now()
> ```
>
> directly.
>
> Do not modify error-response timestamp handling unless it is directly necessary for Task 6.
>
> #### Required domain calculation
>
> Create a small Spring-independent component responsible for calculating `TraceStatus` from persisted trace facts and a supplied current time.
>
> Prefer a pure domain class such as:
>
> ```text
> TraceStatusCalculator
> ```
>
> under:
>
> ```text
> com.clara.challenge.watchdog.domain
> ```
>
> The calculator must not:
>
> * query repositories;
> * depend on Spring;
> * mutate `TraceState`;
> * persist anything;
> * contain HTTP behavior.
>
> It may accept either:
>
> * a `TraceState` plus an `Instant`; or
> * the individual persisted facts required for calculation.
>
> Prefer the option that keeps the domain component easy to unit-test without excessive argument lists or unnecessary DTOs.
>
> #### Status rules
>
> Apply these rules in this precedence order:
>
> 1. When `completedAt` is non-null, return `COMPLETED`.
>
> 2. When there is no current expected event and no deadline, return `STARTED`.
>
> 3. When an expectation exists and:
>
>    ```text
>    now < nextExpectedBefore
>    ```
>
>    return `WAITING_OTHER_EVENT`.
>
> 4. When an expectation exists and:
>
>    ```text
>    now >= nextExpectedBefore
>    ```
>
>    return `TTL_EXPIRED_FOR_EVENT`.
>
> The exact deadline boundary is therefore expired.
>
> Do not persist expiration or modify the trace when calculating status.
>
> #### Status query service
>
> Add the smallest service needed to retrieve and calculate the current status of a trace.
>
> The service should:
>
> * load `TraceState` using `TraceStateRepository.findById`;
> * throw `WatchdogNotFoundException` when the trace does not exist;
> * obtain the current time from the injected UTC `Clock`;
> * delegate status selection to `TraceStatusCalculator`;
> * produce the existing `TraceStatusResponse`.
>
> The response must include:
>
> * `traceId`;
> * calculated status;
> * latest event ID;
> * latest event name;
> * latest event result;
> * current expected event;
> * current expectation deadline;
> * accepted event count;
> * completion timestamp.
>
> Keep mapping explicit and small. Do not add MapStruct.
>
> A reasonable service name is:
>
> ```text
> TraceStatusService
> ```
>
> under:
>
> ```text
> com.clara.challenge.watchdog.service
> ```
>
> #### Unit tests for the calculator
>
> Although the larger unit-test task is Task 9, Task 6 includes tests directly required to verify this isolated status calculation.
>
> Add focused tests for `TraceStatusCalculator` covering:
>
> * completed traces return `COMPLETED`;
> * completed status takes precedence over a stale expectation;
> * active trace without an expectation returns `STARTED`;
> * expectation before its deadline returns `WAITING_OTHER_EVENT`;
> * expectation exactly at its deadline returns `TTL_EXPIRED_FOR_EVENT`;
> * expectation after its deadline returns `TTL_EXPIRED_FOR_EVENT`.
>
> Testing standard:
>
> * Use JUnit 5.
> * Use names in the form `shouldExpectedBehavior_WhenCondition`.
> * Follow Arrange / Act / Assert.
> * Test one business rule per test.
> * Use fixed `Instant` values.
> * Do not load Spring for the pure calculator tests.
>
> A small service unit test for unknown traces and response mapping is acceptable if it remains focused. Do not add database integration tests or controller tests yet.
>
> #### Invalid persisted-state handling
>
> The database constraints guarantee that expectation name and deadline are paired.
>
> The calculator should still fail explicitly rather than silently produce a misleading status if it receives an impossible in-memory state where only one expectation field is present.
>
> Use a clear `IllegalArgumentException` or `IllegalStateException`. Do not introduce a public HTTP error contract for corrupted persisted state.
>
> #### Scope restrictions
>
> Do not add:
>
> * event-ingestion logic;
> * event duplicate comparison;
> * trace-state transitions;
> * controllers;
> * `POST /events`;
> * `GET /traces/{traceId}/status`;
> * Hurl files;
> * database migrations;
> * DDL changes;
> * new dependencies;
> * scheduler or background expiration processing.
>
> Do not modify Task 5 persistence mappings unless a concrete defect blocks Task 6.
>
> #### Before editing
>
> Summarize:
>
> 1. the exact files to create or modify;
> 2. the calculator input and API;
> 3. the status-rule precedence;
> 4. how `Clock` will be configured and used;
> 5. how invalid persisted states will be handled;
> 6. the tests to add;
> 7. any ambiguity.
>
> Then implement only Task 6.
>
> #### After editing
>
> Report:
>
> 1. files created or modified;
> 2. status rules implemented;
> 3. `Clock` configuration and injection;
> 4. status response mapping;
> 5. tests added;
> 6. commands run;
> 7. corrections made;
> 8. unresolved risks.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
>

### Prompt 11 — Review and correct Task 6

> Review only the Task 6 implementation.
>
> Verify:
>
> * `TraceStatusCalculator` is Spring-independent and side-effect free;
> * expectation name and deadline pairing is validated before status precedence;
> * exactly one expectation field being present throws `IllegalStateException`, including when `completedAt` is non-null;
> * a completed trace with both expectation fields present still returns `COMPLETED`;
> * the exact deadline boundary returns `TTL_EXPIRED_FOR_EVENT`;
> * `TraceStatusService` uses `Instant.now(clock)` rather than system time directly;
> * missing traces throw `WatchdogNotFoundException`;
> * response mapping includes every field from `TraceStatusResponse`;
> * no controller, ingestion logic, persistence mutation, scheduler, or Hurl code was added.
>
> Add or adjust tests for:
>
> * completed trace with only `nextExpectedEvent` present;
> * completed trace with only `nextExpectedBefore` present;
> * completed trace with a fully paired stale expectation.
>
> Make corrections only for concrete defects.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
>
> Report findings, corrections, and remaining risks.

### Prompt 12 — Implement Task 7: transactional event ingestion

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, DDL, API contracts, validation rules, persistence entities, repositories, exceptions, `Clock` configuration, and Task 6 status logic before editing.
>
> Implement only Task 7: transactional event ingestion.
>
> Do not mark Task 7 complete in `TASKS.md`. It will remain pending until Task 8 exposes the public endpoints and the API is manually exercised.
>
> #### Exact file scope
>
> Create only:
>
> ```text
> src/main/java/com/clara/challenge/watchdog/service/EventIngestionService.java
> ```
>
> Keep it directly beside `TraceStatusService` under `com.clara.challenge.watchdog.service`.
>
> Use `TransactionTemplate` inside this service so each ingestion attempt has an explicit transaction boundary and race recovery occurs only after the failed transaction has rolled back.
>
> Do not create additional `application`, `usecase`, `command`, `handler`, `mapper`, `transaction`, or nested ingestion packages. Do not create a second transactional service unless a concrete framework limitation makes the single-service `TransactionTemplate` design impossible.
>
> You may modify `TraceState` only if a small mechanical persistence mutation is genuinely required. Do not move lifecycle rules into the entity.
>
> #### Public API
>
> Add:
>
> ```java
> EventIngestionResponse ingest(EventRequest request)
> ```
>
> Return:
>
> * `duplicate = false` for a newly accepted event;
> * `duplicate = true` for an exact idempotent duplicate.
>
> Do not add a controller yet.
>
> #### Transaction structure
>
> Inject the existing repositories, UTC `Clock`, and Spring transaction manager or `TransactionTemplate`.
>
> The public `ingest` method coordinates:
>
> 1. one normal transactional attempt;
> 2. post-rollback race inspection when a database integrity violation occurs;
> 3. at most one retry for concurrent trace creation.
>
> Each transactional attempt must capture exactly one timestamp using:
>
> ```java
> Instant receivedAt = Instant.now(clock);
> ```
>
> Reuse that timestamp within the attempt for event history, trace timestamps, completion, and deadline calculation. A retry is a new attempt and may capture a new timestamp because the original transaction did not accept the event.
>
> Do not catch a constraint exception and continue inside the same transaction. Avoid self-invocation-based `@Transactional` methods.
>
> #### Metadata normalization
>
> Normalize omitted metadata to an empty Jackson 3 object node before persistence and duplicate comparison.
>
> Therefore omitted metadata and `{}` are logically equivalent.
>
> Do not compare raw serialized JSON strings. `TraceEvent` must never receive null metadata.
>
> #### Duplicate handling
>
> In every transactional attempt, look up `TraceEvent` by `eventId` before reading or mutating `TraceState`.
>
> Compare the complete logical request payload:
>
> * `eventId`;
> * `traceId`;
> * `eventName`;
> * `result`;
> * `occurredAt`;
> * `nextExpectedEvent`;
> * `nextEventTtlSeconds`;
> * normalized `finalEvent`;
> * normalized structured metadata.
>
> Exclude server-generated `receivedAt`.
>
> Use `JsonNode.equals` so JSON object field order does not affect equality.
>
> Behavior:
>
> * exact duplicate → return `duplicate = true` without modifying state or history;
> * reused `eventId` with different logical content → throw `WatchdogConflictException`;
> * exact duplicates remain idempotent even after completion or expiration.
>
> Keep the comparison as a small private method in `EventIngestionService`; do not create a separate mapper or comparator class for this MVP.
>
> #### First event
>
> When no trace exists:
>
> * populate latest-event fields from the request;
> * set `eventsReceived = 1`;
> * set `createdAt` and `updatedAt` to `receivedAt`;
> * set `completedAt = receivedAt` for a final event;
> * otherwise calculate `nextExpectedBefore = receivedAt + TTL` when an expectation exists;
> * otherwise leave completion and expectation fields null.
>
> Save and flush `TraceState` before inserting `TraceEvent`, because `trace_event.trace_id` has a foreign key to `trace_state.trace_id`.
>
> Both writes must remain in the same transaction.
>
> #### Existing trace
>
> Before applying business rules, verify that `nextExpectedEvent` and `nextExpectedBefore` are either both present or both absent. Throw `IllegalStateException` for a half-paired persisted state.
>
> For a non-duplicate event, apply checks in this order:
>
> 1. completed trace → `WatchdogConflictException`;
> 2. active expectation with `receivedAt >= nextExpectedBefore` → late-event conflict;
> 3. active expectation whose name does not match `eventName` → unexpected-event conflict;
> 4. otherwise accept the event.
>
> Rejected events must not be persisted, increment counters, or modify state.
>
> For an accepted event:
>
> * update all latest-event facts;
> * increment `eventsReceived` by one;
> * set `updatedAt = receivedAt`;
> * final event → set `completedAt = receivedAt` and clear expectation fields;
> * next expectation → store its name and `receivedAt + TTL`, with `completedAt = null`;
> * no next expectation → clear expectation fields and leave `completedAt = null`.
>
> `result = ERROR` does not independently alter lifecycle behavior.
>
> Persist one `TraceEvent` for every newly accepted event. Do not persist duplicates or rejected events.
>
> Flush before returning success so optimistic-lock and uniqueness failures are observed inside the transaction.
>
> #### Concurrency recovery
>
> Existing trace updates rely on `@Version`. Translate `OptimisticLockingFailureException` to `WatchdogConflictException`.
>
> When a `DataIntegrityViolationException` escapes a rolled-back attempt:
>
> 1. start a new transaction and re-read `eventId`;
> 2. if it now exists, return an exact duplicate or throw a conflicting-duplicate exception;
> 3. otherwise re-read `traceId`;
> 4. if the trace now exists and no retry has been used, retry ingestion once in a new transaction;
> 5. otherwise rethrow the original persistence exception.
>
> Do not parse PostgreSQL error-message text. Do not convert unrelated integrity failures into `409 Conflict`. Do not use unbounded retries.
>
> #### Scope restrictions
>
> Do not add:
>
> * controllers or endpoint mappings;
> * status-endpoint changes;
> * Hurl files;
> * schedulers or background jobs;
> * new dependencies;
> * DDL changes;
> * persistence integration tests;
> * broad Task 9 unit-test coverage;
> * a stored mutable trace-status field.
>
> Do not modify Task 6 unless a concrete blocking defect is discovered.
>
> #### Before editing
>
> Summarize:
>
> 1. the exact file to create and any existing file that truly requires modification;
> 2. the `TransactionTemplate` flow;
> 3. duplicate comparison;
> 4. first-event write order;
> 5. existing-trace transition order;
> 6. event-ID race recovery;
> 7. trace-ID race recovery;
> 8. optimistic-lock handling;
> 9. unresolved ambiguity.
>
> Then implement only Task 7.
>
> #### After editing
>
> Report:
>
> 1. files created or modified;
> 2. transaction boundaries;
> 3. duplicate comparison and metadata normalization;
> 4. first-event behavior;
> 5. existing-trace transitions;
> 6. concurrency recovery;
> 7. commands run;
> 8. corrections made;
> 9. unresolved risks.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
### Prompt 13 — Review and correct Task 7

> Review only the Task 7 implementation in:
>
> ```text
> src/main/java/com/clara/challenge/watchdog/service/EventIngestionService.java
> ```
>
> Do not add controllers, Hurl files, DDL changes, broad unit tests, or new dependencies.
>
> #### Transaction review
>
> Verify that:
>
> * one `receivedAt` value is captured per public `ingest` call;
> * the same value is reused across the single bounded retry;
> * every ingestion attempt executes inside a new `TransactionTemplate` transaction;
> * database exceptions are handled only after the failed transaction has rolled back;
> * no repository read is used to recover from an exception while still inside the failed transaction.
>
> #### Duplicate handling
>
> Verify that duplicate lookup occurs before trace-state inspection or mutation.
>
> Exact duplicate comparison must include:
>
> * `eventId`;
> * `traceId`;
> * `eventName`;
> * `result`;
> * `occurredAt`;
> * `nextExpectedEvent`;
> * `nextEventTtlSeconds`;
> * normalized `finalEvent`;
> * normalized structured metadata.
>
> It must exclude `receivedAt`.
>
> Confirm that omitted metadata and `{}` are equivalent and that structured `JsonNode.equals` is used.
>
> #### Optimistic-lock race correction
>
> Do not immediately translate every optimistic-lock failure to `WatchdogConflictException`.
>
> After an optimistic-lock transaction rolls back:
>
> 1. re-read the request’s `eventId` in a new transaction;
>
> 2. if the event now exists and the logical payload matches, return:
>
>    ```java
>    new EventIngestionResponse(request.eventId(), request.traceId(), true)
>    ```
>
> 3. if the event exists with different logical content, throw `WatchdogConflictException`;
>
> 4. only when the event remains absent should the optimistic-lock failure become a concurrency conflict.
>
> This is required because two identical concurrent requests can race on the same trace version, with one committing and the other failing optimistic locking before observing the committed `eventId`.
>
> #### Integrity-violation recovery
>
> Verify that after `DataIntegrityViolationException` rollback:
>
> 1. `eventId` is re-read first and resolved as exact or conflicting duplicate when present;
> 2. a trace-creation retry occurs only if:
>
>    * the failed attempt was creating a new trace;
>    * the event is still absent;
>    * the trace now exists;
>    * no retry has already occurred;
> 3. unrelated or unexplained integrity violations propagate instead of being converted to `409`.
>
> Do not parse PostgreSQL error-message text.
>
> #### State-transition review
>
> Verify this order for existing traces:
>
> 1. validate paired persisted expectation fields;
> 2. reject completed traces;
> 3. when waiting, reject `receivedAt >= nextExpectedBefore`;
> 4. then reject an unexpected event name;
> 5. only after acceptance, update trace facts and persist event history.
>
> Rejected events must not mutate state, increment counters, or create history.
>
> Confirm:
>
> * first-event state is flushed before history insertion because of the foreign key;
> * final events clear expectation fields;
> * new expectations use `receivedAt + TTL`;
> * events without a new expectation clear prior expectation fields;
> * `result = ERROR` has no independent lifecycle effect;
> * explicit flush occurs before successful return so optimistic-lock failures are observable.
>
> #### Scope and maintainability
>
> Check that:
>
> * no controller or HTTP response-status logic exists;
> * no persisted mutable status field was introduced;
> * no unbounded retry exists;
> * helper methods have narrow, descriptive responsibilities;
> * the single service has not duplicated transition logic unnecessarily;
> * unexpected persistence failures are not hidden as business conflicts.
>
> Make corrections only for concrete defects.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
>
> Report:
>
> 1. findings;
> 2. corrections;
> 3. exact optimistic-lock recovery behavior;
> 4. exact integrity-violation recovery behavior;
> 5. remaining risks.

Prompt 13 superseded two Task 7 instructions from Prompt 12:

* One `receivedAt` value is captured per public `ingest` call and reused across the single bounded retry.
* An optimistic-lock failure is inspected for a concurrently persisted `eventId` before it is translated into a trace-concurrency conflict.

The implementation and Task 7 record reflect the corrected Prompt 13 behavior.

### Prompt 14 — Implement Task 8: HTTP endpoints

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, API contracts, validation rules, exception handling, `EventIngestionService`, and `TraceStatusService` before editing.
>
> Implement only Task 8: expose the watchdog services through HTTP.
>
> Do not mark Tasks 6, 7, or 8 complete in `TASKS.md`. They will remain pending until the endpoints are exercised manually against PostgreSQL.
>
> #### Package and file structure
>
> Keep controllers with the existing API layer:
>
> ```text
> com.clara.challenge.watchdog.api
> ```
>
> Create:
>
> ```text
> src/main/java/com/clara/challenge/watchdog/api/EventController.java
> src/main/java/com/clara/challenge/watchdog/api/TraceStatusController.java
> ```
>
> Do not create another service, mapper, facade, or orchestration layer.
>
> #### Context path
>
> The application already defines the servlet context path:
>
> ```text
> /api
> ```
>
> Controller mappings must therefore be:
>
> ```text
> /events
> /traces/{traceId}/status
> ```
>
> Do not include `/api` in controller annotations, because that would expose incorrect `/api/api/...` paths.
>
> The resulting public endpoints must be:
>
> ```text
> POST /api/events
> GET /api/traces/{traceId}/status
> ```
>
> #### Event endpoint
>
> Add:
>
> ```text
> POST /events
> ```
>
> Requirements:
>
> * Accept the existing `EventRequest`.
> * Apply Jakarta Bean Validation using `@Valid`.
> * Delegate directly to `EventIngestionService.ingest`.
> * Return the existing `EventIngestionResponse`.
>
> HTTP status:
>
> * newly accepted event with `duplicate = false` → `201 Created`;
> * exact idempotent duplicate with `duplicate = true` → `200 OK`.
>
> Do not infer duplicate status in the controller. Use the `duplicate` value returned by the service.
>
> Do not add a `Location` header because the API does not define an event-resource retrieval endpoint.
>
> #### Trace-status endpoint
>
> Add:
>
> ```text
> GET /traces/{traceId}/status
> ```
>
> Requirements:
>
> * Accept `traceId` as a path variable.
> * Delegate directly to `TraceStatusService.getStatus`.
> * Return the existing `TraceStatusResponse`.
> * Return `200 OK` for an existing trace.
>
> `TraceStatusService` already throws `WatchdogNotFoundException` for unknown traces. Let the existing `ApiExceptionHandler` translate it to `404 Not Found`.
>
> #### Error handling
>
> Controllers must not catch or translate domain exceptions.
>
> Continue relying on the existing global exception handler for:
>
> * request validation → `400 VALIDATION_ERROR`;
> * malformed JSON, invalid enums, and invalid timestamps → `400 INVALID_REQUEST`;
> * missing traces → `404 NOT_FOUND`;
> * duplicate or lifecycle conflicts → `409 CONFLICT`.
>
> Do not expose parser details, stack traces, database errors, or internal exception names.
>
> Modify `ApiExceptionHandler` only if a concrete defect prevents these endpoints from returning the documented error contract.
>
> #### Controller responsibilities
>
> Controllers should contain only:
>
> * route definitions;
> * request binding and validation;
> * service delegation;
> * HTTP success-status selection.
>
> Do not place in controllers:
>
> * duplicate comparison;
> * metadata normalization;
> * transaction handling;
> * status calculation;
> * trace-state mutation;
> * deadline calculation;
> * concurrency recovery;
> * repository access.
>
> Use constructor injection.
>
> #### Scope restrictions
>
> Do not add:
>
> * new services;
> * repository methods;
> * JPA entity changes;
> * DDL changes;
> * Hurl files;
> * broad Task 9 tests;
> * schedulers or background jobs;
> * new dependencies;
> * persisted status fields.
>
> Do not change Task 6 or Task 7 business logic unless a concrete blocking defect is discovered.
>
> #### Manual API verification preparation
>
> After implementing the controllers, provide a concise manual verification sequence using `curl`.
>
> Do not execute destructive database-reset commands automatically.
>
> The manual sequence must cover:
>
> 1. health endpoint;
> 2. first event accepted with `201`;
> 3. status reported as `STARTED`;
> 4. exact duplicate accepted with `200` and `duplicate = true`;
> 5. duplicate metadata with JSON properties in a different order still accepted as exact duplicate;
> 6. conflicting reuse of the same `eventId` returning `409`;
> 7. event defining a next expectation;
> 8. status reported as `WAITING_OTHER_EVENT`;
> 9. expected final event accepted;
> 10. status reported as `COMPLETED`;
> 11. new event after completion returning `409`;
> 12. unknown trace returning `404`;
> 13. invalid request returning `400`.
>
> Use unique, internally consistent event and trace IDs in the commands.
>
> Use a sufficiently long TTL for the waiting-flow manual test so it does not expire while commands are being copied and run.
>
> State the expected HTTP status and important response fields after each command.
>
> Do not claim that the API was verified merely because the commands were generated. Manual verification remains pending until the user runs them.
>
> #### Before editing
>
> Summarize:
>
> 1. the exact files to create or modify;
> 2. controller package placement;
> 3. public and annotation-level route mappings;
> 4. event success-status selection;
> 5. exception-flow behavior;
> 6. manual verification scenarios;
> 7. any ambiguity.
>
> Then implement only Task 8.
>
> #### After editing
>
> Report:
>
> 1. files created or modified;
> 2. route mappings;
> 3. validation and service delegation;
> 4. HTTP success-status behavior;
> 5. whether any existing exception handling required correction;
> 6. commands run;
> 7. compilation or formatting corrections;
> 8. unresolved risks;
> 9. the complete manual `curl` verification sequence.
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```

### Prompt 15 — Fix Hibernate JSONB mapping with Jackson 3

> Review the runtime exception from the first manual `POST /api/events` request.
>
> The confirmed root cause is:
>
> ```text
> Could not find a FormatMapper for the JSON format
> ```
>
> The application uses:
>
> * Spring Boot 4.0.2;
> * Hibernate ORM 7.2.1;
> * Jackson 3 types under `tools.jackson.*`;
> * `TraceEvent.metadata` mapped as JSON using:
>
>   ```java
>   @JdbcTypeCode(SqlTypes.JSON)
>   private JsonNode metadata;
>   ```
>
> Hibernate 7.2’s built-in Jackson integration expects Jackson 2 classes under `com.fasterxml.jackson.*`, so it does not auto-detect the Jackson 3 runtime used by Spring Boot 4.
>
> Implement only the smallest correction required for Hibernate JSON persistence with Jackson 3.
>
> #### Required implementation
>
> Create:
>
> ```text
> src/main/java/com/clara/challenge/watchdog/config/Jackson3JsonFormatMapper.java
> ```
>
> Package:
>
> ```java
> com.clara.challenge.watchdog.config
> ```
>
> Implement a Hibernate JSON `FormatMapper` backed by:
>
> ```java
> tools.jackson.databind.json.JsonMapper
> ```
>
> Prefer extending:
>
> ```java
> org.hibernate.type.format.AbstractJsonFormatMapper
> ```
>
> Implement the required operations:
>
> * serialize a value to a JSON string;
> * deserialize a JSON string to the requested Java type;
> * write to a Jackson 3 `JsonGenerator`;
> * read from a Jackson 3 `JsonParser`;
> * declare support for the Jackson 3 parser and generator source/target types.
>
> Use Hibernate’s provided `JavaType.getJavaType()` when constructing the corresponding Jackson type.
>
> A no-argument constructor must be available because Hibernate will instantiate the mapper from configuration.
>
> The no-argument constructor may use:
>
> ```java
> JsonMapper.builder().build()
> ```
>
> This application currently persists only structured `JsonNode` metadata, so no application-specific Jackson modules are required.
>
> #### Hibernate registration
>
> Register the custom mapper in the existing application configuration using:
>
> ```yaml
> spring:
>   jpa:
>     properties:
>       hibernate:
>         type:
>           json_format_mapper: com.clara.challenge.watchdog.config.Jackson3JsonFormatMapper
> ```
>
> Preserve all existing JPA properties and indentation.
>
> Do not replace the complete `spring.jpa` configuration block accidentally.
>
> #### Restrictions
>
> Do not:
>
> * add Jackson 2 dependencies;
> * add JSON-B or Yasson as an alternative serializer;
> * override the Spring Boot-managed Hibernate version;
> * change `TraceEvent.metadata` from structured Jackson 3 `JsonNode`;
> * store metadata as a raw JSON string;
> * remove `@JdbcTypeCode(SqlTypes.JSON)`;
> * alter the DDL;
> * alter event-ingestion business behavior;
> * add controllers or Hurl files;
> * mark Tasks 6–8 complete yet.
>
> #### Regression review
>
> Confirm that:
>
> * omitted metadata remains normalized to `{}`;
> * object property order remains irrelevant through `JsonNode.equals`;
> * PostgreSQL still receives a JSONB object;
> * no Jackson 2 classes appear in source or dependencies;
> * the custom mapper is selected by Hibernate at runtime.
>
> The stack trace also shows Spring Data using `merge` for the assigned `TraceEvent` identifier. Do not redesign entity newness or repository persistence in this correction unless the JSON mapper fix exposes a separate concrete failure.
>
> #### Before editing
>
> Report:
>
> 1. the exact files to create or modify;
> 2. the confirmed compatibility mismatch;
> 3. the mapper implementation strategy;
> 4. how Hibernate will discover it;
> 5. any ambiguity.
>
> Then implement only this runtime correction.
>
> #### After editing
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
>
> Report:
>
> 1. files created or modified;
> 2. mapper methods implemented;
> 3. Hibernate property added;
> 4. commands run;
> 5. unresolved risks.
>
> Do not claim the runtime defect is fixed until the application is restarted and the manual event request succeeds.

### Prompt 16 — Implement Task 9: focused unit tests

> Review the current `README.md`, `TASKS.md`, `AI_USAGE.md`, API contracts, persistence entities, repositories, `TraceStatusCalculator`, `TraceStatusService`, and `EventIngestionService` before editing.
>
> Implement only Task 9: focused unit tests for the watchdog domain and service behavior.
>
> Do not mark Task 9 complete in `TASKS.md`. The tests will be reviewed before the task is closed.
>
> #### Existing coverage
>
> `TraceStatusCalculatorTest` already covers:
>
> * completed status;
> * completion precedence over a paired stale expectation;
> * active trace without an expectation;
> * waiting before the deadline;
> * expiration exactly at the deadline;
> * expiration after the deadline;
> * half-paired expectation states.
>
> Do not duplicate those tests unless a concrete coverage defect is found.
>
> The primary target for Task 9 is:
>
> ```text
> src/main/java/com/clara/challenge/watchdog/service/EventIngestionService.java
> ```
>
> Create:
>
> ```text
> src/test/java/com/clara/challenge/watchdog/service/EventIngestionServiceTest.java
> ```
>
> A small `TraceStatusServiceTest` may also be added if it provides focused coverage for repository lookup and response mapping.
>
> #### Testing approach
>
> Use:
>
> * JUnit 5;
> * Mockito;
> * a fixed or explicitly controlled `Clock`;
> * mocked repositories;
> * a mocked `PlatformTransactionManager`;
> * real `TraceState` and `TraceEvent` instances when inspecting state transitions is clearer than mocking entities;
> * `ArgumentCaptor` where useful for inspecting persisted state and event history.
>
> Do not load a Spring application context for service unit tests.
>
> Do not use:
>
> * Testcontainers;
> * H2;
> * PostgreSQL integration tests;
> * `@SpringBootTest`;
> * `@DataJpaTest`;
> * MockMvc;
> * controller tests;
> * Hurl;
> * sleeps or real-time waiting;
> * reflection to invoke private methods;
> * new production dependencies.
>
> The existing `spring-boot-starter-test` dependency already supplies the required test libraries.
>
> #### Transaction test setup
>
> `EventIngestionService` creates its own `TransactionTemplate` from the injected `PlatformTransactionManager`.
>
> Configure the mocked transaction manager so `TransactionTemplate` can execute normally:
>
> * `getTransaction(...)` returns a usable `TransactionStatus`, such as `SimpleTransactionStatus`;
> * commit and rollback operations remain observable no-ops.
>
> Do not bypass `TransactionTemplate` or invoke private methods directly.
>
> Tests should exercise behavior through:
>
> ```java
> EventIngestionResponse ingest(EventRequest request)
> ```
>
> #### Required normal-ingestion scenarios
>
> Add focused tests covering:
>
> 1. A first event creates and persists `TraceState` and `TraceEvent`.
>
> 2. A first non-final event without another expectation:
>
>    * returns `duplicate = false`;
>    * sets `eventsReceived = 1`;
>    * leaves `completedAt` null;
>    * leaves expectation fields null;
>    * uses the server acceptance time for created, updated, and received timestamps.
>
> 3. A first event defining another expectation:
>
>    * stores the expected event name;
>    * calculates the deadline from `receivedAt + nextEventTtlSeconds`;
>    * does not calculate the deadline from client-provided `occurredAt`.
>
> 4. A first final event:
>
>    * sets `completedAt` to `receivedAt`;
>    * clears expectation fields;
>    * persists final-event history.
>
> 5. An accepted expected event on an existing trace:
>
>    * updates all latest-event fields;
>    * increments `eventsReceived`;
>    * persists one history event;
>    * applies the new expectation, completion, or no-expectation outcome from the new request.
>
> 6. An event with `result = ERROR` follows the same lifecycle rules as a successful event.
>
> #### Required duplicate scenarios
>
> Add focused tests covering:
>
> 1. An exact duplicate:
>
>    * returns `duplicate = true`;
>    * does not read or mutate trace state;
>    * does not save or flush new history.
>
> 2. Reusing an `eventId` with changed logical content throws `WatchdogConflictException`.
>
> 3. Omitted metadata and `{}` are logically equivalent.
>
> 4. JSON object field order does not affect duplicate equality.
>
> 5. `receivedAt` is excluded from duplicate comparison.
>
> 6. Exact duplicates remain idempotent when the associated trace is completed or expired, because event lookup occurs before lifecycle checks.
>
> #### Required rejection scenarios
>
> Add focused tests covering:
>
> 1. An unexpected event name throws `WatchdogConflictException`.
>
> 2. An expected event arriving exactly at the deadline is rejected.
>
> 3. An expected event arriving after the deadline is rejected.
>
> 4. A new non-duplicate event after completion is rejected.
>
> 5. A half-paired persisted expectation throws `IllegalStateException`.
>
> For each rejected event, verify that:
>
> * no new event history is saved;
> * the accepted-event counter is unchanged;
> * latest-event and expectation fields remain unchanged;
> * no successful flush is reported.
>
> #### Required optimistic-lock recovery scenarios
>
> Exercise the public `ingest` method and simulate an optimistic-lock failure from the transactional attempt.
>
> Cover:
>
> 1. After rollback, a matching event now exists:
>
>    * return an idempotent duplicate response.
>
> 2. After rollback, the same `eventId` exists with different logical content:
>
>    * throw `WatchdogConflictException`.
>
> 3. After rollback, the event remains absent:
>
>    * throw the trace-concurrency `WatchdogConflictException`;
>    * do not report the event as accepted.
>
> Verify that recovery reads occur only after the failed transaction has rolled back.
>
> #### Required integrity-violation recovery scenarios
>
> Simulate `DataIntegrityViolationException` from a failed transactional attempt.
>
> Cover:
>
> 1. A matching event now exists after rollback:
>
>    * return an idempotent duplicate response.
>
> 2. A conflicting event now exists after rollback:
>
>    * throw `WatchdogConflictException`.
>
> 3. The failed attempt was creating a trace, the event remains absent, and the trace now exists:
>
>    * retry ingestion exactly once;
>    * reuse the same public-call `receivedAt`;
>    * accept the event when the retry succeeds.
>
> 4. The retry encounters an optimistic-lock failure and a matching event is then visible:
>
>    * return an idempotent duplicate response.
>
> 5. The retry encounters another unexplained integrity violation and no event exists:
>
>    * propagate that integrity violation;
>    * do not convert it into a business conflict.
>
> 6. An integrity violation unrelated to concurrent trace creation:
>
>    * propagates unchanged;
>    * does not trigger an ingestion retry.
>
> Do not test PostgreSQL exception-message parsing because the implementation intentionally does not parse database messages.
>
> #### Timestamp requirements
>
> Verify that one acceptance timestamp is captured per public `ingest` call and reused for:
>
> * trace creation;
> * trace update;
> * event history;
> * deadline calculation;
> * completion;
> * the bounded trace-creation retry.
>
> A controlled or mocked `Clock` may be used to verify that the clock is read once per public call.
>
> #### Optional TraceStatusService tests
>
> If added, keep them limited to:
>
> * unknown trace throws `WatchdogNotFoundException`;
> * an existing trace is mapped completely into `TraceStatusResponse`;
> * the service supplies `Instant.now(clock)` to the calculator.
>
> Do not duplicate the calculator’s status-rule matrix.
>
> #### Test quality requirements
>
> * Follow the naming form `shouldExpectedBehavior_WhenCondition`.
> * Use Arrange / Act / Assert.
> * Test one business rule per test.
> * Avoid broad tests with many unrelated assertions.
> * Prefer exact interaction verification for rejected and duplicate paths.
> * Avoid testing private implementation details unless required to prove transaction recovery occurs after rollback.
> * Use helper methods only to remove repetitive fixture construction.
> * Keep fixture names and timestamps explicit.
> * Do not weaken production visibility solely for testing.
>
> #### Scope restrictions
>
> Do not modify:
>
> * controllers;
> * API contracts;
> * DDL;
> * application configuration;
> * repositories;
> * JPA mappings;
> * Hurl files;
> * `README.md`;
> * `TASKS.md`;
> * `AI_USAGE.md`.
>
> Do not change production code unless a concrete correctness or testability defect prevents valid unit testing. Report such a defect before making the change.
>
> #### Before editing
>
> Report:
>
> 1. the exact test files to create or modify;
> 2. the transaction-manager test strategy;
> 3. the normal-ingestion scenarios;
> 4. the duplicate and rejection scenarios;
> 5. the optimistic-lock recovery scenarios;
> 6. the integrity-violation recovery scenarios;
> 7. how timestamp reuse will be verified;
> 8. any ambiguity or production-code testability concern.
>
> Then implement only Task 9.
>
> #### After editing
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q test
> ```
>
> Report:
>
> 1. files created or modified;
> 2. tests added, grouped by behavior;
> 3. transaction and rollback-recovery coverage;
> 4. timestamp verification;
> 5. commands run;
> 6. test count and results;
> 7. any production defect discovered;
> 8. remaining untested risk.
>
> Do not mark Task 9 complete yet.

### Prompt 17 — Review and correct Task 9 unit tests

> Review only the Task 9 changes in:
>
> ```text
> src/test/java/com/clara/challenge/watchdog/service/EventIngestionServiceTest.java
> ```
>
> Do not add integration tests, controller tests, Hurl files, dependencies, or production changes unless a concrete production defect is exposed.
>
> Do not mark Task 9 complete yet.
>
> #### General test-quality review
>
> Verify that:
>
> * all tests exercise behavior through `EventIngestionService.ingest`;
> * no private method is invoked through reflection;
> * no Spring application context is loaded;
> * no database, H2, Testcontainers, sleep, or real-time waiting is used;
> * tests follow `shouldExpectedBehavior_WhenCondition`;
> * Arrange / Act / Assert is clear;
> * each test covers one primary business rule;
> * helpers remove repetition without hiding the scenario;
> * assertions test externally meaningful state and interactions rather than incidental implementation details;
> * Mockito interaction verification is not unnecessarily brittle.
>
> Remove or simplify duplicate tests only when they genuinely prove the same behavior through the same path.
>
> #### Transaction manager setup
>
> Verify that the mocked `PlatformTransactionManager` is configured correctly:
>
> * every `getTransaction(...)` call returns a fresh `SimpleTransactionStatus`;
> * `commit(...)` and `rollback(...)` are observable no-ops;
> * exceptions are thrown from repository persistence or flush operations inside the transaction callback, not from `getTransaction(...)`;
> * tests do not bypass the real `TransactionTemplate`;
> * normal and exact-duplicate transactions commit;
> * failed transactions roll back before any recovery read occurs.
>
> For rollback-order tests, confirm the exact relevant sequence using `InOrder` or an equivalent assertion:
>
> ```text
> initial transaction begins
> initial repository operation fails
> rollback occurs
> recovery transaction begins
> recovery repository read occurs
> ```
>
> Do not require irrelevant interactions between those operations.
>
> #### Normal-ingestion review
>
> Verify that tests correctly account for the actual first-event write behavior:
>
> 1. `createTraceState` calls `TraceStateRepository.saveAndFlush`;
> 2. event history is saved;
> 3. `flushAcceptedEvent` calls `TraceStateRepository.saveAndFlush` again;
> 4. event history is flushed.
>
> Do not incorrectly assert that first-event state is saved only once.
>
> Confirm separate focused tests exist for:
>
> * first event without expectation;
> * first event defining an expectation;
> * first final event;
> * existing expected event defining another expectation;
> * existing expected final event;
> * existing expected event clearing the previous expectation without completing;
> * `result = ERROR` following normal lifecycle behavior.
>
> Verify that tests inspect:
>
> * latest-event fields;
> * accepted-event count;
> * created and updated timestamps;
> * received timestamp;
> * completion timestamp;
> * expectation fields;
> * deadline calculation from server `receivedAt`, not client `occurredAt`;
> * persisted event-history fields.
>
> #### Duplicate review
>
> Verify that exact duplicate tests prove:
>
> * lookup by `eventId` occurs before trace lookup;
> * `duplicate = true` is returned;
> * no trace lookup occurs;
> * no trace mutation occurs;
> * no event is saved;
> * no successful persistence flush occurs;
> * the transaction may still commit normally.
>
> Verify distinct coverage for:
>
> * changed logical content;
> * omitted metadata versus `{}`;
> * reordered JSON object properties;
> * different persisted `receivedAt`;
> * completed trace;
> * expired trace.
>
> Ensure the completed and expired duplicate tests do not accidentally depend on lifecycle inspection; verify that trace state is never read.
>
> #### Business-rejection review
>
> Verify focused tests for:
>
> * unexpected event name;
> * event exactly at the deadline;
> * event after the deadline;
> * event after completion;
> * expected-event name without deadline;
> * deadline without expected-event name.
>
> For these paths, verify:
>
> * no history event is saved;
> * no successful flush occurs;
> * counters and latest-event facts are unchanged;
> * expectation fields are unchanged.
>
> State immutability assertions are appropriate for these business rejections because rejection occurs before mutation.
>
> Do not assert in-memory entity reversion after an optimistic-lock failure; mocked repositories do not reproduce persistence-context rollback semantics.
>
> #### Optimistic-lock recovery review
>
> Verify that optimistic-lock failures are raised from an operation inside the initial transaction.
>
> Confirm coverage for:
>
> 1. matching event visible after rollback:
>
>    * returns an exact duplicate;
>
> 2. conflicting event visible after rollback:
>
>    * throws `WatchdogConflictException`;
>
> 3. event absent after rollback:
>
>    * throws the trace-concurrency `WatchdogConflictException`.
>
> Verify:
>
> * rollback precedes the recovery event lookup;
> * the recovery lookup occurs in a new transaction;
> * a matching event may have a different `receivedAt`;
> * absent-event recovery does not report a successful ingestion;
> * tests do not expect the mutated Java fixture to be reverted after rollback.
>
> #### Integrity-violation recovery review
>
> Review sequential Mockito stubbing carefully.
>
> ### Concurrent event insertion
>
> Verify:
>
> * initial event lookup is absent;
> * the transactional persistence operation throws `DataIntegrityViolationException`;
> * rollback occurs;
> * recovery event lookup returns matching or conflicting history;
> * matching content returns duplicate;
> * conflicting content throws conflict.
>
> ### Concurrent trace creation retry
>
> Verify the sequence:
>
> ```text
> initial event lookup: absent
> initial trace lookup: absent
> trace creation attempted
> persistence operation: integrity violation
> rollback
> recovery event lookup: absent
> recovery trace-exists lookup: true
> retry event lookup: absent
> retry trace lookup: existing trace
> retry succeeds
> ```
>
> Confirm:
>
> * retry occurs exactly once;
> * the same public-call `receivedAt` is reused;
> * the clock is not read again;
> * the retry updates the existing trace rather than attempting another new trace creation.
>
> ### Retry optimistic-lock recovery
>
> Verify:
>
> ```text
> initial trace-creation attempt fails with integrity violation
> recovery determines the trace now exists
> retry fails with optimistic locking
> retry transaction rolls back
> matching event becomes visible
> duplicate response is returned
> ```
>
> Ensure each repeated `findById` call has the intended sequential result.
>
> ### Retry integrity propagation
>
> Verify that:
>
> * the retry’s second unexplained `DataIntegrityViolationException` is the exception propagated;
> * the service performs its final event lookup;
> * no matching event exists;
> * the exception is not translated into `WatchdogConflictException`;
> * no third ingestion attempt occurs.
>
> ### Unrelated integrity failure
>
> Verify that:
>
> * recovery still performs the required post-rollback event lookup;
> * the failed attempt did not attempt trace creation;
> * `TraceStateRepository.existsById` is not invoked;
> * no retry occurs;
> * the original integrity exception is propagated unchanged.
>
> #### Timestamp review
>
> Verify that timestamp-sensitive tests use a controlled `Clock`.
>
> Confirm one clock read per public `ingest` call and reuse of that value for:
>
> * first-trace timestamps;
> * existing-trace update timestamp;
> * event history `receivedAt`;
> * deadline calculation;
> * completion;
> * trace-creation retry.
>
> Avoid asserting a specific internal Clock method unless that is necessary. The behavioral requirement is one acceptance instant per public call.
>
> #### Assertion and fixture review
>
> Check that:
>
> * captured `TraceEvent` and `TraceState` objects contain complete expected values;
> * JSON fixtures are not shared mutably between tests;
> * the service’s metadata deep copy does not make tests pass accidentally through object identity;
> * exception assertions verify the correct type and, where stable, meaningful message;
> * captors do not accidentally capture both first and second `saveAndFlush` invocations as though only one exists;
> * repository mocks are reset naturally per test rather than manually carrying state;
> * Mockito strictness does not hide unused or incorrectly ordered stubbing.
>
> #### Coverage claims
>
> The tests simulate concurrency recovery branches; they do not execute true concurrent database transactions.
>
> Ensure the report describes them as:
>
> ```text
> unit coverage of concurrency-recovery behavior
> ```
>
> rather than claiming true concurrent integration coverage.
>
> #### Scope
>
> Make corrections only for concrete defects.
>
> Do not modify:
>
> * production code;
> * `TASKS.md`;
> * `AI_USAGE.md`;
> * controllers;
> * DDL;
> * configuration;
> * dependencies;
> * existing status-calculator tests.
>
> If a production defect is discovered, stop and report it before changing production code.
>
> #### Validation
>
> Run:
>
> ```bash
> git diff --check
> ./mvnw -q -Dtest=EventIngestionServiceTest test
> ./mvnw -q test
> ```
>
> Report:
>
> 1. findings;
> 2. corrections made;
> 3. tests removed, split, or added;
> 4. transaction-order verification;
> 5. retry-sequence verification;
> 6. timestamp verification;
> 7. final test counts;
> 8. any remaining risk.

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

## AI-Assisted Areas

So far, AI assistance has been used for:

* Requirement analysis and ambiguity identification.
* Initial technical design and task decomposition.
* Database-model recommendations and PostgreSQL DDL generation.
* Constraint, foreign-key, and index design.
* API contract and domain-enum generation.
* Request validation and centralized API error handling.
* JPA entity, repository, optimistic-lock, and JSONB mapping.
* Deterministic UTC `Clock` configuration.
* Spring-independent trace-status calculation.
* Status-service response mapping.
* Focused status-calculator unit tests.
* Local environment setup and verification guidance.
* Unit-test and Hurl-test strategy.
* Documentation structure and review.
* Transactional event-ingestion implementation and review.
* Exact and conflicting duplicate-event handling.
* Concurrent event and trace race-recovery design.
* HTTP controller and response-status implementation.
* Diagnosis of the Hibernate 7.2 and Jackson 3 JSON-mapping incompatibility.
* Custom Hibernate `FormatMapper` implementation for Jackson 3.
* PostgreSQL-backed manual API and JSONB verification.

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
* Use Jakarta Bean Validation for field rules and explicit cross-field validation for request invariants.
* Return stable API error codes without exposing parser, SQL, stack-trace, or internal exception details.
* Map persistence entities explicitly to the supplied DDL and keep them separate from API records.
* Store `traceId` directly on event history rather than introducing a bidirectional JPA relationship.
* Use Hibernate JSON mapping with Jackson 3 `JsonNode` for PostgreSQL `JSONB`.
* Calculate trace status in a pure domain component and obtain current time from an injected UTC `Clock`.
* Validate persisted expectation-field pairing before applying status precedence.

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
* Retaining the partial pending-deadline index, because the MVP performs primary-key trace lookup and lazy expiration rather than scanning pending traces by deadline.
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

## Implementation Records

### Task 2 Implementation Record

Codex generated the Task 2 DDL after receiving the clarified design prompt.

Generated changes:

* Added `trace_state` for current trace facts.
* Added `trace_event` for accepted event history.
* Added `completed_at`, expectation fields, latest-event facts, accepted-event count, audit timestamps, and optimistic-lock versioning.
* Stored metadata as PostgreSQL `JSONB`.
* Added primary keys, a foreign key, check constraints, and trace-history indexing.
* Did not add a mutable status column, a redundant completion boolean, a payload hash, or application code.

Verification and final decisions:

* Inspected the complete SQL schema and confirmed that all paired expectation fields, completion invariants, positive TTL rules, positive event-count rules, non-negative version rules, result constraints, the foreign key, and object-only metadata constraints express the intended invariants.
* Confirmed that omitted metadata is normalized by the ingestion service to `{}` and is logically equivalent to an explicit empty object.
* Confirmed that duplicate comparison uses structured `JsonNode` equality, so JSON object property order is irrelevant.
* Removed the partial index on pending expectation deadlines because the application performs primary-key trace lookup and lazy expiration rather than deadline scans.
* Retained `idx_trace_event_trace_id_received_at` for ordered event-history access.
* Corrected the schema header to describe application-provided identifiers rather than requiring UUIDs.
* Reinitialized PostgreSQL from a clean Docker volume.
* Confirmed that the PostgreSQL container reached healthy status.
* Confirmed that the clean initialization created exactly `health`, `trace_state`, and `trace_event`.
* Confirmed that all expected primary keys, foreign keys, check constraints, and indexes were created.
* Task 2 was confirmed complete after clean initialization and schema inspection.

### Task 3 Implementation Record

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

### Task 4 Implementation Record

Codex implemented request validation and centralized API error handling.

Generated changes:

* Added field-level Jakarta Validation constraints to `EventRequest`.
* Added cross-field validation for paired expectation fields, positive TTL, final-event consistency, and object-only metadata.
* Preserved normalization of omitted `finalEvent` to `false`.
* Added `WatchdogNotFoundException`.
* Added `WatchdogConflictException`.
* Added `ApiExceptionHandler` using the existing `ApiErrorResponse`.

Accepted behavior:

* Validation failures return `400 VALIDATION_ERROR`.
* Malformed JSON, invalid enum values, invalid timestamps, and incompatible request types return `400 INVALID_REQUEST`.
* Missing resources return `404 NOT_FOUND`.
* Business conflicts return `409 CONFLICT`.
* Parser internals, stack traces, SQL details, and internal exception names are not exposed.

Manual review and correction:

* A follow-up review found no missing validation rules or out-of-scope changes.
* Deterministic validation-message selection was improved by including field names in the sorting key.
* Both field-level and object-level validation errors are handled consistently.
* No catch-all `Exception` or `RuntimeException` handler was added.
* Tests remain pending because they were outside the scope of Task 4.

Validation performed:

```bash
git diff --check
./mvnw -q -DskipTests compile
```

Both commands passed.

### Task 5 Implementation Record

Codex implemented the JPA persistence layer for the watchdog service.

Generated changes:

* Added `TraceState`.
* Added `TraceEvent`.
* Added `TraceStateRepository`.
* Added `TraceEventRepository`.
* Mapped all entity fields explicitly to the Task 2 DDL.
* Mapped optimistic locking with `@Version`.
* Mapped `EventResult` using `EnumType.STRING`.
* Mapped metadata as PostgreSQL `JSONB` using Jackson 3 `JsonNode` and Hibernate JSON support.
* Added ordered trace-history lookup by `traceId` and `receivedAt`.

Accepted decisions:

* Natural string identifiers are used as entity primary keys.
* `TraceEvent` stores `traceId` directly rather than using a bidirectional JPA relationship.
* Entities contain only mechanical persistence mutations and no lifecycle-transition logic.
* No persisted status field or completion boolean was introduced.

Manual review and correction:

* Entity mappings were checked against the DDL for column names, lengths, nullability, and types.
* Confirmed that entity constructors and mutation methods do not contain business transition rules.
* Confirmed that no equality or hash-code implementation depends on mutable entity state.
* Fixed a mismatch where `TraceEvent.metadata` could be null in Java despite being `NOT NULL` in PostgreSQL.
* `TraceEvent` now rejects null metadata using `Objects.requireNonNull`.
* Task 7 must normalize omitted request metadata to an empty JSON object before constructing the entity.

Validation performed:

```bash
git diff --check
./mvnw -q -DskipTests compile
```

Both commands passed.

Remaining verification:

* JSONB persistence must be exercised through a database-backed flow.
* Metadata normalization remains part of Task 7.

### Task 6 Implementation Record

Codex implemented deterministic trace-status calculation and status-query orchestration.

Generated changes:

* Added `WatchdogConfiguration` with a UTC `Clock` bean using `Clock.systemUTC()`.
* Added Spring-independent `TraceStatusCalculator`.
* Added `TraceStatusService`.
* Added focused unit tests for status calculation.

Implemented status rules:

* `COMPLETED` when `completedAt` is present.
* `STARTED` when no expectation is active.
* `WAITING_OTHER_EVENT` when the current time is before the expectation deadline.
* `TTL_EXPIRED_FOR_EVENT` when the current time is equal to or later than the deadline.
* Expiration is calculated lazily and is not persisted.

Service behavior:

* Loads trace state using `TraceStateRepository.findById`.
* Throws `WatchdogNotFoundException` for unknown traces.
* Uses `Instant.now(clock)` rather than direct system time.
* Delegates status selection to `TraceStatusCalculator`.
* Maps all fields required by `TraceStatusResponse`.

Manual review and correction:

* The initial implementation applied completion precedence before validating expectation-field consistency.
* This allowed a corrupted completed trace with only one expectation field present to be reported as `COMPLETED`.
* The calculator was corrected to validate expectation/deadline pairing before applying status precedence.
* A completed trace with a fully paired stale expectation still correctly returns `COMPLETED`.
* Added tests for completed traces with each possible half-paired expectation state.

Validation performed:

```bash
git diff --check
./mvnw -q test
```

Both commands passed.

Surefire result:

```text
11 tests, 0 failures, 0 errors
```

Remaining verification at that stage:

* The existing Spring context test logged a PostgreSQL connection warning in the sandbox environment, but the Maven test run succeeded.
* `TraceStatusService` was not yet exposed through HTTP.
* Task 6 remained pending until Tasks 7 and 8 were implemented and the public API was manually exercised.

### Task 7 Implementation Record

Codex implemented transactional event ingestion in `EventIngestionService`.

Generated behavior:

* Added `ingest(EventRequest)` returning `EventIngestionResponse`.
* Captures one server acceptance timestamp per ingestion call.
* Uses `TransactionTemplate` so race recovery occurs only after rollback.
* Checks existing `eventId` values before inspecting or mutating trace state.
* Compares the complete logical request payload while excluding server-generated `receivedAt`.
* Uses structured Jackson 3 `JsonNode` equality.
* Normalizes omitted metadata to an empty JSON object.
* Creates and flushes `TraceState` before inserting first-event history because of the database foreign key.
* Rejects completed, late, and unexpected events without persisting history or changing trace state.
* Uses service acceptance time for deadlines, update timestamps, and completion timestamps.
* Retries concurrent trace creation at most once.
* Explicitly flushes writes before reporting success.

Manual review identified two concurrency defects:

* Optimistic-lock failures were initially translated directly to `WatchdogConflictException`, which could incorrectly reject an identical concurrent request.
* An unexplained integrity violation during the bounded retry could be hidden as a business conflict.

Corrections:

* After an optimistic-lock rollback, the service now re-reads `eventId` in a new transaction.
* A matching persisted event returns an idempotent duplicate response.
* A differing persisted event produces a conflicting-duplicate response.
* An optimistic-lock failure becomes a trace-concurrency conflict only when the event remains absent.
* After an integrity-violation rollback, `eventId` is re-read first.
* Concurrent trace creation is retried once only when the event remains absent and the trace now exists.
* Unexplained integrity violations propagate instead of being translated to `409 Conflict`.

Validation performed:

```bash
git diff --check
./mvnw -q test
```

Both commands passed.

Surefire result:

```text
11 tests, 0 failures, 0 errors
```

Remaining verification at that stage:

* Race-recovery paths did not yet have focused concurrency tests.
* JSONB persistence and transaction behavior still required database-backed exercise.
* Task 7 remained pending until Task 8 exposed the ingestion API and manual testing succeeded.

### Task 8 Implementation Record

Codex implemented the HTTP endpoints for event ingestion and trace-status queries.

Files created:

* `src/main/java/com/clara/challenge/watchdog/api/EventController.java`
* `src/main/java/com/clara/challenge/watchdog/api/TraceStatusController.java`

Implemented behavior:

* Added `POST /events`, exposed publicly as `POST /api/events` through the existing servlet context path.
* Added `GET /traces/{traceId}/status`, exposed publicly as `GET /api/traces/{traceId}/status`.
* Applied Jakarta Bean Validation to `EventRequest` using `@Valid`.
* Delegated event processing directly to `EventIngestionService`.
* Returned `201 Created` for newly accepted events.
* Returned `200 OK` for exact idempotent duplicates.
* Delegated trace-status queries directly to `TraceStatusService`.
* Continued using `ApiExceptionHandler` for validation, malformed input, missing traces, and business conflicts.
* Added no repository access, transaction handling, state transitions, or duplicate comparison to the controllers.

Validation performed:

```bash
git diff --check
./mvnw -q test
```

Both commands passed.

Remaining verification at that stage:

* The endpoints required manual exercise against PostgreSQL.
* Runtime JSONB serialization had not yet been exercised.
* Manual verification and the JSONB compatibility correction are recorded below.

### Tasks 6–8 Manual API Verification

The trace-status calculation, transactional event ingestion, and HTTP endpoints were exercised manually against PostgreSQL.

A first ingestion attempt exposed a runtime integration defect:

```text
Could not find a FormatMapper for the JSON format
```

The project uses Spring Boot 4 with Jackson 3 types under `tools.jackson.*`, while Hibernate 7.2 did not automatically discover a compatible JSON format mapper.

Correction:

* Added `Jackson3JsonFormatMapper`, backed by Jackson 3 `JsonMapper`.
* Registered it through `spring.jpa.properties.hibernate.type.json_format_mapper`.
* Did not add Jackson 2 core or databind, JSON-B, Yasson, or dependency-version overrides.
* `jackson-annotations:2.20` remains transitively present through Jackson 3; no direct Jackson 2 dependency was introduced.
* Preserved structured `JsonNode` metadata and PostgreSQL JSONB storage.

Runtime verification confirmed:

* A newly accepted event returns `201 Created` with `duplicate = false`.
* An exact duplicate returns `200 OK` with `duplicate = true`.
* Reordering JSON metadata object properties does not create a conflict.
* PostgreSQL stores metadata as a JSONB object.
* A trace without an expectation reports `STARTED`.
* A trace with a pending expectation reports `WAITING_OTHER_EVENT`.
* An unexpected event returns `409 Conflict` and does not mutate trace state.
* The expected final event completes the trace.
* A completed trace reports `COMPLETED`, clears expectation fields, and retains the correct accepted-event count.
* A new non-duplicate event after completion returns `409 Conflict`.
* An unknown trace returns `404 Not Found`.
* An invalid request returns `400 Bad Request`.
* A trace reports `TTL_EXPIRED_FOR_EVENT` after its deadline.
* An expected event arriving after the deadline returns `409 Conflict`.
* A rejected late event does not increment the event count or change trace state.

Tasks 6, 7, and 8 were marked complete after successful manual verification.

Remaining risk:

* Focused unit coverage for concurrent duplicate insertion, concurrent trace creation, and optimistic-lock recovery was added in Task 9. True concurrent PostgreSQL execution remains outside the unit-test scope.



### Task 9 Unit-Test and Review Record

Codex implemented focused unit coverage for `EventIngestionService` without changing production code.

File created:

* `src/test/java/com/clara/challenge/watchdog/service/EventIngestionServiceTest.java`

Initial implementation:

* Added 28 tests covering normal ingestion, duplicate handling, lifecycle rejection, optimistic-lock recovery, integrity-violation recovery, timestamp reuse, and bounded trace-creation retry behavior.
* Used the real `TransactionTemplate` through a mocked `PlatformTransactionManager`.
* Configured each transaction to receive a fresh `SimpleTransactionStatus`.
* Used mocked repositories and real `TraceState` and `TraceEvent` instances.
* Used a controlled `Clock` to verify one acceptance timestamp per public `ingest` call.
* Verified rollback-before-recovery-read ordering with Mockito `InOrder`.
* Confirmed that no Spring context, database, H2, Testcontainers, sleeps, reflection, controller tests, or Hurl tests were introduced.

Review findings:

* Half-paired persisted expectation coverage initially included only expected-event name without deadline.
* Completed and expired duplicate behavior was initially combined into one broad test.
* Rollback-order helpers did not verify the failing repository operation before rollback.
* Some persisted `TraceEvent` assertions were incomplete.
* Normal and exact-duplicate transaction commits were not explicitly verified.

Corrections:

* Split completed-trace and expired-trace duplicate short-circuit coverage into separate tests.
* Added the deadline-without-expected-event persisted-state test.
* Renamed the original half-paired-state test to identify the name-without-deadline case explicitly.
* Added complete persisted `TraceEvent` field assertions in normal-ingestion paths.
* Added commit-without-rollback verification for normal first-event and exact-duplicate transactions.
* Tightened failure-operation, rollback, recovery-transaction, and recovery-read ordering assertions.
* Added retry-count assertions for bounded trace-creation retry paths.
* Kept all tests routed through the public `EventIngestionService.ingest` method.
* Added no production, configuration, dependency, DDL, controller, Hurl, or documentation behavior changes as part of the test implementation.

Validation performed:

```bash
git diff --check
./mvnw -q -Dtest=EventIngestionServiceTest test
./mvnw -q test
```

Results:

```text
EventIngestionServiceTest: 30 tests, 0 failures, 0 errors
Full suite: 41 tests, 0 failures, 0 errors
```

Environment note:

* Mockito's inline mock maker could not attach the Byte Buddy agent inside the sandboxed JVM, so the Maven test runs were repeated outside the sandbox.
* This was an execution-environment limitation rather than a production-code defect.

Remaining risk:

* These tests provide unit coverage of concurrency-recovery behavior using mocked repositories and a mocked transaction manager.
* They do not prove real concurrent PostgreSQL behavior, transaction isolation behavior, or database race timing.
* Those concerns remain suitable for manual verification, Hurl coverage where applicable, or a future integration-test layer.

At this stage, Task 9 had not yet been marked complete in `TASKS.md` because the test file still required commit and repository-level review.


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

The remaining documentation work is:

* Record the final Task 9 commit and repository-level review, then mark Task 9 complete in `TASKS.md`.
* Record Hurl scenarios and their execution results.
* Record README and API-documentation changes.
* Record final submission validation.
* Record any defects discovered during Tasks 10–12 and their corrections.

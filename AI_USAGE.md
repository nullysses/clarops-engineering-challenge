# AI Usage

This document records how AI tools were used during the challenge. It will be updated as implementation progresses.

## Tools Used

* ChatGPT

## Main Prompts

### Prompt 1 — Repository and requirement analysis

> Review the Clara Clarops engineering challenge repository. Identify the explicit requirements, ambiguous areas, likely evaluation criteria, implementation risks, and an appropriate MVP design.

### Prompt 2 — Technical design

> Propose a simple Spring Boot and PostgreSQL design for the distributed event watchdog challenge. Avoid Kafka, schedulers, migration frameworks, distributed locks, and other out-of-scope infrastructure. Focus on event ingestion, trace state, TTL expiration, completion, persistence, and testability.

### Prompt 3 — Task breakdown

> Split the challenge into small, explicit implementation tasks suitable for an AI-assisted workflow. Avoid broad tasks that require the AI to infer the entire solution. Include database design, validation, state transitions, tests, Hurl scenarios, documentation, and final verification.

### Prompt 4 — Unit-test standard

> Define unit tests for the event-watchdog domain logic using the following standard:
>
> * Test names follow `shouldExpectedBehavior_WhenCondition`.
> * Each test validates one business rule.
> * Use Arrange / Act / Assert structure.
> * Use a fixed or controllable `Clock` for time-dependent behavior.
> * Focus on state transitions, TTL expiration, final events, duplicates, unexpected events, late events, and post-completion behavior.
> * Avoid testing Spring or JPA wiring unless necessary.
> * Do not test behavior that is neither required nor explicitly documented as an assumption.
> * Explain which requirement or assumption each test validates.

### Prompt 5 — Hurl test design

> Define Hurl end-to-end tests for the public API of the distributed event watchdog service.
>
> Cover:
>
> * `STARTED`;
> * `WAITING_OTHER_EVENT`;
> * `COMPLETED`;
> * `TTL_EXPIRED_FOR_EVENT`;
> * selected duplicate, unexpected, and late-event behaviors.
>
> Validate only HTTP requests and responses. Do not depend on direct database queries. Keep scenarios independent and deterministic.

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

Implementation assistance will be documented here as it occurs.

## Accepted Suggestions

The following initial suggestions were accepted for the implementation plan:

* Keep the solution within the requested MVP scope.
* Use PostgreSQL for both event history and current trace state.
* Preserve accepted event history for auditability.
* Maintain a separate current-state record for efficient status queries.
* Store metadata as PostgreSQL `JSONB`.
* Evaluate TTL lazily when status is queried.
* Avoid a scheduler because the challenge explicitly permits lazy expiration.
* Avoid Kafka, SQS, Pub/Sub, Flyway, Liquibase, and distributed locks.
* Inject `Clock` for deterministic TTL tests.
* Derive the externally reported status from persisted facts rather than storing a redundant status field.
* Use database constraints and transactional updates to reduce inconsistent state.
* Create planning and AI-usage documentation before implementing application code.

## Initial Design Decisions

These decisions must remain consistent across the code, tests, and documentation:

* TTL will be calculated from the time the service accepts the event rather than from client-provided `occurredAt`.
* A trace is expired when the current time is equal to or later than the deadline.
* An exact duplicate event is treated as idempotent.
* A reused `eventId` with different content is treated as a conflict.
* An unexpected event does not advance the trace.
* An expected event arriving after expiration is rejected and does not revive the trace.
* A completed trace does not accept new non-duplicate events.
* A first event may also be a final event.
* Event `result` and trace lifecycle are independent.
* Metadata is persisted rather than ignored.
* Unknown traces return `404`.
* Arrival order is authoritative for state transitions; `occurredAt` is retained as event information.

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
* Adding an additional Git implementation branch, because the candidate chose to work directly on the fork's `develop` branch.
* Installing SDKMAN, because the development environment already provides a valid Java 21 installation.

## Manual Corrections and Adjustments

* Confirmed that the existing Debian OpenJDK 21 installation satisfies the project requirements; SDKMAN is optional.
* Confirmed that Docker Desktop integration with WSL can run the supplied PostgreSQL setup.
* Confirmed the untouched application starts successfully.
* Confirmed the baseline health endpoint returns:

```text
clarops sr engineer challenge
```

* Corrected the initial workflow suggestion to use a separate implementation branch; development will continue directly on `develop`.

## Manual Review Responsibilities

All AI-assisted output will be reviewed for:

* Consistency with the documented assumptions.
* Correct transaction boundaries.
* Correct handling of duplicate and conflicting events.
* Correct deadline and boundary calculations.
* Database constraints matching application rules.
* Deterministic time-dependent tests.
* HTTP status codes and response contracts.
* Hurl scenarios matching public behavior.
* Unnecessary abstractions or dependencies.
* Code that cannot be explained during the technical interview.

## Pending Updates

This document must be updated after implementation to include:

* Prompts used for generated or revised Java code.
* Prompts used for DDL.
* Prompts used for unit tests.
* Prompts used for Hurl tests.
* AI-generated code that was accepted.
* AI-generated code that was rejected.
* Bugs or inconsistencies found during manual review.
* Final corrections made before submission.

# Distributed Event Watchdog Challenge

## Context

In Clarops, some operational flows are distributed across multiple services. Each service may emit events when it completes an action. In many cases, after one event is received, another event is expected to arrive within a specific time window.

The goal of this challenge is to build a small service that receives distributed events, tracks the state of a flow by `traceId`, and reports whether the flow is started, waiting for another event, completed, or expired because an expected event did not arrive within the configured TTL.

This challenge is intentionally small in scope, but some requirements are intentionally open. We want to understand how you analyze ambiguity, define assumptions, split development work into tasks, use AI tools, and make technical decisions.

You may use AI tools such as ChatGPT, Claude, Cursor, Copilot, or similar.

---

## Running the Project

For setup and run instructions see **[SETUP.md](SETUP.md)**.

---

## Expected Duration

This challenge is designed to be completed in **3 to 4 hours**.

We do not expect a production-ready system. We expect a clear, functional MVP with good analysis, documented trade-offs, meaningful tests, and a reasonable implementation.

---

## Repository Notes

The repository already includes the base setup required to run with:

- Spring Boot
- PostgreSQL
- Basic application structure
- Database connectivity
- An existing endpoint that reads information from the database

You should build your solution on top of the existing project.

You do **not** need to add Kafka, SQS, Pub/Sub, Flyway, Liquibase, Docker, or any external event infrastructure.

---

# Goal

Build a service with two endpoints:

```
POST /events
GET /traces/{traceId}/status
```

The service must:

1. Receive events associated with a `traceId`.
2. Track the current state of the distributed flow.
3. Allow an event to define the next expected event and the TTL to wait for it.
4. Allow an event to indicate that the flow has finished.
5. Return the current status of the flow by `traceId`.
6. Detect TTL expiration when the expected next event does not arrive on time.

---

# Functional Requirements

## 1. Receive Events

The service must expose:

```
POST /events
```

This endpoint receives an event related to a distributed flow.

### Request Body Contract

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "finalEvent": false,
  "metadata": {
    "country": "MX",
    "entityId": "company-123"
  }
}
```

### Field Description

|         Field         |   Type   | Required |                            Description                             |
|-----------------------|----------|----------|--------------------------------------------------------------------|
| `eventId`             | string   | Yes      | Unique identifier of the event.                                    |
| `traceId`             | string   | Yes      | Identifier of the distributed flow.                                |
| `eventName`           | string   | Yes      | Name of the event being reported.                                  |
| `result`              | string   | Yes      | Result of the event. Allowed values: `SUCCESS`, `ERROR`.           |
| `occurredAt`          | datetime | Yes      | Date and time when the event occurred.                             |
| `nextExpectedEvent`   | string   | No       | Name of the next event expected for this trace.                    |
| `nextEventTtlSeconds` | number   | No       | Maximum number of seconds to wait for the next expected event.     |
| `finalEvent`          | boolean  | No       | Indicates whether this event completes the flow. Default: `false`. |
| `metadata`            | object   | No       | Flexible metadata associated with the event.                       |

---

## 2. Query Trace Status

The service must expose:

```
GET /traces/{traceId}/status
```

This endpoint returns the current state of the flow associated with the given `traceId`.

The status must be calculated based on the received events and the TTL of the next expected event.

The service does **not** need to implement a scheduler or background job. TTL expiration may be evaluated when the status endpoint is called.

---

# Trace Statuses

The service must support the following statuses:

|         Status          |                                               Description                                                |
|-------------------------|----------------------------------------------------------------------------------------------------------|
| `STARTED`               | The first event was received, but there is no next expected event defined and the flow is not completed. |
| `WAITING_OTHER_EVENT`   | The latest event defined a next expected event and its TTL has not expired yet.                          |
| `TTL_EXPIRED_FOR_EVENT` | The expected next event did not arrive before the configured TTL expired.                                |
| `COMPLETED`             | A received event marked the flow as completed using `finalEvent = true`.                                 |

---

# Status Response Contract

The exact response structure is part of the design you must define.

However, the response should include enough information to understand the current state of the trace, such as:

```json
{
  "traceId": "trace-123",
  "status": "WAITING_OTHER_EVENT",
  "lastEventName": "APPLICATION_RECEIVED",
  "lastEventResult": "SUCCESS",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextExpectedBefore": "2026-06-15T10:02:00Z",
  "eventsReceived": 1
}
```

You may add or remove fields if your design justifies it. Please document your decision.

---

# Expected Behavior

## First Event

When the first event for a `traceId` is received, the service must create the trace state.

The resulting status depends on the event:

- If the event defines a next expected event and TTL, the trace should move to `WAITING_OTHER_EVENT`.
- If the event does not define a next expected event and is not final, the trace should remain in `STARTED`.
- If the event is marked as final, the trace should move to `COMPLETED`.

---

## Waiting for Another Event

When an event defines `nextExpectedEvent` and `nextEventTtlSeconds`, the trace should wait for that event until the TTL expires.

You must decide and document how the TTL is calculated.

For example, you may calculate it from:

- `occurredAt`
- the time the service received the event
- another approach you consider better for this MVP

---

## Expected Event Arrives

If the next received event matches the expected event, the trace should advance.

The new event may:

- define another expected event;
- complete the flow;
- leave the flow in another valid state according to your design.

---

## TTL Expiration

If the expected event does not arrive within the configured TTL, the trace status must be reported as:

```
TTL_EXPIRED_FOR_EVENT
```

A real alerting system is not required. For this MVP, the expired status is enough to represent the alert.

---

## Final Event

If an event is received with `finalEvent = true`, the trace must be marked as:

```
COMPLETED
```

A completed trace should not be reported as expired.

---

## Event Result

The event result must support:

```
SUCCESS
ERROR
```

The `result` indicates the outcome of the event, not necessarily the overall status of the trace.

For example, a flow may be completed with a final event whose result is `ERROR`.

---

# Database Requirement

You must define the database schema required for your solution.

One of your development tasks must be:

> Define the DDL for the data model and include the initialization script inside the `docker/init-scripts/db/` folder.

You do **not** need to use Flyway, Liquibase, or any similar migration tool. A plain SQL initialization script is enough.

Your schema should support the behavior required by the challenge. You are expected to make and document your own decisions regarding:

- tables;
- columns;
- constraints;
- indexes;
- whether to store event history, current trace state, or both;
- how to store `metadata`.

---

# Open Questions

Some requirements are intentionally open. You are expected to identify them, make reasonable decisions, and document your assumptions.

Please include these decisions in your `README.md`.

Examples of open questions:

1. What should happen if the same `eventId` is received more than once?
2. What should happen if an event arrives with a different `eventName` than the currently expected event?
3. What should happen if the expected event arrives after the TTL already expired?
4. Should TTL be calculated from `occurredAt` or from the time the event was received by the service?
5. Should a completed trace accept more events?
6. What should happen if the first event is also a final event?
7. What should happen if an event with `result = ERROR` defines a next expected event?
8. Should `metadata` be stored as JSON, structured columns, or ignored?
9. How should the service avoid inconsistent trace states?
10. What should be returned when querying a `traceId` that does not exist?

There is no single correct answer for all of these. We care about your reasoning, trade-offs, and implementation quality.

---

# Required Deliverables

Your submitted fork must include:

1. Functional code.
2. Database schema DDL.
3. SQL initialization script inside the `docker/init-scripts/db/` folder.
4. `README.md` updated with:
   - problem understanding;
   - assumptions;
   - technical decisions;
   - trade-offs;
   - how to run the project;
   - how to run the Hurl tests;
   - request and response examples.
5. `TASKS.md` with the tasks used during development.
6. `AI_USAGE.md` with the AI tools and prompts used.
7. Unit tests.
8. Hurl end-to-end tests covering the expected use cases.
9. All Hurl tests must pass successfully.

---

Submission Instructions

# TASKS.md Requirement

The `TASKS.md` file is important.

We want to understand how you split the problem before implementing it, especially if you use an LLM to help you write the code.

A good task breakdown should avoid vague instructions such as:

```
Implement the whole challenge.
```

or:

```
Create the watchdog service.
```

Those tasks are too broad and may cause an LLM to infer too much, spend more time, produce unnecessary code, or use more tokens than needed.

Instead, prefer small and explicit tasks.

Example:

```markdown
# Tasks

## Task 1 — Define data model and DDL

- Define the tables required to support the solution.
- Define constraints and indexes.
- Add SQL initialization script under `docker/init-scripts/db/`.

## Task 2 — Implement event ingestion contract

- Create the request contract for `POST /events`.
- Validate required fields.
- Persist the received event.
- Create or update trace state.

## Task 3 — Implement trace status logic

- Implement the logic to return `STARTED`, `WAITING_OTHER_EVENT`, `TTL_EXPIRED_FOR_EVENT`, or `COMPLETED`.
- Define how TTL is calculated.
- Document edge-case decisions.

## Task 4 — Implement status endpoint

- Create `GET /traces/{traceId}/status`.
- Return the current trace state.
- Handle unknown `traceId`.

## Task 5 — Add tests

- Add unit tests for the core state transition logic.
- Add Hurl tests for at least two end-to-end scenarios.
```

You may define your own tasks. The important part is that they are clear, scoped, and useful for development.

---

# Unit Tests Requirement

You must include unit tests.

We do not expect unit tests to be randomly selected by an LLM. We expect you to define a clear testing standard and then use it consistently.

Your unit tests should focus on the core business logic, especially:

- first event creates the trace state;
- event with next expected event moves the trace to `WAITING_OTHER_EVENT`;
- TTL expiration returns `TTL_EXPIRED_FOR_EVENT`;
- final event moves the trace to `COMPLETED`;
- duplicate event behavior, if you choose to support it;
- late event behavior, if you choose to support it.

You do not need to test every possible edge case. We care more about whether your tests are meaningful, consistent, and aligned with your decisions.

---

## Unit Test Prompt Requirement

If you use AI to define or generate unit tests, you must include the prompt you used in `AI_USAGE.md`.

The prompt should define a testing standard. It should not simply ask the LLM to “write any tests”.

Avoid prompts like:

```
Write unit tests for this service.
```

Prefer prompts that define clear expectations, for example:

```
Define unit tests for the Event Watchdog state transition service.

Use the following standard:
- Test method names should follow: shouldExpectedBehavior_WhenCondition.
- Each test should validate one business rule only.
- Use Arrange / Act / Assert structure.
- Avoid testing framework or Spring wiring unless necessary.
- Focus on state transitions, TTL expiration, final event behavior, duplicate events, and late events.
- Do not add tests for behavior not defined in the challenge unless clearly marked as an assumption.
- For every test, explain which requirement or assumption it validates.
```

You may use your own standard. The important part is that your prompt makes the expected testing style explicit.

---

# E2E Validation with Hurl

The Hurl tests are part of the official validation criteria for this challenge.

We will use the Hurl end-to-end tests to validate the expected use cases implemented by the candidate. These tests are expected to run successfully in **100% of the covered scenarios**.

The goal of the Hurl tests is to validate the service from the outside, using the public HTTP contract, not internal implementation details.

The Hurl tests should validate behavior through the public HTTP API:

```
POST /events
GET /traces/{traceId}/status
```

Do not rely on direct database queries for Hurl validation unless you clearly justify it.

---

## Required Hurl Coverage

At minimum, the Hurl tests must cover:

1. A trace that starts and reaches `STARTED`.
2. A trace that moves to `WAITING_OTHER_EVENT`.
3. A trace that reaches `COMPLETED`.
4. A trace that reaches `TTL_EXPIRED_FOR_EVENT`.

If your implementation includes additional decisions, such as duplicate event handling, late event handling, or unexpected event handling, please include Hurl tests for those scenarios as well.

A suggested folder structure is:

```
hurl/
  started-flow.hurl
  waiting-other-event-flow.hurl
  completed-flow.hurl
  ttl-expired-flow.hurl
```

Please include instructions in the README explaining how to run the Hurl tests.

Example:

```bash
hurl --test hurl/*.hurl
```

---

# AI-Assisted Implementation

You may use AI tools freely to implement this challenge.

This includes, but is not limited to:

- data model design;
- DDL definition;
- Spring components;
- controllers;
- services;
- repositories;
- DTOs;
- unit tests;
- Hurl scripts;
- documentation;
- refactoring;
- debugging.

Using AI is allowed and expected.

However, the candidate is responsible for understanding the final solution.

During the technical interview, we may ask questions about any part of the implementation, including:

- why the data model was designed that way;
- why certain tables, columns, constraints, or indexes were created;
- why a specific state transition was implemented;
- how TTL expiration is calculated;
- how duplicate events are handled;
- how late events are handled;
- how unexpected events are handled;
- why certain unit tests were included or excluded;
- why the Hurl scenarios were selected;
- how the implementation would change with more time;
- what parts were generated by AI and what parts were manually adjusted.

The purpose is not to penalize AI usage. The purpose is to understand whether the candidate can reason about what the LLM produced and whether they can explain, validate, and challenge the generated code.

Generated code is acceptable. Unexplained generated code is not.

---

# AI_USAGE.md Requirement

Please include an `AI_USAGE.md` file with:

1. AI tools used.
2. Main prompts used.
3. Prompt used to define the unit test standard.
4. Prompts used to generate or refine Hurl tests.
5. What parts of the solution were generated or assisted by AI.
6. Which AI suggestions you accepted.
7. Which AI suggestions you rejected and why.
8. Any important correction you made to the AI output.
9. Any part of the generated code that required manual review or adjustment.

Example structure:

```markdown
# AI Usage

## Tools Used

- Claude
- ChatGPT
- Cursor

## Prompts

### Prompt 1 — Requirement analysis

> Help me identify edge cases for a service that receives distributed events and tracks TTL expiration for the next expected event.

### Prompt 2 — Task breakdown

> Split this challenge into small implementation tasks suitable for an LLM-assisted workflow. Avoid broad tasks that require too much inference.

### Prompt 3 — Unit test standard

> Define unit tests for the Event Watchdog state transition service.
>
> Use the following standard:
> - Test method names should follow: shouldExpectedBehavior_WhenCondition.
> - Each test should validate one business rule only.
> - Use Arrange / Act / Assert structure.
> - Focus on state transitions and TTL expiration.
> - Do not add tests for behavior not defined in the challenge unless clearly marked as an assumption.

### Prompt 4 — Hurl E2E scenarios

> Define Hurl end-to-end tests for the public HTTP contract of the Event Watchdog service.
>
> Cover these scenarios:
> - trace starts successfully;
> - trace waits for another event;
> - trace completes successfully;
> - trace expires when the expected event does not arrive within TTL.
>
> Validate only public API responses. Do not assert internal database details.

## Accepted Suggestions

- Example: Using lazy TTL evaluation in the status endpoint.
- Example: Storing event metadata as JSON.

## Rejected Suggestions

- Example: Kafka-based implementation, because the challenge does not require real event infrastructure.
- Example: Scheduler-based expiration, because lazy evaluation is enough for the MVP.

## Manual Decisions

- Example: TTL is calculated from server acceptance time, not from `occurredAt`.
- Example: Exact duplicate `eventId` requests are idempotent; reused IDs with different logical content
  return conflict.
- Example: A completed trace does not accept new non-duplicate events.
```

---

# Out of Scope

The following items are not required:

- Kafka, SQS, Pub/Sub, or any real message broker.
- Scheduler or background job.
- External alert notification system.
- UI.
- Authentication or authorization.
- Docker changes, unless you consider them necessary.
- Flyway, Liquibase, or database migration tools.
- Multi-tenant support.
- Multiple dynamic flow definitions.
- Production-grade observability.
- Distributed locks.
- Retry mechanisms.

---

# Submission Instructions

Please submit your solution as a fork of this repository.

## Expected Submission Format

1. Fork this repository.
2. Implement your solution in your fork.
3. Push all required changes to your fork.
4. Share the repository URL with the recruiting or interview team.

If your fork is private, please make sure the required reviewers have access before submitting it.

Do not submit the solution as a ZIP file unless explicitly requested by the team.

## Submission Checklist

Before submitting, please make sure your fork includes:

- Functional implementation.
- Database schema DDL.
- SQL initialization script inside the `docker/init-scripts/db/` folder.
- Updated `README.md`.
- `TASKS.md`.
- `AI_USAGE.md`.
- Unit tests.
- Hurl E2E tests.
- Instructions to run the application.
- Instructions to run the Hurl tests.
- All covered Hurl scenarios passing successfully.

The submitted repository should be self-contained enough for reviewers to run, inspect, and discuss during the technical interview.

# Evaluation Criteria

We will evaluate more than the final code.

Because this challenge is designed to be completed in **3 to 4 hours**, we do not expect every area to be equally polished. Candidates should prioritize the most important parts first.

## Highest-Weight Criteria

The following criteria carry the most weight:

| Priority |                    Criteria                    |                                                                       What we are looking for                                                                        |
|----------|------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1        | Requirement analysis and task breakdown        | Ability to identify ambiguity, define reasonable assumptions, and split the work into clear, scoped tasks that reduce unnecessary inference when using AI tools.     |
| 2        | Core implementation and technical design       | A simple but coherent implementation of events, trace state, TTL expiration, and completion using a reasonable data model.                                           |
| 3        | Validation and understanding of generated code | Hurl E2E tests passing for the expected use cases, meaningful unit tests, and ability to explain and defend implementation decisions during the technical interview. |

## Full Evaluation Criteria

|            Criteria             |                                       What we are looking for                                       |
|---------------------------------|-----------------------------------------------------------------------------------------------------|
| Requirement analysis            | Ability to identify ambiguity and define reasonable assumptions.                                    |
| Task breakdown                  | Clear, scoped tasks that reduce unnecessary inference when using AI tools.                          |
| Technical design                | Simple but coherent model for events, trace state, TTL, and completion.                             |
| Database design                 | Reasonable DDL, constraints, indexes, and initialization script.                                    |
| Implementation                  | Functional endpoints and correct core behavior.                                                     |
| Unit tests                      | Meaningful tests focused on business rules and state transitions.                                   |
| Hurl E2E tests                  | Public API validation for all expected use cases, with tests passing successfully.                  |
| AI usage                        | Clear documentation of prompts, accepted suggestions, rejected suggestions, and manual corrections. |
| Understanding of generated code | Ability to explain and defend implementation decisions during the technical interview.              |
| Communication                   | Clear README, trade-offs, and explanation of decisions.                                             |

A strong submission does not need to over-engineer the solution. A strong submission should show good judgment, a working core flow, clear validation, and ownership of the AI-assisted implementation.

---

# Interview Follow-up

After submitting the challenge, the technical interview may include questions about the implementation.

The interviewer may ask the candidate to explain:

- a specific class, function, or state transition;
- a DDL decision;
- a unit test;
- a Hurl scenario;
- a prompt used to generate part of the solution;
- a trade-off documented in the README;
- an edge case that was intentionally left out;
- how the implementation would evolve with more time.

The goal is to evaluate how the candidate works with AI-generated code, how they review it, how they reason about it, and whether they can take ownership of the final solution.

---

# Final Notes

Keep the solution simple.

We are not looking for a perfect event-driven platform. We are looking for a small and thoughtful implementation that shows how you reason about distributed events, TTL expiration, ambiguous requirements, database modeling, AI-assisted development, task decomposition, and testing standards.

---

# Implemented Solution

## Problem Understanding

The service receives events belonging to distributed operational flows identified by `traceId`.
Each accepted event may:

* leave the trace active without another expected event;
* define the next expected event and the TTL for receiving it;
* complete the trace.

The implementation persists both accepted event history and the current facts required to answer
trace-status queries efficiently. Status is derived from those facts rather than stored as a mutable
status value.

TTL expiration is evaluated lazily when the status endpoint is called. No scheduler, background
expiration process, or external alerting system is required for this MVP.

The public endpoints include the configured `/api` servlet context path:

```text
POST /api/events
GET  /api/traces/{traceId}/status
```

## Assumptions and Behavioral Decisions

### TTL calculation

TTL is calculated from the server acceptance time, not from the client-provided `occurredAt`.

```text
nextExpectedBefore = receivedAt + nextEventTtlSeconds
```

This prevents client clock skew, delayed delivery, and backdated events from changing the operational
deadline.

A trace is expired when:

```text
currentTime >= nextExpectedBefore
```

The exact deadline boundary is therefore expired.

### Event ordering

Events are processed in service-arrival order.

`occurredAt` is retained as event information and persisted in event history, but it does not control
state-transition ordering.

### Duplicate events

`eventId` is globally unique.

* An exact logical duplicate is idempotent and returns `200 OK` with `duplicate = true`.
* An exact duplicate does not increment `eventsReceived`, append another history record, or modify
  trace state.
* Reusing an `eventId` with different logical content returns `409 Conflict`.
* Exact duplicates remain idempotent even when the associated trace is completed or expired.

Duplicate equality includes:

* `eventId`;
* `traceId`;
* `eventName`;
* `result`;
* `occurredAt`;
* `nextExpectedEvent`;
* `nextEventTtlSeconds`;
* normalized `finalEvent`;
* normalized structured metadata.

The server-generated `receivedAt` value is excluded.

Omitted metadata is normalized to `{}`. An omitted metadata value and an explicit empty object are
therefore equivalent. Metadata comparison uses structured JSON equality, so object-property order is
irrelevant.

### Unexpected events

When a trace is waiting for a specific event, another event name is rejected with `409 Conflict`.

The rejected event is not persisted and does not modify the latest-event facts, expectation,
completion state, or accepted-event count.

### Late events

If the expected event arrives at or after its deadline, it is rejected with `409 Conflict`.

The event does not revive the trace. The trace continues to report
`TTL_EXPIRED_FOR_EVENT`, and its state remains unchanged.

### Completed traces

A first event may also be a final event.

After completion:

* exact duplicates remain idempotent;
* new non-duplicate events are rejected with `409 Conflict`;
* pending expectation fields are cleared;
* the trace is always reported as `COMPLETED`, never expired.

### Event result

`result` describes the outcome of the individual event and does not independently determine trace
lifecycle.

An event with `result = ERROR` may still:

* define another expected event;
* leave the trace active without an expectation;
* complete the trace.

### Final-event consistency

An event with `finalEvent = true` must not define `nextExpectedEvent` or
`nextEventTtlSeconds`.

Contradictory requests return `400 Bad Request`.

### Next-event fields

`nextExpectedEvent` and `nextEventTtlSeconds` must either both be provided or both be absent.

When present:

* `nextExpectedEvent` must be non-blank;
* `nextEventTtlSeconds` must be greater than zero.

### `STARTED` semantics

`STARTED` means that the trace is active, not completed, and currently has no expected next event.

It may apply after more than one accepted event, not only immediately after the first event. An
accepted event without a new expectation clears any previous expectation and leaves the active trace
in `STARTED`.

### Metadata

Event metadata is persisted as PostgreSQL `JSONB`.

Metadata must be a JSON object. Arrays and scalar values are rejected. The service stores metadata
but does not interpret it for state transitions.

### Missing traces

Querying an unknown `traceId` returns `404 Not Found`.

## Technical Design

The implementation uses a small layered structure:

```text
API controllers and contracts
        |
        v
application services
        |
        v
domain status calculation
        |
        v
Spring Data JPA repositories
        |
        v
PostgreSQL
```

### API layer

The API layer contains:

* request and response records;
* Jakarta Bean Validation constraints;
* `EventController`;
* `TraceStatusController`;
* centralized exception handling through `ApiExceptionHandler`.

Controllers perform request binding, validation, service delegation, and HTTP success-status
selection. They do not contain persistence, duplicate comparison, deadline calculation, or
state-transition logic.

### Service layer

`EventIngestionService` coordinates:

* duplicate lookup;
* metadata normalization;
* trace creation and updates;
* lifecycle validation;
* accepted event-history persistence;
* transaction boundaries;
* optimistic-lock and uniqueness-race recovery.

`TraceStatusService` loads persisted trace facts, obtains the current time from an injected UTC
`Clock`, delegates status selection to `TraceStatusCalculator`, and maps the result to
`TraceStatusResponse`.

### Domain layer

`TraceStatusCalculator` is Spring-independent and side-effect free. It derives status from:

* `completedAt`;
* `nextExpectedEvent`;
* `nextExpectedBefore`;
* the supplied current time.

No mutable status column is stored.

### Time handling

All watchdog lifecycle timestamps use an injected UTC `Clock`.

One server acceptance timestamp is captured for each public ingestion call and reused for:

* trace creation or update timestamps;
* event-history `receivedAt`;
* completion;
* expectation-deadline calculation;
* the single bounded trace-creation retry.

## Database Model

The schema is defined in:

```text
docker/init-scripts/db/01-init-schema.sql
```

The application uses the existing PostgreSQL schema:

```text
clarops_challenge_schema
```

### `trace_state`

`trace_state` stores the latest accepted facts for each trace:

|           Column           |                   Purpose                    |
|----------------------------|----------------------------------------------|
| `trace_id`                 | Natural primary key for the distributed flow |
| `last_event_*`             | Latest accepted event facts                  |
| `next_expected_event`      | Current expected event name                  |
| `next_expected_before`     | Server-calculated expectation deadline       |
| `completed_at`             | Completion fact and timestamp                |
| `events_received`          | Count of accepted, non-duplicate events      |
| `created_at`, `updated_at` | Trace audit timestamps                       |
| `version`                  | Optimistic-lock version                      |

Status is derived from these facts. There is no persisted mutable status column or separate completion
boolean.

### `trace_event`

`trace_event` stores one immutable history row for every newly accepted event:

|                     Column                      |                Purpose                 |
|-------------------------------------------------|----------------------------------------|
| `event_id`                                      | Global event primary key               |
| `trace_id`                                      | Owning trace, enforced by foreign key  |
| `event_name`, `result`                          | Event identity and outcome             |
| `occurred_at`                                   | Client-provided event time             |
| `received_at`                                   | Server acceptance time                 |
| `next_expected_event`, `next_event_ttl_seconds` | Logical request expectation fields     |
| `final_event`                                   | Whether this event completed the trace |
| `metadata`                                      | Structured PostgreSQL `JSONB` object   |

The full logical request is retained so exact and conflicting duplicate events can be distinguished
without relying solely on a payload hash.

### Constraints and index

The database enforces:

* valid event-result values;
* paired expectation fields;
* positive TTL values;
* completed traces without pending expectations;
* positive accepted-event counts;
* non-negative optimistic-lock versions;
* metadata containing a JSON object;
* event history referencing an existing trace.

The primary keys provide final uniqueness safeguards for `traceId` and `eventId`.

The index:

```text
idx_trace_event_trace_id_received_at
```

supports ordered event-history access by trace. No deadline-scan index is included because this MVP
uses primary-key trace lookup and lazy expiration rather than scanning pending traces.

### Why two tables?

Keeping both current state and event history provides:

* constant-size status lookup by `traceId`;
* accepted-event audit history;
* complete logical duplicate comparison;
* database-level uniqueness for event IDs;
* no need to rebuild current state from the full event stream on every query.

## State-Transition Rules

|        Current condition         |          Incoming request           |                Result                 |
|----------------------------------|-------------------------------------|---------------------------------------|
| Trace does not exist             | Non-final event without expectation | Create trace as `STARTED`             |
| Trace does not exist             | Non-final event with expectation    | Create trace as `WAITING_OTHER_EVENT` |
| Trace does not exist             | Final event                         | Create trace as `COMPLETED`           |
| Active trace without expectation | New accepted event                  | Apply the new event outcome           |
| Waiting before deadline          | Expected event                      | Accept and apply its next outcome     |
| Waiting before deadline          | Unexpected event                    | `409 Conflict`; state unchanged       |
| Waiting at or after deadline     | Expected event                      | `409 Conflict`; trace remains expired |
| Completed trace                  | New non-duplicate event             | `409 Conflict`; state unchanged       |
| Any trace state                  | Exact duplicate event               | `200 OK`; state unchanged             |
| Any trace state                  | Same `eventId`, different payload   | `409 Conflict`; state unchanged       |

For an accepted event, its outcome is applied as follows:

* `finalEvent = true` sets `completedAt` and clears expectation fields;
* a new expectation stores its name and `receivedAt + TTL`;
* no new expectation clears prior expectation fields and leaves the trace active;
* `eventsReceived` increments exactly once.

## Consistency and Concurrency

Each newly accepted event updates `trace_state` and inserts `trace_event` in one database transaction.
A partial update cannot be returned as a successful ingestion.

Duplicate lookup occurs before trace-state inspection or mutation.

Concurrency safeguards are:

* `trace_event.event_id` primary-key uniqueness for concurrent duplicate insertion;
* `trace_state.trace_id` primary-key uniqueness for concurrent first-event trace creation;
* `@Version` optimistic locking for concurrent updates to an existing trace;
* post-rollback event lookup to recognize a concurrently committed exact duplicate;
* one bounded retry when a concurrent request created the trace first;
* propagation of unexplained integrity failures rather than hiding them as business conflicts.

The first trace-state row is flushed before inserting its event-history row because
`trace_event.trace_id` has a foreign key to `trace_state.trace_id`.

## HTTP API

### Submit an event

```http
POST /api/events
Content-Type: application/json
```

Example request:

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "finalEvent": false,
  "metadata": {
    "country": "MX",
    "entityId": "company-123"
  }
}
```

Newly accepted event — `201 Created`:

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "duplicate": false
}
```

Exact duplicate — `200 OK`:

```json
{
  "eventId": "evt-001",
  "traceId": "trace-123",
  "duplicate": true
}
```

### Query trace status

```http
GET /api/traces/trace-123/status
```

Example waiting response — `200 OK`:

```json
{
  "traceId": "trace-123",
  "status": "WAITING_OTHER_EVENT",
  "lastEventId": "evt-001",
  "lastEventName": "APPLICATION_RECEIVED",
  "lastEventResult": "SUCCESS",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextExpectedBefore": "2026-06-15T10:02:00Z",
  "eventsReceived": 1,
  "completedAt": null
}
```

Example completed response — `200 OK`:

```json
{
  "traceId": "trace-123",
  "status": "COMPLETED",
  "lastEventId": "evt-002",
  "lastEventName": "RULES_EVALUATED",
  "lastEventResult": "SUCCESS",
  "nextExpectedEvent": null,
  "nextExpectedBefore": null,
  "eventsReceived": 2,
  "completedAt": "2026-06-15T10:01:00Z"
}
```

### Error response

Errors use one consistent contract:

```json
{
  "code": "CONFLICT",
  "message": "Unexpected event. Expected RULES_EVALUATED",
  "timestamp": "2026-06-15T10:00:30Z"
}
```

Stable error codes are:

|        Code        | HTTP status |                            Meaning                            |
|--------------------|------------:|---------------------------------------------------------------|
| `VALIDATION_ERROR` |         400 | A valid JSON request violates field or cross-field validation |
| `INVALID_REQUEST`  |         400 | Malformed JSON or incompatible JSON value                     |
| `NOT_FOUND`        |         404 | The requested trace does not exist                            |
| `CONFLICT`         |         409 | Duplicate-content or lifecycle conflict                       |

### HTTP response summary

|               Situation               |          Response |
|---------------------------------------|------------------:|
| New event accepted                    |     `201 Created` |
| Exact duplicate accepted idempotently |          `200 OK` |
| Existing trace status                 |          `200 OK` |
| Invalid request                       | `400 Bad Request` |
| Unknown trace                         |   `404 Not Found` |
| Unexpected event                      |    `409 Conflict` |
| Late expected event                   |    `409 Conflict` |
| Conflicting duplicate event ID        |    `409 Conflict` |
| New event after completion            |    `409 Conflict` |

## Running the Project

Detailed environment notes are also available in [SETUP.md](SETUP.md).

### Prerequisites

* Java 21;
* Docker or Docker Desktop;
* the included Maven wrapper;
* Hurl 8.0.0 or later for end-to-end tests.

### Configure PostgreSQL

Create the local Docker environment file:

```bash
cp docker/example.env docker/.env
```

The default application datasource is:

```text
jdbc:postgresql://localhost:5432/clarops_challenge
username: clarops
password: CHANGE_ME
```

The corresponding values in `docker/.env` must match
`src/main/resources/application.yaml`.

### Start the application

Spring Boot can start the configured Docker Compose stack automatically:

```bash
./mvnw spring-boot:run
```

Verify the application and database connection:

```bash
curl http://localhost:8080/api/health
```

Expected response:

```text
clarops sr engineer challenge
```

### Start PostgreSQL manually

When automatic Docker Compose startup is unavailable:

```bash
docker compose -f docker/docker-compose.yml up -d
docker compose -f docker/docker-compose.yml ps
```

Then start the application:

```bash
./mvnw spring-boot:run
```

### Reset the database

The initialization SQL runs only when PostgreSQL creates a new data volume.

```bash
docker compose -f docker/docker-compose.yml down -v
docker compose -f docker/docker-compose.yml up -d
```

This is destructive to the local challenge database.

## Unit Tests

Run the complete unit suite:

```bash
./mvnw -q test
```

Current verified result:

```text
41 tests, 0 failures, 0 errors
```

The focused tests cover:

* status calculation and exact expiration boundaries;
* first-event trace creation;
* normal trace transitions;
* exact and conflicting duplicates;
* metadata normalization and property-order independence;
* unexpected, late, and post-completion rejection;
* optimistic-lock recovery behavior;
* concurrent event-insertion recovery behavior;
* bounded concurrent trace-creation retry;
* transaction commit and rollback ordering;
* single acceptance-timestamp reuse.

The concurrency-recovery tests use mocked repositories and a mocked transaction manager. They verify
service behavior but are not true concurrent PostgreSQL integration tests.

Run formatting and the full Maven verification lifecycle:

```bash
./mvnw clean spotless:apply verify
```

## Hurl End-to-End Tests

The Hurl suite validates only the public HTTP API. It does not query PostgreSQL directly.

Required files:

```text
hurl/started-flow.hurl
hurl/waiting-other-event-flow.hurl
hurl/completed-flow.hurl
hurl/ttl-expired-flow.hurl
```

Coverage includes:

* `STARTED`;
* `WAITING_OTHER_EVENT`;
* `COMPLETED`;
* `TTL_EXPIRED_FOR_EVENT`;
* exact duplicate idempotency;
* conflicting duplicate rejection;
* unexpected-event rejection;
* late-event rejection;
* event-after-completion rejection;
* rejected requests leaving public trace state unchanged.

With PostgreSQL and the application running, execute:

```bash
RUN_ID="hurl-$(date +%s)"

hurl --test \
  --error-format long \
  --variable base_url=http://localhost:8080/api \
  --variable run_id="$RUN_ID" \
  hurl/started-flow.hurl \
  hurl/waiting-other-event-flow.hurl \
  hurl/completed-flow.hurl \
  hurl/ttl-expired-flow.hurl
```

The expiration scenario uses a two-second TTL and a Hurl request delay of `3000ms`. The other flows
use long TTL values to avoid timing flakiness.

Current verified result:

```text
4 files
20 requests
0 failures
```

The complete suite was also executed a second time with a different `run_id`, confirming that the
scenarios are isolated from prior test data.

Task 12 repeats this suite after resetting the PostgreSQL volume.

## Known Limitations

* Expiration is evaluated only when trace status is queried; there is no proactive alert delivery.
* The service tracks one current expected event per trace rather than arbitrary workflow graphs.
* There is no authentication, authorization, or tenant isolation.
* There is no public event-history endpoint or pagination.
* The SQL initialization script is suitable for the challenge but is not a production migration
  strategy.
* Concurrency-recovery unit tests do not execute real simultaneous PostgreSQL transactions.
* Concurrent trace creation is retried at most once.
* Hurl expiration validation necessarily uses wall-clock delay and does not test the exact boundary;
  the exact boundary is covered deterministically by unit tests.
* Error timestamps currently use direct system time rather than the watchdog's injected `Clock`.
* The service assumes a single PostgreSQL database as the source of truth and does not publish
  accepted events through an outbox or message broker.

## Possible Production Improvements

With more time, the design could be extended with:

* Flyway or Liquibase migrations and backward-compatible schema evolution;
* Testcontainers-based PostgreSQL integration tests, including true concurrent race scenarios;
* an outbox pattern for reliably publishing accepted events;
* scheduled deadline scanning and alert delivery when proactive notification is required;
* a deadline index if pending traces must be scanned rather than queried by ID;
* authentication, authorization, and tenant-aware identifiers;
* metrics, structured logging, distributed tracing, and operational dashboards;
* configurable retry and conflict policies;
* a public, paginated event-history endpoint;
* archived or partitioned event-history storage for long retention periods;
* canonical payload fingerprints as an optimization while retaining logical-field comparison as the
  correctness source;
* rate limiting and request-size limits;
* richer flow definitions when multiple dynamic expectations or branching workflows are required.

## Project Documentation

* [SETUP.md](SETUP.md) — environment and startup details.
* [TASKS.md](TASKS.md) — scoped implementation plan and completion status.
* [AI_USAGE.md](AI_USAGE.md) — AI tools, prompts, accepted and rejected suggestions, corrections, and
  review records.

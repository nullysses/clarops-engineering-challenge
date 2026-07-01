# First non-final event without another expectation becomes STARTED.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-started-event-1-{{run_id}}",
  "traceId": "task10-started-trace-{{run_id}}",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "country": "MX",
    "entityId": "task10-started-entity-{{run_id}}"
  }
}
HTTP 201
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-started-event-1-{{run_id}}"
jsonpath "$.traceId" == "task10-started-trace-{{run_id}}"
jsonpath "$.duplicate" == false

# The trace reports STARTED with the accepted event facts.
GET {{base_url}}/traces/task10-started-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-started-trace-{{run_id}}"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventId" == "task10-started-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "APPLICATION_RECEIVED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == null
jsonpath "$.nextExpectedBefore" == null
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# The same logical event is idempotent even when metadata object fields are reordered.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-started-event-1-{{run_id}}",
  "traceId": "task10-started-trace-{{run_id}}",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "entityId": "task10-started-entity-{{run_id}}",
    "country": "MX"
  }
}
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-started-event-1-{{run_id}}"
jsonpath "$.traceId" == "task10-started-trace-{{run_id}}"
jsonpath "$.duplicate" == true

# The duplicate does not change public trace state.
GET {{base_url}}/traces/task10-started-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-started-trace-{{run_id}}"
jsonpath "$.status" == "STARTED"
jsonpath "$.lastEventId" == "task10-started-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "APPLICATION_RECEIVED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == null
jsonpath "$.nextExpectedBefore" == null
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# Reusing the event ID with different logical content is rejected.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-started-event-1-{{run_id}}",
  "traceId": "task10-started-trace-{{run_id}}",
  "eventName": "APPLICATION_REVIEWED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:00:00Z",
  "metadata": {
    "country": "MX",
    "entityId": "task10-started-entity-{{run_id}}"
  }
}
HTTP 409
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.code" == "CONFLICT"
jsonpath "$.message" == "Event ID already exists with different logical content"
jsonpath "$.timestamp" matches /^.+Z$/

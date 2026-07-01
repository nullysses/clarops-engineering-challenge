# First event defines the next expected event and enters WAITING_OTHER_EVENT.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-waiting-event-1-{{run_id}}",
  "traceId": "task10-waiting-trace-{{run_id}}",
  "eventName": "APPLICATION_RECEIVED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:10:00Z",
  "nextExpectedEvent": "RULES_EVALUATED",
  "nextEventTtlSeconds": 120,
  "metadata": {
    "entityId": "task10-waiting-entity-{{run_id}}",
    "stage": "application"
  }
}
HTTP 201
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-waiting-event-1-{{run_id}}"
jsonpath "$.traceId" == "task10-waiting-trace-{{run_id}}"
jsonpath "$.duplicate" == false

# Before the long TTL expires, status remains waiting for RULES_EVALUATED.
GET {{base_url}}/traces/task10-waiting-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-waiting-trace-{{run_id}}"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.lastEventId" == "task10-waiting-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "APPLICATION_RECEIVED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# A different event name is rejected while the trace waits for RULES_EVALUATED.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-waiting-event-unexpected-{{run_id}}",
  "traceId": "task10-waiting-trace-{{run_id}}",
  "eventName": "PAYMENT_CONFIRMED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:11:00Z",
  "metadata": {
    "entityId": "task10-waiting-entity-{{run_id}}"
  }
}
HTTP 409
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.code" == "CONFLICT"
jsonpath "$.message" == "Unexpected event. Expected RULES_EVALUATED"
jsonpath "$.timestamp" matches /^.+Z$/

# The rejected event does not mutate public trace state.
GET {{base_url}}/traces/task10-waiting-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-waiting-trace-{{run_id}}"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.lastEventId" == "task10-waiting-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "APPLICATION_RECEIVED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "RULES_EVALUATED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

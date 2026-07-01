# First event starts a flow waiting for PAYMENT_CONFIRMED.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-completed-event-1-{{run_id}}",
  "traceId": "task10-completed-trace-{{run_id}}",
  "eventName": "PAYMENT_REQUESTED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:20:00Z",
  "nextExpectedEvent": "PAYMENT_CONFIRMED",
  "nextEventTtlSeconds": 120,
  "metadata": {
    "invoiceId": "task10-invoice-{{run_id}}"
  }
}
HTTP 201
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-completed-event-1-{{run_id}}"
jsonpath "$.traceId" == "task10-completed-trace-{{run_id}}"
jsonpath "$.duplicate" == false

# The trace is waiting for the expected payment confirmation.
GET {{base_url}}/traces/task10-completed-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-completed-trace-{{run_id}}"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.lastEventId" == "task10-completed-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "PAYMENT_REQUESTED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "PAYMENT_CONFIRMED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# The expected event completes the trace.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-completed-event-2-{{run_id}}",
  "traceId": "task10-completed-trace-{{run_id}}",
  "eventName": "PAYMENT_CONFIRMED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:21:00Z",
  "finalEvent": true,
  "metadata": {
    "invoiceId": "task10-invoice-{{run_id}}",
    "provider": "stripe"
  }
}
HTTP 201
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-completed-event-2-{{run_id}}"
jsonpath "$.traceId" == "task10-completed-trace-{{run_id}}"
jsonpath "$.duplicate" == false

# Completion clears the pending expectation and records final-event facts.
GET {{base_url}}/traces/task10-completed-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-completed-trace-{{run_id}}"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventId" == "task10-completed-event-2-{{run_id}}"
jsonpath "$.lastEventName" == "PAYMENT_CONFIRMED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == null
jsonpath "$.nextExpectedBefore" == null
jsonpath "$.eventsReceived" == 2
jsonpath "$.completedAt" matches /^.+Z$/

# A new non-duplicate event after completion is rejected.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-completed-event-after-{{run_id}}",
  "traceId": "task10-completed-trace-{{run_id}}",
  "eventName": "RECEIPT_SENT",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:22:00Z",
  "metadata": {
    "invoiceId": "task10-invoice-{{run_id}}"
  }
}
HTTP 409
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.code" == "CONFLICT"
jsonpath "$.message" == "Trace is already completed"
jsonpath "$.timestamp" matches /^.+Z$/

# The rejected event does not mutate the completed trace.
GET {{base_url}}/traces/task10-completed-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-completed-trace-{{run_id}}"
jsonpath "$.status" == "COMPLETED"
jsonpath "$.lastEventId" == "task10-completed-event-2-{{run_id}}"
jsonpath "$.lastEventName" == "PAYMENT_CONFIRMED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == null
jsonpath "$.nextExpectedBefore" == null
jsonpath "$.eventsReceived" == 2
jsonpath "$.completedAt" matches /^.+Z$/

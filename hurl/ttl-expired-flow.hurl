# First event defines a short TTL for DELIVERY_CONFIRMED.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-expired-event-1-{{run_id}}",
  "traceId": "task10-expired-trace-{{run_id}}",
  "eventName": "ORDER_SHIPPED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:30:00Z",
  "nextExpectedEvent": "DELIVERY_CONFIRMED",
  "nextEventTtlSeconds": 2,
  "metadata": {
    "shipmentId": "task10-shipment-{{run_id}}"
  }
}
HTTP 201
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.eventId" == "task10-expired-event-1-{{run_id}}"
jsonpath "$.traceId" == "task10-expired-trace-{{run_id}}"
jsonpath "$.duplicate" == false

# Immediately after acceptance, the trace is waiting.
GET {{base_url}}/traces/task10-expired-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-expired-trace-{{run_id}}"
jsonpath "$.status" == "WAITING_OTHER_EVENT"
jsonpath "$.lastEventId" == "task10-expired-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "ORDER_SHIPPED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "DELIVERY_CONFIRMED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# Wait longer than the two-second TTL before querying status again.
GET {{base_url}}/traces/task10-expired-trace-{{run_id}}/status
[Options]
delay: 3000ms
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-expired-trace-{{run_id}}"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.lastEventId" == "task10-expired-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "ORDER_SHIPPED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "DELIVERY_CONFIRMED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

# The formerly expected event is rejected after the deadline.
POST {{base_url}}/events
Content-Type: application/json
{
  "eventId": "task10-expired-event-late-{{run_id}}",
  "traceId": "task10-expired-trace-{{run_id}}",
  "eventName": "DELIVERY_CONFIRMED",
  "result": "SUCCESS",
  "occurredAt": "2026-06-15T10:31:00Z",
  "finalEvent": true,
  "metadata": {
    "shipmentId": "task10-shipment-{{run_id}}"
  }
}
HTTP 409
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.code" == "CONFLICT"
jsonpath "$.message" == "Expected event arrived after the TTL deadline"
jsonpath "$.timestamp" matches /^.+Z$/

# The rejected late event does not mutate the expired trace.
GET {{base_url}}/traces/task10-expired-trace-{{run_id}}/status
HTTP 200
[Asserts]
header "Content-Type" contains "application/json"
jsonpath "$.traceId" == "task10-expired-trace-{{run_id}}"
jsonpath "$.status" == "TTL_EXPIRED_FOR_EVENT"
jsonpath "$.lastEventId" == "task10-expired-event-1-{{run_id}}"
jsonpath "$.lastEventName" == "ORDER_SHIPPED"
jsonpath "$.lastEventResult" == "SUCCESS"
jsonpath "$.nextExpectedEvent" == "DELIVERY_CONFIRMED"
jsonpath "$.nextExpectedBefore" matches /^.+Z$/
jsonpath "$.eventsReceived" == 1
jsonpath "$.completedAt" == null

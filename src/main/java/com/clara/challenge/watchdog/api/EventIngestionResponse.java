package com.clara.challenge.watchdog.api;

public record EventIngestionResponse(String eventId, String traceId, boolean duplicate) {}

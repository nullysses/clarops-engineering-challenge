package com.clara.challenge.watchdog.api;

import java.time.Instant;

public record ApiErrorResponse(String code, String message, Instant timestamp) {}

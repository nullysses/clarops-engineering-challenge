package com.clara.challenge.watchdog.domain;

public enum TraceStatus {
  STARTED,
  WAITING_OTHER_EVENT,
  TTL_EXPIRED_FOR_EVENT,
  COMPLETED
}

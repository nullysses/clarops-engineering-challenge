package com.clara.challenge.watchdog.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceEventRepository extends JpaRepository<TraceEvent, String> {

  List<TraceEvent> findByTraceIdOrderByReceivedAtAsc(String traceId);
}

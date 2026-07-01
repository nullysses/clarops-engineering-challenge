package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.service.TraceStatusService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TraceStatusController {

  private final TraceStatusService traceStatusService;

  public TraceStatusController(TraceStatusService traceStatusService) {
    this.traceStatusService = traceStatusService;
  }

  @GetMapping("/traces/{traceId}/status")
  public TraceStatusResponse getStatus(@PathVariable String traceId) {
    return traceStatusService.getStatus(traceId);
  }
}

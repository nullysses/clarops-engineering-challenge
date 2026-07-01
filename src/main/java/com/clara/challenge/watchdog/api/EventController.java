package com.clara.challenge.watchdog.api;

import com.clara.challenge.watchdog.service.EventIngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EventController {

  private final EventIngestionService eventIngestionService;

  public EventController(EventIngestionService eventIngestionService) {
    this.eventIngestionService = eventIngestionService;
  }

  @PostMapping("/events")
  public ResponseEntity<EventIngestionResponse> ingest(@Valid @RequestBody EventRequest request) {
    EventIngestionResponse response = eventIngestionService.ingest(request);
    HttpStatus status = response.duplicate() ? HttpStatus.OK : HttpStatus.CREATED;
    return ResponseEntity.status(status).body(response);
  }
}

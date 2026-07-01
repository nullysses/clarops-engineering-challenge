package com.clara.challenge.watchdog.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TraceStateRepository extends JpaRepository<TraceState, String> {}

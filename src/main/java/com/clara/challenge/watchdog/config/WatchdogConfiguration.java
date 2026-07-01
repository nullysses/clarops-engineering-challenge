package com.clara.challenge.watchdog.config;

import com.clara.challenge.watchdog.domain.TraceStatusCalculator;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WatchdogConfiguration {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }

  @Bean
  public TraceStatusCalculator traceStatusCalculator() {
    return new TraceStatusCalculator();
  }
}

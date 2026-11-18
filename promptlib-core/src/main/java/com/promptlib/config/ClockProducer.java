package com.promptlib.config;

import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.time.Clock;

/** Time is injected so tests can pin it. */
public class ClockProducer {

    @Produces
    @Singleton
    public Clock utcClock() {
        return Clock.systemUTC();
    }
}

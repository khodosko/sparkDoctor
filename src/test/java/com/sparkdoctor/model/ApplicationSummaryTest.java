package com.sparkdoctor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class ApplicationSummaryTest {
    @Test
    void durationMillisReturnsDifferenceBetweenEndAndStart() {
        ApplicationSummary summary = new ApplicationSummary("app-1", "daily_job", 1000L, 2500L);

        assertEquals(1500L, summary.durationMillis().orElseThrow());
    }

    @Test
    void durationMillisIsEmptyWhenStartOrEndIsMissing() {
        ApplicationSummary summary = new ApplicationSummary("app-1", "daily_job", 1000L, null);

        assertTrue(summary.durationMillis().isEmpty());
    }
}


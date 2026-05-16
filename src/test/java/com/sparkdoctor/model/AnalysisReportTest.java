package com.sparkdoctor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class AnalysisReportTest {
    @Test
    void buildsInitialAnalysisReportFromApplicationSummary() {
        ApplicationSummary applicationSummary =
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L);

        AnalysisReport report = AnalysisReport.from(applicationSummary);

        assertEquals("app-1", report.application().id());
        assertEquals("daily_job", report.application().name());
        assertEquals(1000L, report.application().startTimeMillis());
        assertEquals(2500L, report.application().endTimeMillis());
        assertEquals(1500L, report.application().durationMillis());
        assertEquals(0, report.summary().jobs());
        assertEquals(0, report.summary().stages());
        assertEquals(0, report.summary().tasks());
        assertEquals(0, report.summary().issuesDetected());
        assertTrue(report.bottlenecks().isEmpty());
        assertTrue(report.recommendations().isEmpty());
    }
}


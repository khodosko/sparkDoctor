package com.sparkdoctor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
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
        assertTrue(report.stages().isEmpty());
        assertTrue(report.bottlenecks().isEmpty());
        assertTrue(report.recommendations().isEmpty());
    }

    @Test
    void buildsInitialAnalysisReportFromParsedEventLog() {
        ParsedEventLog parsedEventLog = new ParsedEventLog(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 2, 3, 0),
                List.of(new StageAnalysis(4, "read parquet", 12)));

        AnalysisReport report = AnalysisReport.from(parsedEventLog);

        assertEquals(1, report.summary().jobs());
        assertEquals(2, report.summary().stages());
        assertEquals(3, report.summary().tasks());
        assertEquals(0, report.summary().issuesDetected());
        assertEquals(1, report.stages().size());
        assertEquals(4, report.stages().get(0).id());
        assertEquals("read parquet", report.stages().get(0).name());
        assertEquals(12, report.stages().get(0).taskCount());
    }
}

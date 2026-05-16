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
                List.of(new StageAnalysis(4, "read parquet", 12, 2, 1000L, 3000L, 2000L, 7000L, 5000L)),
                List.of(new Bottleneck(
                        "task_duration_skew",
                        "medium",
                        4,
                        "Stage 4 has task duration skew.",
                        java.util.Map.of("skewRatio", 3.0))),
                List.of(new Recommendation(
                        "investigate-task-duration-skew",
                        "medium",
                        "Investigate task duration skew",
                        "Stage 4 has tasks running much longer than the stage average.",
                        "task_duration_skew",
                        4)));

        AnalysisReport report = AnalysisReport.from(parsedEventLog);

        assertEquals(1, report.summary().jobs());
        assertEquals(2, report.summary().stages());
        assertEquals(3, report.summary().tasks());
        assertEquals(1, report.summary().issuesDetected());
        assertEquals(1, report.stages().size());
        assertEquals(4, report.stages().get(0).id());
        assertEquals("read parquet", report.stages().get(0).name());
        assertEquals(12, report.stages().get(0).taskCount());
        assertEquals(2, report.stages().get(0).completedTasks());
        assertEquals(1000L, report.stages().get(0).minTaskDurationMillis());
        assertEquals(3000L, report.stages().get(0).maxTaskDurationMillis());
        assertEquals(2000L, report.stages().get(0).avgTaskDurationMillis());
        assertEquals(7000L, report.stages().get(0).shuffleReadBytes());
        assertEquals(5000L, report.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(1, report.bottlenecks().size());
        assertEquals("task_duration_skew", report.bottlenecks().get(0).type());
        assertEquals(1, report.recommendations().size());
        assertEquals("investigate-task-duration-skew", report.recommendations().get(0).id());
    }
}

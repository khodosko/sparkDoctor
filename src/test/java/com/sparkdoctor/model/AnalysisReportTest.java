package com.sparkdoctor.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

final class AnalysisReportTest {
    @Test
    void buildsInitialAnalysisReportFromApplicationSummary() {
        ApplicationSummary applicationSummary =
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L);

        AnalysisReport report = AnalysisReport.from(applicationSummary);

        assertEquals("1", report.schemaVersion());
        assertEquals("SparkDoctor", report.producer().name());
        assertEquals(com.sparkdoctor.SparkDoctorVersion.current(), report.producer().version());
        assertEquals("app-1", report.application().id());
        assertEquals("daily_job", report.application().name());
        assertEquals(1000L, report.application().startTimeMillis());
        assertEquals(2500L, report.application().endTimeMillis());
        assertEquals(1500L, report.application().durationMillis());
        assertEquals(0, report.summary().jobs());
        assertEquals(0, report.summary().jobsCompleted());
        assertEquals(0, report.summary().jobsFailed());
        assertEquals(0, report.summary().stages());
        assertEquals(0, report.summary().stagesCompleted());
        assertEquals(0, report.summary().stagesFailed());
        assertEquals(0, report.summary().tasks());
        assertEquals(0, report.summary().issuesDetected());
        assertTrue(report.stages().isEmpty());
        assertTrue(report.sqlExecutions().isEmpty());
        assertTrue(report.failedJobs().isEmpty());
        assertTrue(report.failedStages().isEmpty());
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

        assertEquals("1", report.schemaVersion());
        assertEquals(1, report.summary().jobs());
        assertEquals(0, report.summary().jobsCompleted());
        assertEquals(0, report.summary().jobsFailed());
        assertEquals(2, report.summary().stages());
        assertEquals(0, report.summary().stagesCompleted());
        assertEquals(0, report.summary().stagesFailed());
        assertEquals(3, report.summary().tasks());
        assertEquals(1, report.summary().issuesDetected());
        assertEquals(1, report.stages().size());
        assertTrue(report.sqlExecutions().isEmpty());
        assertTrue(report.failedJobs().isEmpty());
        assertTrue(report.failedStages().isEmpty());
        assertEquals(4, report.stages().get(0).id());
        assertEquals("read parquet", report.stages().get(0).name());
        assertEquals(12, report.stages().get(0).taskCount());
        assertEquals(2, report.stages().get(0).completedTasks());
        assertEquals(1000L, report.stages().get(0).minTaskDurationMillis());
        assertEquals(3000L, report.stages().get(0).maxTaskDurationMillis());
        assertEquals(2000L, report.stages().get(0).avgTaskDurationMillis());
        assertEquals(7000L, report.stages().get(0).shuffleReadBytes());
        assertEquals(5000L, report.stages().get(0).maxTaskShuffleReadBytes());
        assertTrue(report.stages().get(0).taskShuffleReadBytes().isEmpty());
        assertEquals(0L, report.stages().get(0).memoryBytesSpilled());
        assertEquals(0L, report.stages().get(0).diskBytesSpilled());
        assertEquals(1, report.bottlenecks().size());
        assertEquals("task_duration_skew", report.bottlenecks().get(0).type());
        assertEquals("bottleneck-1", report.bottlenecks().get(0).instanceId());
        assertEquals(1, report.recommendations().size());
        assertEquals("investigate-task-duration-skew", report.recommendations().get(0).id());
        assertEquals("bottleneck-1", report.recommendations().get(0).relatedBottleneckId());
    }

    @Test
    void rejectsRecommendationWithoutMatchingBottleneck() {
        ApplicationSummary applicationSummary =
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L);
        Recommendation unmatchedRecommendation = new Recommendation(
                "investigate-task-duration-skew",
                "medium",
                "Investigate task duration skew",
                "Review the long-running tasks.",
                "task_duration_skew",
                4);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> AnalysisReport.from(
                        applicationSummary,
                        new AnalysisSummary(0, 0, 0, 0),
                        List.of(),
                        List.of(),
                        List.of(unmatchedRecommendation)));

        assertTrue(exception.getMessage().contains("does not match a bottleneck"));
    }

    @Test
    void keepsMultipleRecommendationsLinkedToOneIdentifiedBottleneck() {
        Bottleneck bottleneck = new Bottleneck(
                "task_duration_skew",
                "medium",
                4,
                "Stage 4 has task duration skew.",
                java.util.Map.of("skewRatio", 5.0),
                "bottleneck-custom");
        Recommendation first = new Recommendation(
                "investigate-task-duration-skew",
                "medium",
                "Investigate task duration skew",
                "Review task inputs.",
                "task_duration_skew",
                4,
                "bottleneck-custom");
        Recommendation second = new Recommendation(
                "review-partitioning",
                "medium",
                "Review partitioning",
                "Review the partition key.",
                "task_duration_skew",
                4,
                "bottleneck-custom");

        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(0, 0, 0, 0),
                List.of(),
                List.of(bottleneck),
                List.of(first, second));

        assertEquals("bottleneck-custom", report.bottlenecks().get(0).instanceId());
        assertEquals(2, report.recommendations().size());
        assertTrue(report.recommendations().stream()
                .allMatch(recommendation ->
                        "bottleneck-custom".equals(recommendation.relatedBottleneckId())));
    }
}

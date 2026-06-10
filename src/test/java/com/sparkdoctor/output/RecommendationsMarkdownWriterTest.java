package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.FailedStage;
import com.sparkdoctor.model.Recommendation;
import com.sparkdoctor.model.StageAnalysis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RecommendationsMarkdownWriterTest {
    @TempDir
    private Path tempDir;

    private final RecommendationsMarkdownWriter writer = new RecommendationsMarkdownWriter();

    @Test
    void writesRecommendationsMarkdownIntoOutputDirectory() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 2, 1),
                List.of(new StageAnalysis(4, "aggregate", 2, 2, 1000L, 3000L, 2000L, 0L, null)),
                List.of(new Bottleneck(
                        "spill_pressure",
                        "medium",
                        4,
                        "Stage 4 has spill pressure.",
                        Map.of(
                                "diskBytesSpilled", 314572800L,
                                "failedTaskAttemptDurationMillis", 30_000L))),
                List.of(new Recommendation(
                        "reduce-spill-pressure",
                        "medium",
                        "Reduce spill pressure",
                        "Stage 4 spilled a significant amount of data during task execution.",
                        "spill_pressure",
                        4)));
        Path outputDirectory = tempDir.resolve("report");

        Path recommendationsPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("recommendations.md"), recommendationsPath);
        assertTrue(Files.exists(recommendationsPath));
        String markdown = Files.readString(recommendationsPath);
        assertTrue(markdown.contains("# SparkDoctor Recommendations"));
        assertTrue(markdown.contains("- Name: daily_job"));
        assertTrue(markdown.contains("- Jobs completed: 0"));
        assertTrue(markdown.contains("- Stages completed: 0"));
        assertTrue(markdown.contains("- Issues detected: 1"));
        assertTrue(markdown.contains("- Severity summary: medium=1"));
        assertTrue(markdown.contains("## Stage Hotspots"));
        assertTrue(markdown.contains("- Stage 4 (aggregate): issues=1"));
        assertTrue(markdown.contains("completedTasks=2"));
        assertTrue(markdown.contains("avgTaskDurationMillis=2000"));
        assertTrue(markdown.contains("maxTaskDurationMillis=3000"));
        assertTrue(markdown.contains("### Reduce spill pressure"));
        assertTrue(markdown.contains("- Severity: medium"));
        assertTrue(markdown.contains("- Stage ID: 4"));
        assertTrue(markdown.contains("- Related bottleneck: spill_pressure"));
        assertTrue(markdown.contains("Evidence:"));
        assertTrue(markdown.contains("- diskBytesSpilled: 314572800 (300 MiB)"));
        assertTrue(markdown.contains("- failedTaskAttemptDurationMillis: 30000 (30 s)"));
        assertTrue(markdown.contains("Stage 4 spilled a significant amount of data"));
    }

    @Test
    void writesNoRecommendationsMessageWhenReportHasNoRecommendations() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L));
        Path outputDirectory = tempDir.resolve("report");

        Path recommendationsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(recommendationsPath);
        assertTrue(markdown.contains("- Severity summary: none"));
        assertTrue(markdown.contains("No recommendations generated."));
    }

    @Test
    void writesApplicationScopeForApplicationLevelRecommendations() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 0, 1, 1, 0, 0, 0, 1),
                List.of(),
                List.of(new Bottleneck(
                        "failed_job",
                        "high",
                        -1,
                        "Job 3 failed.",
                        Map.of("jobId", 3))),
                List.of(new Recommendation(
                        "investigate-failed-job",
                        "high",
                        "Investigate failed Spark job",
                        "Inspect driver logs.",
                        "failed_job",
                        -1)));
        Path outputDirectory = tempDir.resolve("application-report");

        Path recommendationsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(recommendationsPath);
        assertTrue(markdown.contains("- Severity summary: high=1"));
        assertTrue(markdown.contains("- Scope: application"));
        assertTrue(markdown.contains("- Related bottleneck: failed_job"));
        assertTrue(markdown.contains("- jobId: 3"));
    }

    @Test
    void writesFailedStagesFirstInStageHotspots() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 0, 1, 2, 0, 1, 10, 1),
                List.of(
                        new StageAnalysis(1, "slow stage", 8, 8, 1000L, 9000L, 3000L, 0L, null),
                        new StageAnalysis(2, "failed stage", 2, 0, null, null, null, 0L, null)),
                List.of(),
                List.of(new FailedStage(2, "failed stage", "Fetch failed")),
                List.of(new Bottleneck(
                        "failed_stage",
                        "high",
                        2,
                        "Stage 2 failed.",
                        Map.of("stageId", 2))),
                List.of(new Recommendation(
                        "investigate-failed-stage",
                        "high",
                        "Investigate failed Spark stage",
                        "Stage 2 failed.",
                        "failed_stage",
                        2)));
        Path outputDirectory = tempDir.resolve("failed-hotspots-report");

        Path recommendationsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(recommendationsPath);
        int failedStageIndex = markdown.indexOf("- Stage 2 (failed stage): issues=1, failed=true");
        int slowStageIndex = markdown.indexOf("- Stage 1 (slow stage): issues=0");
        assertTrue(failedStageIndex > 0);
        assertTrue(slowStageIndex > failedStageIndex);
    }
}

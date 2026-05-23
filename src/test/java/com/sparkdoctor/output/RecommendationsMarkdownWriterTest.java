package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
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
                        Map.of("diskBytesSpilled", 314572800L))),
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
        assertTrue(markdown.contains("- Issues detected: 1"));
        assertTrue(markdown.contains("### Reduce spill pressure"));
        assertTrue(markdown.contains("- Severity: medium"));
        assertTrue(markdown.contains("- Stage ID: 4"));
        assertTrue(markdown.contains("- Related bottleneck: spill_pressure"));
        assertTrue(markdown.contains("Stage 4 spilled a significant amount of data"));
    }

    @Test
    void writesNoRecommendationsMessageWhenReportHasNoRecommendations() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L));
        Path outputDirectory = tempDir.resolve("report");

        Path recommendationsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(recommendationsPath);
        assertTrue(markdown.contains("No recommendations generated."));
    }
}

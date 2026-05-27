package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.SqlExecution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqlExecutionsMarkdownWriterTest {
    @TempDir
    private Path tempDir;

    private final SqlExecutionsMarkdownWriter writer = new SqlExecutionsMarkdownWriter();

    @Test
    void writesSqlExecutionsMarkdownIntoOutputDirectory() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        3L,
                        3L,
                        "collect",
                        "Dataset.collectToPython",
                        1100L,
                        1700L,
                        600L,
                        "Initial Plan",
                        "Final Plan",
                        "")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        Path outputDirectory = tempDir.resolve("report");

        Path sqlExecutionsPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("sql-executions.md"), sqlExecutionsPath);
        assertTrue(Files.exists(sqlExecutionsPath));
        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(markdown.contains("# SparkDoctor SQL Executions"));
        assertTrue(markdown.contains("- Name: daily_job"));
        assertTrue(markdown.contains("## SQL Execution 3"));
        assertTrue(markdown.contains("- Description: collect"));
        assertTrue(markdown.contains("- Duration millis: 600"));
        assertTrue(markdown.contains("- Status: success"));
        assertTrue(markdown.contains("### Details"));
        assertTrue(markdown.contains("Dataset.collectToPython"));
        assertTrue(markdown.contains("### Initial Physical Plan"));
        assertTrue(markdown.contains("Initial Plan"));
        assertTrue(markdown.contains("### Latest Physical Plan"));
        assertTrue(markdown.contains("Final Plan"));
    }

    @Test
    void writesNoSqlExecutionsMessageWhenReportHasNoSqlExecutions() throws Exception {
        AnalysisReport report = AnalysisReport.from(new ApplicationSummary("app-1", "daily_job", 1000L, 2500L));
        Path outputDirectory = tempDir.resolve("report");

        Path sqlExecutionsPath = writer.write(outputDirectory, report);

        String markdown = Files.readString(sqlExecutionsPath);
        assertTrue(markdown.contains("No SQL executions found."));
    }
}

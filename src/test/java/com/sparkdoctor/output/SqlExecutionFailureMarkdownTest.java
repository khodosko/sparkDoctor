package com.sparkdoctor.output;

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

final class SqlExecutionFailureMarkdownTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesFailedSqlErrorUsingSafeCodeFence() throws Exception {
        SqlExecution failedExecution = new SqlExecution(
                9L,
                null,
                "failed query",
                null,
                100L,
                200L,
                100L,
                null,
                null,
                "Analysis failed near ```unsafe``` text");
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "test", 0L, 200L),
                new AnalysisSummary(0, 0, 0, 0),
                List.of(),
                List.of(failedExecution),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        Path output = new SqlExecutionsMarkdownWriter().write(tempDir, report);

        String markdown = Files.readString(output);
        assertTrue(markdown.contains("- Status: failed"));
        assertTrue(markdown.contains("### Error"));
        assertTrue(markdown.contains(
                "````text\nAnalysis failed near ```unsafe``` text\n````"));
    }
}

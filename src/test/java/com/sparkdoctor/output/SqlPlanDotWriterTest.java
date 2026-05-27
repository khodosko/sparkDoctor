package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.SqlExecution;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SqlPlanDotWriterTest {
    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SqlPlanDotWriter writer = new SqlPlanDotWriter();

    @Test
    void writesDotFileForSqlExecutionPlan() throws Exception {
        JsonNode plan = objectMapper.readTree("""
                {
                  "nodeName": "AdaptiveSparkPlan",
                  "simpleString": "AdaptiveSparkPlan isFinalPlan=true",
                  "children": [
                    {
                      "nodeName": "Exchange",
                      "simpleString": "Exchange hashpartitioning(group_id, 4)",
                      "children": []
                    }
                  ]
                }
                """);
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(
                        7L,
                        7L,
                        "collect",
                        null,
                        1000L,
                        1500L,
                        500L,
                        "Initial Plan",
                        "Final Plan",
                        "",
                        plan,
                        plan)),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        Path outputDirectory = tempDir.resolve("report");

        List<Path> dotPaths = writer.write(outputDirectory, report);

        assertEquals(List.of(outputDirectory.resolve("sql-execution-7.dot")), dotPaths);
        String dot = Files.readString(dotPaths.get(0));
        assertTrue(dot.contains("digraph sql_execution_7"));
        assertTrue(dot.contains("AdaptiveSparkPlan"));
        assertTrue(dot.contains("Exchange"));
        assertTrue(dot.contains("n0 -> n1"));
    }

    @Test
    void skipsSqlExecutionsWithoutStructuredPlanInfo() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 1, 1, 0),
                List.of(),
                List.of(new SqlExecution(7L, 7L, "collect", null, 1000L, 1500L, 500L, null, null, "")),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        List<Path> dotPaths = writer.write(tempDir.resolve("report"), report);

        assertTrue(dotPaths.isEmpty());
    }
}

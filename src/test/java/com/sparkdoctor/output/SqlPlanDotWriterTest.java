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
import java.util.Map;
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
                      "metrics": [
                        {"name": "duration", "accumulatorId": 1, "metricType": "timing"}
                      ],
                      "children": [
                        {
                          "nodeName": "Exchange",
                          "simpleString": "Exchange hashpartitioning(group_id, 4)",
                          "metrics": [
                            {"name": "shuffle bytes written", "accumulatorId": 2, "metricType": "size"},
                            {"name": "shuffle records written", "accumulatorId": 3, "metricType": "sum"},
                            {"name": "records read", "accumulatorId": 4, "metricType": "sum"},
                            {"name": "number of partitions", "accumulatorId": 5, "metricType": "sum"},
                            {"name": "fetch wait time", "accumulatorId": 6, "metricType": "timing"},
                            {"name": "remote bytes read", "accumulatorId": 7, "metricType": "size"}
                          ],
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
                        plan,
                        Map.of(
                                1L, "29",
                                2L, "2048",
                                3L, "1000",
                                4L, "2500",
                                5L, "4",
                                6L, "12",
                                7L, "1024"))),
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
        assertTrue(dot.contains("metrics:\\n- duration: 29 ms"));
        assertTrue(dot.contains("- shuffle bytes written: 2 KiB"));
        assertTrue(dot.contains("- shuffle records written: 1,000"));
        assertTrue(dot.contains("- records read: 2,500"));
        assertTrue(dot.contains("- number of partitions: 4"));
        assertTrue(dot.contains("- fetch wait time: 12 ms"));
        assertTrue(dot.contains("- +1 more"));
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

package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class AnalyzeCommandTest {
    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void analyzeAcceptsExistingEventLogPath() throws Exception {
        Path eventLog = tempDir.resolve("eventlog.json");
        Files.writeString(eventLog, "{}\n");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(eventLog.toString(), "--out", tempDir.resolve("report").toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("SparkDoctor analyzed"));
    }

    @Test
    void analyzeRejectsMissingEventLogPath() {
        Path missingEventLog = tempDir.resolve("missing-eventlog.json");
        StringWriter errorOutput = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setErr(new PrintWriter(errorOutput, true));

        int exitCode = commandLine.execute(missingEventLog.toString());

        assertEquals(2, exitCode);
        assertTrue(errorOutput.toString().contains("Event log path does not exist"));
    }

    @Test
    void analyzeExplainsInvalidEventLogAndRemovesStaleReportArtifacts() throws Exception {
        Path invalidEventLog = tempDir.resolve("invalid-eventlog.json");
        Files.writeString(invalidEventLog, "this is not json\n");
        Path outputDirectory = tempDir.resolve("stale-report");
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("analysis.json"), "{}\n");
        Files.writeString(outputDirectory.resolve("recommendations.md"), "stale\n");
        Files.writeString(outputDirectory.resolve("sql-executions.md"), "stale\n");
        Files.writeString(outputDirectory.resolve("sql-execution-0.dot"), "stale\n");
        Files.writeString(outputDirectory.resolve("keep.txt"), "do not delete\n");
        StringWriter errorOutput = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setErr(new PrintWriter(errorOutput, true));

        int exitCode = commandLine.execute(invalidEventLog.toString(), "--out", outputDirectory.toString());

        assertEquals(1, exitCode);
        assertTrue(errorOutput.toString().contains("Failed to read Spark event log"));
        assertTrue(errorOutput.toString().contains("JSON-lines format"));
        assertTrue(errorOutput.toString().contains(".zstd/.zst"));
        assertTrue(errorOutput.toString().contains("No report artifacts were written."));
        assertTrue(Files.notExists(outputDirectory.resolve("analysis.json")));
        assertTrue(Files.notExists(outputDirectory.resolve("recommendations.md")));
        assertTrue(Files.notExists(outputDirectory.resolve("sql-executions.md")));
        assertTrue(Files.notExists(outputDirectory.resolve("sql-execution-0.dot")));
        assertTrue(Files.exists(outputDirectory.resolve("keep.txt")));
    }

    @Test
    void analyzePrintsParsedApplicationSummary() {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/minimal-eventlog.json",
                "--out",
                tempDir.resolve("report").toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: daily_customer_etl"));
        assertTrue(output.toString().contains("Application ID: app-20260515120000-0001"));
        assertTrue(output.toString().contains("Duration: 2832000 ms"));
        assertTrue(output.toString().contains("Jobs: 1"));
        assertTrue(output.toString().contains("Stages: 2"));
        assertTrue(output.toString().contains("Tasks: 3"));
        assertTrue(output.toString().contains("SQL executions: 0"));
        assertTrue(output.toString().contains("Issues detected: 0"));
        assertTrue(output.toString().contains("Severity summary: none"));
        assertTrue(output.toString().contains("Recommendations: 0"));
    }

    @Test
    void analyzeWritesAnalysisJson() throws Exception {
        Path outputDirectory = tempDir.resolve("report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/minimal-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        Path analysisJson = outputDirectory.resolve("analysis.json");
        Path recommendationsMarkdown = outputDirectory.resolve("recommendations.md");
        Path sqlExecutionsMarkdown = outputDirectory.resolve("sql-executions.md");
        assertTrue(Files.exists(analysisJson));
        assertTrue(Files.exists(recommendationsMarkdown));
        assertTrue(Files.notExists(sqlExecutionsMarkdown));
        assertTrue(output.toString().contains("Analysis JSON: " + analysisJson));
        assertTrue(output.toString().contains("Recommendations Markdown: " + recommendationsMarkdown));

        JsonNode json = objectMapper.readTree(analysisJson.toFile());
        assertEquals("app-20260515120000-0001", json.path("application").path("id").asText());
        assertEquals("daily_customer_etl", json.path("application").path("name").asText());
        assertEquals(2832000L, json.path("application").path("durationMillis").asLong());
        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(2, json.path("summary").path("stages").asInt());
        assertEquals(3, json.path("summary").path("tasks").asInt());
        assertEquals(0, json.path("summary").path("issuesDetected").asInt());
        assertEquals(2, json.path("stages").size());
        assertEquals(0, json.path("sqlExecutions").size());
        assertEquals(0, json.path("stages").get(0).path("id").asInt());
        assertEquals("scan", json.path("stages").get(0).path("name").asText());
        assertEquals(2, json.path("stages").get(0).path("taskCount").asInt());
        assertEquals(2, json.path("stages").get(0).path("completedTasks").asInt());
        assertEquals(2000L, json.path("stages").get(0).path("minTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(0).path("maxTaskDurationMillis").asLong());
        assertEquals(2500L, json.path("stages").get(0).path("avgTaskDurationMillis").asLong());
        assertEquals(8000L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(4000L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("p99TaskShuffleReadBytes").asLong());
        assertEquals(2, json.path("stages").get(0).path("taskShuffleReadBytes").size());
        assertEquals(3000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(0).asLong());
        assertEquals(5000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(1).asLong());
        assertEquals(300L, json.path("stages").get(0).path("memoryBytesSpilled").asLong());
        assertEquals(30L, json.path("stages").get(0).path("diskBytesSpilled").asLong());
        assertEquals(200L, json.path("stages").get(0).path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(20L, json.path("stages").get(0).path("maxTaskDiskBytesSpilled").asLong());
        assertEquals(1, json.path("stages").get(1).path("id").asInt());
        assertEquals("aggregate", json.path("stages").get(1).path("name").asText());
        assertEquals(1, json.path("stages").get(1).path("taskCount").asInt());
        assertEquals(1, json.path("stages").get(1).path("completedTasks").asInt());
        assertEquals(3000L, json.path("stages").get(1).path("minTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(1).path("maxTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(1).path("avgTaskDurationMillis").asLong());
        assertEquals(8000L, json.path("stages").get(1).path("shuffleReadBytes").asLong());
        assertEquals(8000L, json.path("stages").get(1).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(8000L, json.path("stages").get(1).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(8000L, json.path("stages").get(1).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(8000L, json.path("stages").get(1).path("p99TaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("stages").get(1).path("taskShuffleReadBytes").size());
        assertEquals(8000L, json.path("stages").get(1).path("taskShuffleReadBytes").get(0).asLong());
        assertEquals(500L, json.path("stages").get(1).path("memoryBytesSpilled").asLong());
        assertEquals(100L, json.path("stages").get(1).path("diskBytesSpilled").asLong());
        assertEquals(500L, json.path("stages").get(1).path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(100L, json.path("stages").get(1).path("maxTaskDiskBytesSpilled").asLong());
        assertEquals(0, json.path("bottlenecks").size());
        assertEquals(0, json.path("recommendations").size());
        assertTrue(Files.readString(recommendationsMarkdown).contains("No recommendations generated."));
    }

    @Test
    void analyzeWritesSummaryForRealSparkGeneratedFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("real-spark-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/real-spark-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: sparkdoctor_real_fixture"));
        assertTrue(output.toString().contains("Application ID: local-sparkdoctor-fixture"));
        assertTrue(output.toString().contains("Jobs: 4"));
        assertTrue(output.toString().contains("Jobs completed: 4"));
        assertTrue(output.toString().contains("Jobs failed: 0"));
        assertTrue(output.toString().contains("Stages: 4"));
        assertTrue(output.toString().contains("Stages completed: 4"));
        assertTrue(output.toString().contains("Stages failed: 0"));
        assertTrue(output.toString().contains("Tasks: 17"));
        assertTrue(output.toString().contains("SQL executions: 1"));
        assertTrue(output.toString().contains("Issues detected: 0"));
        assertTrue(output.toString().contains("Recommendations: 0"));
        assertTrue(output.toString()
                .contains("SQL Executions Markdown: " + outputDirectory.resolve("sql-executions.md")));
        assertTrue(output.toString().contains("See SQL Executions Markdown for full SQL plan output."));
        assertTrue(output.toString().contains("SQL Plan DOT: " + outputDirectory.resolve("sql-execution-0.dot")));

        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("local-sparkdoctor-fixture", json.path("application").path("id").asText());
        assertEquals("sparkdoctor_real_fixture", json.path("application").path("name").asText());
        assertEquals(4, json.path("summary").path("jobs").asInt());
        assertEquals(4, json.path("summary").path("jobsCompleted").asInt());
        assertEquals(0, json.path("summary").path("jobsFailed").asInt());
        assertEquals(4, json.path("summary").path("stages").asInt());
        assertEquals(4, json.path("summary").path("stagesCompleted").asInt());
        assertEquals(0, json.path("summary").path("stagesFailed").asInt());
        assertEquals(17, json.path("summary").path("tasks").asInt());
        assertEquals(0, json.path("summary").path("issuesDetected").asInt());
        assertEquals(4, json.path("stages").size());
        assertEquals(1, json.path("sqlExecutions").size());
        assertEquals(0, json.path("sqlExecutions").get(0).path("id").asLong());
        assertEquals(960L, json.path("sqlExecutions").get(0).path("durationMillis").asLong());
        assertTrue(json.path("sqlExecutions").get(0).path("physicalPlanDescription").asText().contains("AdaptiveSparkPlan"));
        assertTrue(json.path("sqlExecutions").get(0).path("latestPhysicalPlanDescription").asText().contains("Final Plan"));
        assertTrue(json.path("sqlExecutions").get(0).path("operatorSummaries").size() > 0);
        assertTrue(json.path("sqlExecutions").get(0).path("operatorSummaries").toString().contains("Exchange"));
        assertEquals(0, json.path("failedJobs").size());
        assertEquals(0, json.path("failedStages").size());
        assertEquals(0, json.path("bottlenecks").size());
        assertEquals(0, json.path("recommendations").size());
        String sqlExecutionsMarkdown = Files.readString(outputDirectory.resolve("sql-executions.md"));
        assertTrue(sqlExecutionsMarkdown.contains("# SparkDoctor SQL Executions"));
        assertTrue(sqlExecutionsMarkdown.contains("## SQL Execution 0"));
        assertTrue(sqlExecutionsMarkdown.contains("- DOT graph: `sql-execution-0.dot`"));
        assertTrue(sqlExecutionsMarkdown.contains("### Operator Summary"));
        assertTrue(sqlExecutionsMarkdown.contains("- Exchange: 2"));
        assertTrue(sqlExecutionsMarkdown.contains("### Latest Physical Plan"));
        assertTrue(sqlExecutionsMarkdown.contains("AdaptiveSparkPlan"));
        String sqlPlanDot = Files.readString(outputDirectory.resolve("sql-execution-0.dot"));
        assertTrue(sqlPlanDot.contains("digraph sql_execution_0"));
        assertTrue(sqlPlanDot.contains("AdaptiveSparkPlan"));
        assertTrue(sqlPlanDot.contains("Exchange"));
        assertTrue(Files.readString(outputDirectory.resolve("recommendations.md")).contains("No recommendations generated."));
    }

    @Test
    void analyzeWritesTaskDurationSkewBottleneckForSkewedFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("skewed-report");
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/skewed-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("skewed_customer_etl", json.path("application").path("name").asText());
        assertEquals(10, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("task_duration_skew", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(4, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(10, json.path("bottlenecks").get(0).path("evidence").path("completedTasks").asInt());
        assertEquals(1800L, json.path("bottlenecks").get(0).path("evidence").path("avgTaskDurationMillis").asLong());
        assertEquals(9000L, json.path("bottlenecks").get(0).path("evidence").path("maxTaskDurationMillis").asLong());
        assertEquals(5.0, json.path("bottlenecks").get(0).path("evidence").path("skewRatio").asDouble());
        assertEquals(19000L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(10000L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(1000L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(10000L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(10000L, json.path("stages").get(0).path("p99TaskShuffleReadBytes").asLong());
        assertEquals(10, json.path("stages").get(0).path("taskShuffleReadBytes").size());
        assertEquals(1000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(0).asLong());
        assertEquals(10000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(9).asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("investigate-task-duration-skew", json.path("recommendations").get(0).path("id").asText());
        assertEquals("task_duration_skew", json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(4, json.path("recommendations").get(0).path("stageId").asInt());
    }

    @Test
    void analyzeWritesShufflePartitionSkewBottleneckForShuffleSkewedFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("shuffle-skewed-report");
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/shuffle-skewed-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("shuffle_skewed_customer_etl", json.path("application").path("name").asText());
        assertEquals(10, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(408944640L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(10485760L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("p99TaskShuffleReadBytes").asLong());
        assertEquals(10, json.path("stages").get(0).path("taskShuffleReadBytes").size());
        assertEquals(10485760L, json.path("stages").get(0).path("taskShuffleReadBytes").get(0).asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("taskShuffleReadBytes").get(9).asLong());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("shuffle_partition_skew", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("high", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(8, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(30.0, json.path("bottlenecks").get(0).path("evidence").path("skewRatio").asDouble());
        assertEquals(
                10485760L,
                json.path("bottlenecks").get(0).path("evidence").path("medianTaskShuffleReadBytes").asLong());
        assertEquals(
                314572800L,
                json.path("bottlenecks").get(0).path("evidence").path("maxTaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("mitigate-shuffle-partition-skew", json.path("recommendations").get(0).path("id").asText());
        assertEquals(
                "shuffle_partition_skew",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(8, json.path("recommendations").get(0).path("stageId").asInt());
    }

    @Test
    void analyzeWritesOversizedShufflePartitionsBottleneckForOversizedShuffleFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("oversized-shuffle-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/oversized-shuffle-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Severity summary: medium=1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains(
                "- [medium] oversized_shuffle_partitions (stage 10): Stage 10 has oversized shuffle partitions."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("oversized_shuffle_customer_etl", json.path("application").path("name").asText());
        assertEquals(4, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(1258291200L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("oversized_shuffle_partitions", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(10, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(
                314572800L,
                json.path("bottlenecks").get(0).path("evidence").path("p95TaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("reduce-oversized-shuffle-partitions", json.path("recommendations").get(0).path("id").asText());
        assertEquals(
                "oversized_shuffle_partitions",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Reduce oversized shuffle partitions"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 10"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: oversized_shuffle_partitions"));
    }

    @Test
    void analyzeWritesLowShuffleParallelismBottleneckForLowShuffleParallelismFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("low-shuffle-parallelism-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/low-shuffle-parallelism-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains(
                "- [medium] low_shuffle_parallelism (stage 11): Stage 11 has low shuffle parallelism."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("low_shuffle_parallelism_customer_etl", json.path("application").path("name").asText());
        assertEquals(6, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(1258291200L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(209715200L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(209715200L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(209715200L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("low_shuffle_parallelism", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(11, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(
                1258291200L,
                json.path("bottlenecks").get(0).path("evidence").path("shuffleReadBytes").asLong());
        assertEquals(
                209715200L,
                json.path("bottlenecks").get(0).path("evidence").path("avgTaskShuffleReadBytes").asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("increase-shuffle-parallelism", json.path("recommendations").get(0).path("id").asText());
        assertEquals(
                "low_shuffle_parallelism",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Increase shuffle parallelism"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 11"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: low_shuffle_parallelism"));
    }

    @Test
    void analyzeWritesSpillPressureBottleneckForSpillHeavyFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("spill-heavy-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/spill-heavy-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains("Top bottlenecks:"));
        assertTrue(output.toString().contains("- [medium] spill_pressure (stage 9): Stage 9 has spill pressure."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("spill_heavy_customer_etl", json.path("application").path("name").asText());
        assertEquals(2, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(134217728L, json.path("stages").get(0).path("memoryBytesSpilled").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("diskBytesSpilled").asLong());
        assertEquals(67108864L, json.path("stages").get(0).path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(209715200L, json.path("stages").get(0).path("maxTaskDiskBytesSpilled").asLong());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("spill_pressure", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(9, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(134217728L, json.path("bottlenecks").get(0).path("evidence").path("memoryBytesSpilled").asLong());
        assertEquals(314572800L, json.path("bottlenecks").get(0).path("evidence").path("diskBytesSpilled").asLong());
        assertEquals(
                67108864L,
                json.path("bottlenecks").get(0).path("evidence").path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(
                209715200L,
                json.path("bottlenecks").get(0).path("evidence").path("maxTaskDiskBytesSpilled").asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("reduce-spill-pressure", json.path("recommendations").get(0).path("id").asText());
        assertEquals("spill_pressure", json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(9, json.path("recommendations").get(0).path("stageId").asInt());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Reduce spill pressure"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 9"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: spill_pressure"));
    }

    @Test
    void analyzeWritesMemorySpillSkewBottleneckForMemorySpillSkewFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("memory-spill-skew-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/memory-spill-skew-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains("Top bottlenecks:"));
        assertTrue(output.toString().contains(
                "- [medium] memory_spill_skew (stage 14): Stage 14 has memory spill skew."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("memory_spill_skew_customer_etl", json.path("application").path("name").asText());
        assertEquals(10, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(419430400L, json.path("stages").get(0).path("memoryBytesSpilled").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(10485760L, json.path("stages").get(0).path("medianTaskMemoryBytesSpilled").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("p95TaskMemoryBytesSpilled").asLong());
        assertEquals(314572800L, json.path("stages").get(0).path("p99TaskMemoryBytesSpilled").asLong());
        assertEquals(10, json.path("stages").get(0).path("taskMemoryBytesSpilled").size());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("memory_spill_skew", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(14, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(
                10485760L,
                json.path("bottlenecks").get(0).path("evidence").path("medianTaskMemoryBytesSpilled").asLong());
        assertEquals(
                314572800L,
                json.path("bottlenecks").get(0).path("evidence").path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(30.0, json.path("bottlenecks").get(0).path("evidence").path("skewRatio").asDouble());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("investigate-memory-spill-skew", json.path("recommendations").get(0).path("id").asText());
        assertEquals(
                "memory_spill_skew",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(14, json.path("recommendations").get(0).path("stageId").asInt());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate memory spill skew"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 14"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: memory_spill_skew"));
    }

    @Test
    void analyzeWritesTooManyTinyTasksBottleneckForTinyTasksFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("tiny-tasks-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/tiny-tasks-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: tiny_tasks_customer_etl"));
        assertTrue(output.toString().contains("Tasks: 100"));
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains("Top bottlenecks:"));
        assertTrue(output.toString().contains(
                "- [medium] too_many_tiny_tasks (stage 15): Stage 15 has too many tiny tasks."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("tiny_tasks_customer_etl", json.path("application").path("name").asText());
        assertEquals(100, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(200L, json.path("stages").get(0).path("avgTaskDurationMillis").asLong());
        assertEquals(200L, json.path("stages").get(0).path("medianTaskDurationMillis").asLong());
        assertEquals(200L, json.path("stages").get(0).path("p95TaskDurationMillis").asLong());
        assertEquals(200L, json.path("stages").get(0).path("p99TaskDurationMillis").asLong());
        assertEquals(100, json.path("stages").get(0).path("taskDurationMillis").size());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("too_many_tiny_tasks", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(15, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(
                100,
                json.path("bottlenecks").get(0).path("evidence").path("completedTasks").asInt());
        assertEquals(
                200L,
                json.path("bottlenecks").get(0).path("evidence").path("avgTaskDurationMillis").asLong());
        assertEquals(
                200L,
                json.path("bottlenecks").get(0).path("evidence").path("p95TaskDurationMillis").asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("reduce-tiny-task-overhead", json.path("recommendations").get(0).path("id").asText());
        assertEquals(
                "too_many_tiny_tasks",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(15, json.path("recommendations").get(0).path("stageId").asInt());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Reduce tiny task overhead"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 15"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: too_many_tiny_tasks"));
    }

    @Test
    void analyzeWritesRetryWasteBottleneckForRetryWasteFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("retry-waste-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/retry-waste-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: retry_waste_customer_etl"));
        assertTrue(output.toString().contains("Tasks: 3"));
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains("Top bottlenecks:"));
        assertTrue(output.toString().contains(
                "- [medium] retry_waste (stage 16): Stage 16 has retry waste from failed task attempts."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("retry_waste_customer_etl", json.path("application").path("name").asText());
        assertEquals(3, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(3, json.path("stages").get(0).path("completedTasks").asInt());
        assertEquals(3, json.path("stages").get(0).path("failedTaskAttempts").asInt());
        assertEquals(30_000L, json.path("stages").get(0).path("failedTaskAttemptDurationMillis").asLong());
        assertEquals(2, json.path("stages").get(0).path("failedTaskAttemptReasons").size());
        assertEquals("ExceptionFailure", json.path("stages").get(0).path("failedTaskAttemptReasons").get(0).asText());
        assertEquals("ExecutorLostFailure", json.path("stages").get(0).path("failedTaskAttemptReasons").get(1).asText());
        assertEquals(1, json.path("bottlenecks").size());
        assertEquals("retry_waste", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(16, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(
                3,
                json.path("bottlenecks").get(0).path("evidence").path("failedTaskAttempts").asInt());
        assertEquals(
                30_000L,
                json.path("bottlenecks")
                        .get(0)
                        .path("evidence")
                        .path("failedTaskAttemptDurationMillis")
                        .asLong());
        assertEquals(1, json.path("recommendations").size());
        assertEquals("investigate-retry-waste", json.path("recommendations").get(0).path("id").asText());
        assertEquals("retry_waste", json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(16, json.path("recommendations").get(0).path("stageId").asInt());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate retry waste"));
        assertTrue(recommendationsMarkdown.contains("- Severity: medium"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 16"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: retry_waste"));
    }

    @Test
    void analyzeWritesWorkerImbalanceBottlenecksForWorkerImbalancedFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("worker-imbalanced-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/worker-imbalanced-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: worker_imbalanced_customer_etl"));
        assertTrue(output.toString().contains("Tasks: 10"));
        assertTrue(output.toString().contains("Issues detected: 2"));
        assertTrue(output.toString().contains("Recommendations: 2"));
        assertTrue(output.toString().contains("Top bottlenecks:"));
        assertTrue(output.toString().contains(
                "- [medium] executor_imbalance (stage 17): Stage 17 has executor imbalance."));
        assertTrue(output.toString().contains(
                "- [medium] host_imbalance (stage 17): Stage 17 has host imbalance."));
        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("worker_imbalanced_customer_etl", json.path("application").path("name").asText());
        assertEquals(10, json.path("summary").path("tasks").asInt());
        assertEquals(2, json.path("summary").path("issuesDetected").asInt());
        assertEquals(2, json.path("stages").get(0).path("executorSummaries").size());
        assertEquals("executor-1", json.path("stages").get(0).path("executorSummaries").get(0).path("id").asText());
        assertEquals(8, json.path("stages").get(0).path("executorSummaries").get(0).path("taskCount").asInt());
        assertEquals(
                8000L,
                json.path("stages").get(0).path("executorSummaries").get(0).path("taskDurationMillis").asLong());
        assertEquals(0.8, json.path("stages").get(0).path("executorSummaries").get(0).path("taskShare").asDouble());
        assertEquals(0.8, json.path("stages").get(0).path("executorSummaries").get(0).path("durationShare").asDouble());
        assertEquals(2, json.path("stages").get(0).path("hostSummaries").size());
        assertEquals("host-a", json.path("stages").get(0).path("hostSummaries").get(0).path("id").asText());
        assertEquals("executor_imbalance", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("host_imbalance", json.path("bottlenecks").get(1).path("type").asText());
        assertEquals(
                "executor-1",
                json.path("bottlenecks").get(0).path("evidence").path("executorId").asText());
        assertEquals("host-a", json.path("bottlenecks").get(1).path("evidence").path("host").asText());
        assertEquals("investigate-executor-imbalance", json.path("recommendations").get(0).path("id").asText());
        assertEquals("investigate-host-imbalance", json.path("recommendations").get(1).path("id").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate executor imbalance"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: executor_imbalance"));
        assertTrue(recommendationsMarkdown.contains("### Investigate host imbalance"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: host_imbalance"));
    }

    @Test
    void analyzeWritesSqlManyExchangesBottleneckForSqlManyExchangesFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("sql-many-exchanges-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/sql-many-exchanges-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: sql_many_exchanges_customer_etl"));
        assertTrue(output.toString().contains("SQL executions: 1"));
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains(
                "- [medium] sql_many_exchanges (application): SQL execution 9 has many exchange operators."));
        assertTrue(output.toString()
                .contains("SQL Executions Markdown: " + outputDirectory.resolve("sql-executions.md")));

        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals(1, json.path("sqlExecutions").size());
        assertEquals(9L, json.path("sqlExecutions").get(0).path("id").asLong());
        assertTrue(json.path("sqlExecutions").get(0).path("operatorSummaries").toString().contains("Exchange"));
        assertEquals("sql_many_exchanges", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals(-1, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(9L, json.path("bottlenecks").get(0).path("evidence").path("sqlExecutionId").asLong());
        assertEquals(4, json.path("bottlenecks").get(0).path("evidence").path("exchangeCount").asInt());
        assertEquals(
                "investigate-sql-many-exchanges",
                json.path("recommendations").get(0).path("id").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate SQL plan exchanges"));
        assertTrue(recommendationsMarkdown.contains("- Scope: application"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: sql_many_exchanges"));
        String sqlExecutionsMarkdown = Files.readString(outputDirectory.resolve("sql-executions.md"));
        assertTrue(sqlExecutionsMarkdown.contains("### Operator Summary"));
        assertTrue(sqlExecutionsMarkdown.contains("- Exchange: 4"));
    }

    @Test
    void analyzeWritesSpeculationHeavyBottleneckForSpeculationHeavyFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("speculation-heavy-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/speculation-heavy-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: speculation_heavy_customer_etl"));
        assertTrue(output.toString().contains("Tasks: 10"));
        assertTrue(output.toString().contains("Issues detected: 1"));
        assertTrue(output.toString().contains("Recommendations: 1"));
        assertTrue(output.toString().contains(
                "- [medium] speculation_heavy (stage 18): Stage 18 has heavy speculative execution."));

        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("speculation_heavy_customer_etl", json.path("application").path("name").asText());
        assertEquals(10, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(3, json.path("stages").get(0).path("speculativeTaskAttempts").asInt());
        assertEquals(3000L, json.path("stages").get(0).path("speculativeTaskAttemptDurationMillis").asLong());
        assertEquals(3, json.path("stages").get(0).path("duplicateSuccessfulTaskAttempts").asInt());
        assertEquals("speculation_heavy", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals(18, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(0.3, json.path("bottlenecks").get(0).path("evidence").path("speculativeAttemptShare").asDouble());
        assertEquals(
                "investigate-speculation-heavy-stage",
                json.path("recommendations").get(0).path("id").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate heavy speculative execution"));
        assertTrue(recommendationsMarkdown.contains("- Related bottleneck: speculation_heavy"));
    }

    @Test
    void analyzeWritesFailedJobAndStageBottlenecksForFailedFixture() throws Exception {
        Path outputDirectory = tempDir.resolve("failed-report");
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/failed-eventlog.json",
                "--out",
                outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("Application: failed_customer_etl"));
        assertTrue(output.toString().contains("Jobs failed: 1"));
        assertTrue(output.toString().contains("Stages failed: 1"));
        assertTrue(output.toString().contains("Issues detected: 2"));
        assertTrue(output.toString().contains("Recommendations: 2"));
        assertTrue(output.toString().contains("- [high] failed_job (application): Job 12 failed."));
        assertTrue(output.toString().contains("- [high] failed_stage (stage 13): Stage 13 failed."));

        JsonNode json = objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
        assertEquals("failed_customer_etl", json.path("application").path("name").asText());
        assertEquals(1, json.path("summary").path("jobsFailed").asInt());
        assertEquals(1, json.path("summary").path("stagesFailed").asInt());
        assertEquals(2, json.path("summary").path("issuesDetected").asInt());
        assertEquals(1, json.path("failedJobs").size());
        assertEquals(12, json.path("failedJobs").get(0).path("id").asInt());
        assertEquals("JobFailed", json.path("failedJobs").get(0).path("result").asText());
        assertEquals(1, json.path("failedStages").size());
        assertEquals(13, json.path("failedStages").get(0).path("id").asInt());
        assertEquals(
                "Fetch failed: executor lost during shuffle read",
                json.path("failedStages").get(0).path("failureReason").asText());
        assertEquals(2, json.path("failedStages").get(0).path("failedTaskAttempts").asInt());
        assertEquals(4500L, json.path("failedStages").get(0).path("failedTaskAttemptDurationMillis").asLong());
        assertEquals(2, json.path("failedStages").get(0).path("failedTaskAttemptReasons").size());
        assertEquals("FetchFailed", json.path("failedStages").get(0).path("failedTaskAttemptReasons").get(0).asText());
        assertEquals("failed_job", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("failed_stage", json.path("bottlenecks").get(1).path("type").asText());
        assertEquals(2, json.path("bottlenecks").get(1).path("evidence").path("failedTaskAttempts").asInt());
        assertEquals(
                "ExecutorLostFailure",
                json.path("bottlenecks").get(1).path("evidence").path("failedTaskAttemptReasons").get(1).asText());
        assertEquals("investigate-failed-job", json.path("recommendations").get(0).path("id").asText());
        assertEquals("investigate-failed-stage", json.path("recommendations").get(1).path("id").asText());

        String recommendationsMarkdown = Files.readString(outputDirectory.resolve("recommendations.md"));
        assertTrue(recommendationsMarkdown.contains("### Investigate failed Spark job"));
        assertTrue(recommendationsMarkdown.contains("- Scope: application"));
        assertTrue(recommendationsMarkdown.contains("### Investigate failed Spark stage"));
        assertTrue(recommendationsMarkdown.contains("- Stage ID: 13"));
        assertTrue(recommendationsMarkdown.contains("Failure reason: Fetch failed: executor lost during shuffle read."));
        assertTrue(recommendationsMarkdown.contains("The stage recorded 2 failed task attempts."));
    }

    @Test
    void analyzeReturnsErrorWhenAnalysisJsonCannotBeWritten() throws Exception {
        Path outputPathThatIsAFile = tempDir.resolve("report");
        Files.writeString(outputPathThatIsAFile, "not a directory");
        StringWriter errorOutput = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setErr(new PrintWriter(errorOutput, true));

        int exitCode = commandLine.execute(
                "src/test/resources/fixtures/minimal-eventlog.json",
                "--out",
                outputPathThatIsAFile.toString());

        assertEquals(1, exitCode);
        assertTrue(errorOutput.toString().contains("Failed to write analysis output"));
    }
}

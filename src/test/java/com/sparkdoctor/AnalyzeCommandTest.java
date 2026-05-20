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
        assertTrue(Files.exists(analysisJson));
        assertTrue(output.toString().contains("Analysis JSON: " + analysisJson));

        JsonNode json = objectMapper.readTree(analysisJson.toFile());
        assertEquals("app-20260515120000-0001", json.path("application").path("id").asText());
        assertEquals("daily_customer_etl", json.path("application").path("name").asText());
        assertEquals(2832000L, json.path("application").path("durationMillis").asLong());
        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(2, json.path("summary").path("stages").asInt());
        assertEquals(3, json.path("summary").path("tasks").asInt());
        assertEquals(0, json.path("summary").path("issuesDetected").asInt());
        assertEquals(2, json.path("stages").size());
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
        assertEquals(0, json.path("bottlenecks").size());
        assertEquals(0, json.path("recommendations").size());
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

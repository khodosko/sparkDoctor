package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

final class AnalysisJsonContractTest {
    @TempDir
    private Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void spillFixtureKeepsMachineReadableAnalysisContractStable() throws Exception {
        JsonNode json = analyzeFixture(
                "src/test/resources/fixtures/spill-heavy-eventlog.json",
                tempDir.resolve("spill-report"));

        assertEquals("1", json.path("schemaVersion").asText());
        assertEquals("app-spill-heavy-0001", json.path("application").path("id").asText());
        assertEquals("spill_heavy_customer_etl", json.path("application").path("name").asText());
        assertEquals(10000L, json.path("application").path("durationMillis").asLong());

        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(0, json.path("summary").path("jobsCompleted").asInt());
        assertEquals(0, json.path("summary").path("jobsFailed").asInt());
        assertEquals(1, json.path("summary").path("stages").asInt());
        assertEquals(0, json.path("summary").path("stagesCompleted").asInt());
        assertEquals(0, json.path("summary").path("stagesFailed").asInt());
        assertEquals(2, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());

        JsonNode stage = json.path("stages").get(0);
        assertEquals(9, stage.path("id").asInt());
        assertEquals("spill-heavy aggregate", stage.path("name").asText());
        assertEquals(2, stage.path("completedTasks").asInt());
        assertEquals(2000L, stage.path("shuffleReadBytes").asLong());
        assertEquals(134217728L, stage.path("memoryBytesSpilled").asLong());
        assertEquals(314572800L, stage.path("diskBytesSpilled").asLong());
        assertEquals(67108864L, stage.path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(209715200L, stage.path("maxTaskDiskBytesSpilled").asLong());

        assertEquals(0, json.path("failedJobs").size());
        assertEquals(0, json.path("failedStages").size());
        assertEquals("spill_pressure", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(9, json.path("bottlenecks").get(0).path("stageId").asInt());
    }

    @Test
    void failedFixtureKeepsFailureAndBottleneckContractStable() throws Exception {
        JsonNode json = analyzeFixture(
                "src/test/resources/fixtures/failed-eventlog.json",
                tempDir.resolve("failed-report"));

        assertEquals("1", json.path("schemaVersion").asText());
        assertEquals("app-failed-0001", json.path("application").path("id").asText());
        assertEquals("failed_customer_etl", json.path("application").path("name").asText());
        assertEquals(10000L, json.path("application").path("durationMillis").asLong());

        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(0, json.path("summary").path("jobsCompleted").asInt());
        assertEquals(1, json.path("summary").path("jobsFailed").asInt());
        assertEquals(1, json.path("summary").path("stages").asInt());
        assertEquals(0, json.path("summary").path("stagesCompleted").asInt());
        assertEquals(1, json.path("summary").path("stagesFailed").asInt());
        assertEquals(0, json.path("summary").path("tasks").asInt());
        assertEquals(2, json.path("summary").path("issuesDetected").asInt());

        assertEquals(12, json.path("failedJobs").get(0).path("id").asInt());
        assertEquals("JobFailed", json.path("failedJobs").get(0).path("result").asText());

        JsonNode failedStage = json.path("failedStages").get(0);
        assertEquals(13, failedStage.path("id").asInt());
        assertEquals("failed shuffle", failedStage.path("name").asText());
        assertEquals("Fetch failed: executor lost during shuffle read", failedStage.path("failureReason").asText());
        assertEquals(2, failedStage.path("failedTaskAttempts").asInt());
        assertEquals(4500L, failedStage.path("failedTaskAttemptDurationMillis").asLong());
        assertEquals("FetchFailed", failedStage.path("failedTaskAttemptReasons").get(0).asText());
        assertEquals("ExecutorLostFailure", failedStage.path("failedTaskAttemptReasons").get(1).asText());

        assertEquals("high", bottleneckByType(json, "failed_job").path("severity").asText());
        assertEquals("high", bottleneckByType(json, "failed_stage").path("severity").asText());
    }

    private JsonNode analyzeFixture(String fixturePath, Path outputDirectory) throws Exception {
        StringWriter output = new StringWriter();
        CommandLine commandLine = new CommandLine(new AnalyzeCommand());
        commandLine.setOut(new PrintWriter(output, true));

        int exitCode = commandLine.execute(fixturePath, "--out", outputDirectory.toString());

        assertEquals(0, exitCode);
        assertTrue(output.toString().contains("SparkDoctor analyzed " + fixturePath));
        return objectMapper.readTree(outputDirectory.resolve("analysis.json").toFile());
    }

    private JsonNode bottleneckByType(JsonNode json, String type) {
        for (JsonNode bottleneck : json.path("bottlenecks")) {
            if (type.equals(bottleneck.path("type").asText())) {
                return bottleneck;
            }
        }
        throw new AssertionError("Expected bottleneck type " + type);
    }
}

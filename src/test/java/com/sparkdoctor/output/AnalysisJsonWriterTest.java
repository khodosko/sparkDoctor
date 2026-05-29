package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.Recommendation;
import com.sparkdoctor.model.SqlExecution;
import com.sparkdoctor.model.StageAnalysis;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class AnalysisJsonWriterTest {
    @TempDir
    private Path tempDir;

    private final AnalysisJsonWriter writer = new AnalysisJsonWriter();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesAnalysisJsonIntoOutputDirectory() throws Exception {
        AnalysisReport report = AnalysisReport.from(
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L),
                new AnalysisSummary(1, 2, 3, 0),
                List.of(new StageAnalysis(
                        4,
                        "read parquet",
                        12,
                        2,
                        1000L,
                        3000L,
                        2000L,
                        2000L,
                        3000L,
                        3000L,
                        List.of(1000L, 3000L),
                        7000L,
                        5000L,
                        3500L,
                        5000L,
                        5000L,
                        List.of(2000L, 5000L),
                        300L,
                        700L,
                        200L,
                        500L,
                        150L,
                        200L,
                        200L,
                        List.of(100L, 200L),
                        350L,
                        500L,
                        500L,
                        List.of(200L, 500L))),
                List.of(new SqlExecution(
                        8L,
                        8L,
                        "collect",
                        "Dataset.collectToPython",
                        1000L,
                        1800L,
                        800L,
                        "Initial Plan",
                        "Final Plan",
                        "")),
                List.of(),
                List.of(),
                List.of(new Bottleneck(
                        "task_duration_skew",
                        "medium",
                        4,
                        "Stage 4 has task duration skew.",
                        Map.of("skewRatio", 3.0))),
                List.of(new Recommendation(
                        "investigate-task-duration-skew",
                        "medium",
                        "Investigate task duration skew",
                        "Stage 4 has tasks running much longer than the stage average.",
                        "task_duration_skew",
                        4)));
        Path outputDirectory = tempDir.resolve("report");

        Path analysisPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("analysis.json"), analysisPath);
        assertTrue(Files.exists(analysisPath));

        JsonNode json = objectMapper.readTree(analysisPath.toFile());
        assertEquals("app-1", json.path("application").path("id").asText());
        assertEquals("daily_job", json.path("application").path("name").asText());
        assertEquals(1500L, json.path("application").path("durationMillis").asLong());
        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(0, json.path("summary").path("jobsCompleted").asInt());
        assertEquals(0, json.path("summary").path("jobsFailed").asInt());
        assertEquals(2, json.path("summary").path("stages").asInt());
        assertEquals(0, json.path("summary").path("stagesCompleted").asInt());
        assertEquals(0, json.path("summary").path("stagesFailed").asInt());
        assertEquals(3, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(4, json.path("stages").get(0).path("id").asInt());
        assertEquals("read parquet", json.path("stages").get(0).path("name").asText());
        assertEquals(12, json.path("stages").get(0).path("taskCount").asInt());
        assertEquals(2, json.path("stages").get(0).path("completedTasks").asInt());
        assertEquals(1000L, json.path("stages").get(0).path("minTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(0).path("maxTaskDurationMillis").asLong());
        assertEquals(2000L, json.path("stages").get(0).path("avgTaskDurationMillis").asLong());
        assertEquals(2000L, json.path("stages").get(0).path("medianTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(0).path("p95TaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(0).path("p99TaskDurationMillis").asLong());
        assertEquals(2, json.path("stages").get(0).path("taskDurationMillis").size());
        assertEquals(1000L, json.path("stages").get(0).path("taskDurationMillis").get(0).asLong());
        assertEquals(3000L, json.path("stages").get(0).path("taskDurationMillis").get(1).asLong());
        assertEquals(7000L, json.path("stages").get(0).path("shuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("maxTaskShuffleReadBytes").asLong());
        assertEquals(3500L, json.path("stages").get(0).path("medianTaskShuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("p95TaskShuffleReadBytes").asLong());
        assertEquals(5000L, json.path("stages").get(0).path("p99TaskShuffleReadBytes").asLong());
        assertEquals(2, json.path("stages").get(0).path("taskShuffleReadBytes").size());
        assertEquals(2000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(0).asLong());
        assertEquals(5000L, json.path("stages").get(0).path("taskShuffleReadBytes").get(1).asLong());
        assertEquals(300L, json.path("stages").get(0).path("memoryBytesSpilled").asLong());
        assertEquals(700L, json.path("stages").get(0).path("diskBytesSpilled").asLong());
        assertEquals(200L, json.path("stages").get(0).path("maxTaskMemoryBytesSpilled").asLong());
        assertEquals(500L, json.path("stages").get(0).path("maxTaskDiskBytesSpilled").asLong());
        assertEquals(150L, json.path("stages").get(0).path("medianTaskMemoryBytesSpilled").asLong());
        assertEquals(200L, json.path("stages").get(0).path("p95TaskMemoryBytesSpilled").asLong());
        assertEquals(200L, json.path("stages").get(0).path("p99TaskMemoryBytesSpilled").asLong());
        assertEquals(2, json.path("stages").get(0).path("taskMemoryBytesSpilled").size());
        assertEquals(100L, json.path("stages").get(0).path("taskMemoryBytesSpilled").get(0).asLong());
        assertEquals(200L, json.path("stages").get(0).path("taskMemoryBytesSpilled").get(1).asLong());
        assertEquals(350L, json.path("stages").get(0).path("medianTaskDiskBytesSpilled").asLong());
        assertEquals(500L, json.path("stages").get(0).path("p95TaskDiskBytesSpilled").asLong());
        assertEquals(500L, json.path("stages").get(0).path("p99TaskDiskBytesSpilled").asLong());
        assertEquals(2, json.path("stages").get(0).path("taskDiskBytesSpilled").size());
        assertEquals(200L, json.path("stages").get(0).path("taskDiskBytesSpilled").get(0).asLong());
        assertEquals(500L, json.path("stages").get(0).path("taskDiskBytesSpilled").get(1).asLong());
        assertEquals(1, json.path("sqlExecutions").size());
        assertEquals(8L, json.path("sqlExecutions").get(0).path("id").asLong());
        assertEquals("collect", json.path("sqlExecutions").get(0).path("description").asText());
        assertEquals(800L, json.path("sqlExecutions").get(0).path("durationMillis").asLong());
        assertEquals("Initial Plan", json.path("sqlExecutions").get(0).path("physicalPlanDescription").asText());
        assertEquals("Final Plan", json.path("sqlExecutions").get(0).path("latestPhysicalPlanDescription").asText());
        assertEquals(0, json.path("failedJobs").size());
        assertEquals(0, json.path("failedStages").size());
        assertEquals("task_duration_skew", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(4, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(3.0, json.path("bottlenecks").get(0).path("evidence").path("skewRatio").asDouble());
        assertEquals("investigate-task-duration-skew", json.path("recommendations").get(0).path("id").asText());
        assertEquals("medium", json.path("recommendations").get(0).path("severity").asText());
        assertEquals("Investigate task duration skew", json.path("recommendations").get(0).path("title").asText());
        assertEquals(
                "task_duration_skew",
                json.path("recommendations").get(0).path("relatedBottleneckType").asText());
        assertEquals(4, json.path("recommendations").get(0).path("stageId").asInt());
    }
}

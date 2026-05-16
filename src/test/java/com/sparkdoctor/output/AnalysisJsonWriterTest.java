package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
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
                List.of(new StageAnalysis(4, "read parquet", 12, 2, 1000L, 3000L, 2000L)),
                List.of(new Bottleneck(
                        "task_duration_skew",
                        "medium",
                        4,
                        "Stage 4 has task duration skew.",
                        Map.of("skewRatio", 3.0))));
        Path outputDirectory = tempDir.resolve("report");

        Path analysisPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("analysis.json"), analysisPath);
        assertTrue(Files.exists(analysisPath));

        JsonNode json = objectMapper.readTree(analysisPath.toFile());
        assertEquals("app-1", json.path("application").path("id").asText());
        assertEquals("daily_job", json.path("application").path("name").asText());
        assertEquals(1500L, json.path("application").path("durationMillis").asLong());
        assertEquals(1, json.path("summary").path("jobs").asInt());
        assertEquals(2, json.path("summary").path("stages").asInt());
        assertEquals(3, json.path("summary").path("tasks").asInt());
        assertEquals(1, json.path("summary").path("issuesDetected").asInt());
        assertEquals(4, json.path("stages").get(0).path("id").asInt());
        assertEquals("read parquet", json.path("stages").get(0).path("name").asText());
        assertEquals(12, json.path("stages").get(0).path("taskCount").asInt());
        assertEquals(2, json.path("stages").get(0).path("completedTasks").asInt());
        assertEquals(1000L, json.path("stages").get(0).path("minTaskDurationMillis").asLong());
        assertEquals(3000L, json.path("stages").get(0).path("maxTaskDurationMillis").asLong());
        assertEquals(2000L, json.path("stages").get(0).path("avgTaskDurationMillis").asLong());
        assertEquals("task_duration_skew", json.path("bottlenecks").get(0).path("type").asText());
        assertEquals("medium", json.path("bottlenecks").get(0).path("severity").asText());
        assertEquals(4, json.path("bottlenecks").get(0).path("stageId").asInt());
        assertEquals(3.0, json.path("bottlenecks").get(0).path("evidence").path("skewRatio").asDouble());
        assertTrue(json.path("recommendations").isArray());
    }
}

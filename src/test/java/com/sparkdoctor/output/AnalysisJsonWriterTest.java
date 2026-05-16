package com.sparkdoctor.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisReport;
import com.sparkdoctor.model.ApplicationSummary;
import java.nio.file.Files;
import java.nio.file.Path;
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
                new ApplicationSummary("app-1", "daily_job", 1000L, 2500L));
        Path outputDirectory = tempDir.resolve("report");

        Path analysisPath = writer.write(outputDirectory, report);

        assertEquals(outputDirectory.resolve("analysis.json"), analysisPath);
        assertTrue(Files.exists(analysisPath));

        JsonNode json = objectMapper.readTree(analysisPath.toFile());
        assertEquals("app-1", json.path("application").path("id").asText());
        assertEquals("daily_job", json.path("application").path("name").asText());
        assertEquals(1500L, json.path("application").path("durationMillis").asLong());
        assertEquals(0, json.path("summary").path("issuesDetected").asInt());
        assertTrue(json.path("bottlenecks").isArray());
        assertTrue(json.path("recommendations").isArray());
    }
}


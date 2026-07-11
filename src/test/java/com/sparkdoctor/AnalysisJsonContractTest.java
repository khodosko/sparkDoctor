package com.sparkdoctor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

        assertRequiredTopLevelShape(json);
        assertEquals("1", json.get("schemaVersion").textValue());

        JsonNode application = requiredObject(json, "application");
        assertEquals("app-spill-heavy-0001", application.get("id").textValue());
        assertEquals("spill_heavy_customer_etl", application.get("name").textValue());
        assertEquals(10000L, application.get("durationMillis").longValue());

        JsonNode summary = requiredObject(json, "summary");
        assertEquals(1, summary.get("jobs").intValue());
        assertEquals(0, summary.get("jobsCompleted").intValue());
        assertEquals(0, summary.get("jobsFailed").intValue());
        assertEquals(1, summary.get("stages").intValue());
        assertEquals(0, summary.get("stagesCompleted").intValue());
        assertEquals(0, summary.get("stagesFailed").intValue());
        assertEquals(2, summary.get("tasks").intValue());
        assertEquals(1, summary.get("issuesDetected").intValue());

        JsonNode stages = requiredArray(json, "stages");
        assertEquals(1, stages.size());
        JsonNode stage = stages.get(0);
        assertEquals(9, stage.get("id").intValue());
        assertEquals("spill-heavy aggregate", stage.get("name").textValue());
        assertEquals(2, stage.get("completedTasks").intValue());
        assertEquals(2000L, stage.get("shuffleReadBytes").longValue());
        assertEquals(134217728L, stage.get("memoryBytesSpilled").longValue());
        assertEquals(314572800L, stage.get("diskBytesSpilled").longValue());
        assertEquals(67108864L, stage.get("maxTaskMemoryBytesSpilled").longValue());
        assertEquals(209715200L, stage.get("maxTaskDiskBytesSpilled").longValue());

        assertEquals(0, requiredArray(json, "failedJobs").size());
        assertEquals(0, requiredArray(json, "failedStages").size());

        JsonNode bottlenecks = requiredArray(json, "bottlenecks");
        assertEquals(1, bottlenecks.size());
        JsonNode bottleneck = bottlenecks.get(0);
        assertEquals("bottleneck-1", bottleneck.get("instanceId").textValue());
        assertEquals("spill_pressure", bottleneck.get("type").textValue());
        assertEquals("medium", bottleneck.get("severity").textValue());
        assertEquals(9, bottleneck.get("stageId").intValue());
        assertEquals(134217728L, requiredIntegral(bottleneck.get("evidence"), "memoryBytesSpilled").longValue());

        JsonNode recommendations = requiredArray(json, "recommendations");
        assertEquals(1, recommendations.size());
        JsonNode recommendation = recommendations.get(0);
        assertEquals("reduce-spill-pressure", recommendation.get("id").textValue());
        assertEquals("spill_pressure", recommendation.get("relatedBottleneckType").textValue());
        assertEquals("bottleneck-1", recommendation.get("relatedBottleneckId").textValue());
        assertEquals(9, recommendation.get("stageId").intValue());
    }

    @Test
    void failedFixtureKeepsFailureAndBottleneckContractStable() throws Exception {
        JsonNode json = analyzeFixture(
                "src/test/resources/fixtures/failed-eventlog.json",
                tempDir.resolve("failed-report"));

        assertRequiredTopLevelShape(json);
        assertEquals("1", json.get("schemaVersion").textValue());

        JsonNode application = requiredObject(json, "application");
        assertEquals("app-failed-0001", application.get("id").textValue());
        assertEquals("failed_customer_etl", application.get("name").textValue());
        assertEquals(10000L, application.get("durationMillis").longValue());

        JsonNode summary = requiredObject(json, "summary");
        assertEquals(1, summary.get("jobs").intValue());
        assertEquals(0, summary.get("jobsCompleted").intValue());
        assertEquals(1, summary.get("jobsFailed").intValue());
        assertEquals(1, summary.get("stages").intValue());
        assertEquals(0, summary.get("stagesCompleted").intValue());
        assertEquals(1, summary.get("stagesFailed").intValue());
        assertEquals(0, summary.get("tasks").intValue());
        assertEquals(2, summary.get("issuesDetected").intValue());

        JsonNode failedJobs = requiredArray(json, "failedJobs");
        assertEquals(1, failedJobs.size());
        JsonNode failedJob = failedJobs.get(0);
        assertEquals(12, failedJob.get("id").intValue());
        assertEquals("JobFailed", failedJob.get("result").textValue());

        JsonNode failedStages = requiredArray(json, "failedStages");
        assertEquals(1, failedStages.size());
        JsonNode failedStage = failedStages.get(0);
        assertEquals(13, failedStage.get("id").intValue());
        assertEquals("failed shuffle", failedStage.get("name").textValue());
        assertEquals("Fetch failed: executor lost during shuffle read", failedStage.get("failureReason").textValue());
        assertEquals(2, failedStage.get("failedTaskAttempts").intValue());
        assertEquals(4500L, failedStage.get("failedTaskAttemptDurationMillis").longValue());
        assertEquals("FetchFailed", failedStage.get("failedTaskAttemptReasons").get(0).textValue());
        assertEquals("ExecutorLostFailure", failedStage.get("failedTaskAttemptReasons").get(1).textValue());

        JsonNode failedJobBottleneck = bottleneckByType(json, "failed_job");
        JsonNode failedStageBottleneck = bottleneckByType(json, "failed_stage");
        assertEquals("high", failedJobBottleneck.get("severity").textValue());
        assertEquals("high", failedStageBottleneck.get("severity").textValue());
    }

    @Test
    void sqlFixtureKeepsPublicSqlFieldsAndExcludesInternalPlanState() throws Exception {
        JsonNode json = analyzeFixture(
                "src/test/resources/fixtures/real-spark-eventlog.json",
                tempDir.resolve("sql-report"));

        assertRequiredTopLevelShape(json);
        JsonNode sqlExecutions = requiredArray(json, "sqlExecutions");
        assertEquals(1, sqlExecutions.size());

        JsonNode sqlExecution = sqlExecutions.get(0);
        JsonNode operatorSummaries = requiredArray(sqlExecution, "operatorSummaries");

        assertEquals(0L, sqlExecution.get("id").longValue());
        assertEquals(960L, sqlExecution.get("durationMillis").longValue());
        assertTrue(operatorSummaries.size() > 0);
        for (JsonNode operatorSummary : operatorSummaries) {
            requiredText(operatorSummary, "name");
            requiredIntegral(operatorSummary, "count");
        }

        for (String internalField :
                List.of("sparkPlanInfo", "latestSparkPlanInfo", "planRoot", "latestPlanRoot", "sqlMetricValues")) {
            assertFalse(sqlExecution.has(internalField), "Internal SQL field must not be public: " + internalField);
        }
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

    private void assertRequiredTopLevelShape(JsonNode json) {
        assertTrue(json.isObject(), "analysis.json must contain a JSON object");
        requiredText(json, "schemaVersion");
        JsonNode producer = requiredObject(json, "producer");
        assertEquals("SparkDoctor", requiredText(producer, "name").textValue());
        assertEquals(SparkDoctorVersion.current(), requiredText(producer, "version").textValue());
        assertApplicationShape(requiredObject(json, "application"));
        assertSummaryShape(requiredObject(json, "summary"));
        requiredArray(json, "stages").forEach(this::assertStageShape);
        requiredArray(json, "sqlExecutions").forEach(this::assertSqlExecutionShape);
        requiredArray(json, "failedJobs").forEach(this::assertFailedJobShape);
        requiredArray(json, "failedStages").forEach(this::assertFailedStageShape);
        JsonNode bottlenecks = requiredArray(json, "bottlenecks");
        Set<String> bottleneckIds = new HashSet<>();
        for (JsonNode bottleneck : bottlenecks) {
            assertBottleneckShape(bottleneck);
            String instanceId = bottleneck.get("instanceId").textValue();
            assertTrue(bottleneckIds.add(instanceId), "Bottleneck instance IDs must be unique within a report");
        }

        for (JsonNode recommendation : requiredArray(json, "recommendations")) {
            assertRecommendationShape(recommendation);
            assertTrue(
                    bottleneckIds.contains(recommendation.get("relatedBottleneckId").textValue()),
                    "Recommendation must reference a bottleneck in the same report");
        }
    }

    private void assertApplicationShape(JsonNode application) {
        requiredNullableText(application, "id");
        requiredNullableText(application, "name");
        requiredNullableIntegral(application, "startTimeMillis");
        requiredNullableIntegral(application, "endTimeMillis");
        requiredNullableIntegral(application, "durationMillis");
    }

    private void assertSummaryShape(JsonNode summary) {
        for (String field : List.of(
                "jobs",
                "jobsCompleted",
                "jobsFailed",
                "stages",
                "stagesCompleted",
                "stagesFailed",
                "tasks",
                "issuesDetected")) {
            requiredIntegral(summary, field);
        }
    }

    private void assertStageShape(JsonNode stage) {
        requiredIntegral(stage, "id");
        requiredNullableText(stage, "name");
        requiredNullableIntegral(stage, "taskCount");

        for (String field : List.of(
                "completedTasks",
                "failedTaskAttempts",
                "failedTaskAttemptDurationMillis",
                "speculativeTaskAttempts",
                "speculativeTaskAttemptDurationMillis",
                "duplicateSuccessfulTaskAttempts",
                "shuffleReadBytes",
                "memoryBytesSpilled",
                "diskBytesSpilled")) {
            requiredIntegral(stage, field);
        }

        for (String field : List.of(
                "minTaskDurationMillis",
                "maxTaskDurationMillis",
                "avgTaskDurationMillis",
                "medianTaskDurationMillis",
                "p95TaskDurationMillis",
                "p99TaskDurationMillis",
                "maxTaskShuffleReadBytes",
                "medianTaskShuffleReadBytes",
                "p95TaskShuffleReadBytes",
                "p99TaskShuffleReadBytes",
                "maxTaskMemoryBytesSpilled",
                "maxTaskDiskBytesSpilled",
                "medianTaskMemoryBytesSpilled",
                "p95TaskMemoryBytesSpilled",
                "p99TaskMemoryBytesSpilled",
                "medianTaskDiskBytesSpilled",
                "p95TaskDiskBytesSpilled",
                "p99TaskDiskBytesSpilled")) {
            requiredNullableIntegral(stage, field);
        }

        for (String field : List.of(
                "taskDurationMillis",
                "taskShuffleReadBytes",
                "taskMemoryBytesSpilled",
                "taskDiskBytesSpilled")) {
            requiredIntegralArray(stage, field);
        }
        requiredTextArray(stage, "failedTaskAttemptReasons");

        for (String field : List.of("executorSummaries", "hostSummaries")) {
            for (JsonNode workerSummary : requiredArray(stage, field)) {
                requiredText(workerSummary, "id");
                requiredIntegral(workerSummary, "taskCount");
                requiredIntegral(workerSummary, "taskDurationMillis");
                requiredNumber(workerSummary, "taskShare");
                requiredNumber(workerSummary, "durationShare");
            }
        }
    }

    private void assertSqlExecutionShape(JsonNode sqlExecution) {
        requiredIntegral(sqlExecution, "id");
        requiredNullableIntegral(sqlExecution, "rootExecutionId");
        requiredNullableText(sqlExecution, "description");
        requiredNullableText(sqlExecution, "details");
        requiredNullableIntegral(sqlExecution, "startTimeMillis");
        requiredNullableIntegral(sqlExecution, "endTimeMillis");
        requiredNullableIntegral(sqlExecution, "durationMillis");
        requiredNullableText(sqlExecution, "physicalPlanDescription");
        requiredNullableText(sqlExecution, "latestPhysicalPlanDescription");
        requiredNullableText(sqlExecution, "errorMessage");
        for (JsonNode operatorSummary : requiredArray(sqlExecution, "operatorSummaries")) {
            requiredText(operatorSummary, "name");
            requiredIntegral(operatorSummary, "count");
        }
    }

    private void assertFailedJobShape(JsonNode failedJob) {
        requiredIntegral(failedJob, "id");
        requiredNullableText(failedJob, "result");
    }

    private void assertFailedStageShape(JsonNode failedStage) {
        requiredIntegral(failedStage, "id");
        requiredNullableText(failedStage, "name");
        requiredNullableText(failedStage, "failureReason");
        requiredIntegral(failedStage, "failedTaskAttempts");
        requiredIntegral(failedStage, "failedTaskAttemptDurationMillis");
        requiredTextArray(failedStage, "failedTaskAttemptReasons");
    }

    private void assertBottleneckShape(JsonNode bottleneck) {
        requiredText(bottleneck, "instanceId");
        requiredText(bottleneck, "type");
        requiredText(bottleneck, "severity");
        requiredIntegral(bottleneck, "stageId");
        requiredText(bottleneck, "message");
        requiredObject(bottleneck, "evidence");
    }

    private void assertRecommendationShape(JsonNode recommendation) {
        requiredText(recommendation, "id");
        requiredText(recommendation, "severity");
        requiredText(recommendation, "title");
        requiredText(recommendation, "description");
        requiredText(recommendation, "relatedBottleneckType");
        requiredText(recommendation, "relatedBottleneckId");
        requiredIntegral(recommendation, "stageId");
    }

    private JsonNode requiredObject(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isObject(), "Required field must be an object: " + field);
        return value;
    }

    private JsonNode requiredArray(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isArray(), "Required field must be an array: " + field);
        return value;
    }

    private JsonNode requiredText(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isTextual(), "Required field must be a string: " + field);
        return value;
    }

    private JsonNode requiredNullableText(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isNull() || value.isTextual(), "Required field must be null or a string: " + field);
        return value;
    }

    private JsonNode requiredIntegral(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isIntegralNumber(), "Required field must be an integer: " + field);
        return value;
    }

    private JsonNode requiredNullableIntegral(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isNull() || value.isIntegralNumber(), "Required field must be null or an integer: " + field);
        return value;
    }

    private JsonNode requiredNumber(JsonNode object, String field) {
        JsonNode value = requiredField(object, field);
        assertTrue(value.isNumber(), "Required field must be numeric: " + field);
        return value;
    }

    private JsonNode requiredIntegralArray(JsonNode object, String field) {
        JsonNode values = requiredArray(object, field);
        for (JsonNode value : values) {
            assertTrue(value.isIntegralNumber(), "Array values must be integers: " + field);
        }
        return values;
    }

    private JsonNode requiredTextArray(JsonNode object, String field) {
        JsonNode values = requiredArray(object, field);
        for (JsonNode value : values) {
            assertTrue(value.isTextual(), "Array values must be strings: " + field);
        }
        return values;
    }

    private JsonNode requiredField(JsonNode object, String field) {
        assertTrue(object.isObject(), "Parent value must be an object for field: " + field);
        assertTrue(object.has(field), "Missing required field: " + field);
        return object.get(field);
    }

    private JsonNode bottleneckByType(JsonNode json, String type) {
        for (JsonNode bottleneck : requiredArray(json, "bottlenecks")) {
            if (type.equals(requiredText(bottleneck, "type").textValue())) {
                return bottleneck;
            }
        }
        throw new AssertionError("Expected bottleneck type " + type);
    }
}

package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.ParsedEventLog;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SparkEventLogParserTest {
    private final SparkEventLogParser parser = new SparkEventLogParser();

    @Test
    void parsesApplicationStartAndEndEventsFromLines() throws Exception {
        ApplicationSummary summary = parser.parseApplicationSummary(List.of(
                "{\"Event\":\"SparkListenerApplicationStart\",\"App Name\":\"daily_customer_etl\","
                        + "\"App ID\":\"app-20260515120000-0001\",\"Timestamp\":1778846400000}",
                "{\"Event\":\"SparkListenerApplicationEnd\",\"Timestamp\":1778849232000}"));

        assertEquals("app-20260515120000-0001", summary.appId());
        assertEquals("daily_customer_etl", summary.appName());
        assertEquals(1778846400000L, summary.startTimeMillis());
        assertEquals(1778849232000L, summary.endTimeMillis());
        assertEquals(2832000L, summary.durationMillis().orElseThrow());
    }

    @Test
    void parsesJobStageAndTaskCountsFromLines() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":7}",
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":7}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":11}}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":12}}",
                "{\"Event\":\"SparkListenerStageSubmitted\",\"Stage Info\":{\"Stage ID\":12}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1001}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1002}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Task Info\":{\"Task ID\":1002}}"));

        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(2, parsedEventLog.analysisSummary().stages());
        assertEquals(2, parsedEventLog.analysisSummary().tasks());
        assertEquals(0, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(11, parsedEventLog.stages().get(0).id());
        assertEquals(12, parsedEventLog.stages().get(1).id());
    }

    @Test
    void parsesSqlExecutionEventsFromLines() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionStart\","
                        + "\"executionId\":3,\"rootExecutionId\":3,\"description\":\"collect\","
                        + "\"details\":\"Dataset.collectToPython\",\"physicalPlanDescription\":\"Initial Plan\","
                        + "\"sparkPlanInfo\":{\"nodeName\":\"AdaptiveSparkPlan\",\"children\":["
                        + "{\"nodeName\":\"Exchange\",\"children\":["
                        + "{\"nodeName\":\"Range\",\"children\":[]}]}]},"
                        + "\"time\":1000}",
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":7,\"Stage IDs\":[9],"
                        + "\"Properties\":{\"spark.sql.execution.id\":\"3\"}}",
                "{\"Event\":\"SparkListenerStageCompleted\","
                        + "\"Stage Info\":{\"Stage ID\":9,\"Accumulables\":["
                        + "{\"ID\":44,\"Name\":\"shuffle bytes written\",\"Value\":\"2048\","
                        + "\"Metadata\":\"sql\"}]}}",
                "{\"Event\":\"org.apache.spark.sql.execution.ui.SparkListenerSQLAdaptiveExecutionUpdate\","
                        + "\"executionId\":3,\"physicalPlanDescription\":\"Final Plan\","
                        + "\"sparkPlanInfo\":{\"nodeName\":\"AdaptiveSparkPlan\",\"children\":["
                        + "{\"nodeName\":\"Exchange\",\"children\":["
                        + "{\"nodeName\":\"Project\",\"children\":["
                        + "{\"nodeName\":\"Range\",\"children\":[]}]}]}]}}",
                "{\"Event\":\"org.apache.spark.sql.execution.ui.SparkListenerDriverAccumUpdates\","
                        + "\"executionId\":3,\"accumUpdates\":[[55,4]]}",
                "{\"Event\":\"org.apache.spark.sql.execution.ui.SparkListenerSQLExecutionEnd\","
                        + "\"executionId\":3,\"time\":1750,\"errorMessage\":\"\"}"));

        assertEquals(1, parsedEventLog.sqlExecutions().size());
        assertEquals(3L, parsedEventLog.sqlExecutions().get(0).id());
        assertEquals(3L, parsedEventLog.sqlExecutions().get(0).rootExecutionId());
        assertEquals("collect", parsedEventLog.sqlExecutions().get(0).description());
        assertEquals("Dataset.collectToPython", parsedEventLog.sqlExecutions().get(0).details());
        assertEquals(1000L, parsedEventLog.sqlExecutions().get(0).startTimeMillis());
        assertEquals(1750L, parsedEventLog.sqlExecutions().get(0).endTimeMillis());
        assertEquals(750L, parsedEventLog.sqlExecutions().get(0).durationMillis());
        assertEquals("Initial Plan", parsedEventLog.sqlExecutions().get(0).physicalPlanDescription());
        assertEquals("Final Plan", parsedEventLog.sqlExecutions().get(0).latestPhysicalPlanDescription());
        assertEquals("", parsedEventLog.sqlExecutions().get(0).errorMessage());
        assertEquals("AdaptiveSparkPlan", parsedEventLog.sqlExecutions().get(0).operatorSummaries().get(0).name());
        assertEquals(1, parsedEventLog.sqlExecutions().get(0).operatorSummaries().get(0).count());
        assertTrue(parsedEventLog.sqlExecutions().get(0).operatorSummaries().stream()
                .anyMatch(operator -> "Exchange".equals(operator.name()) && operator.count() == 1));
        assertEquals("2048", parsedEventLog.sqlExecutions().get(0).sqlMetricValues().get(44L));
        assertEquals("4", parsedEventLog.sqlExecutions().get(0).sqlMetricValues().get(55L));
    }

    @Test
    void parsesCompletedAndFailedJobAndStageCountsFromLines() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":1}",
                "{\"Event\":\"SparkListenerJobStart\",\"Job ID\":2}",
                "{\"Event\":\"SparkListenerJobEnd\",\"Job ID\":1,\"Job Result\":{\"Result\":\"JobSucceeded\"}}",
                "{\"Event\":\"SparkListenerJobEnd\",\"Job ID\":2,\"Job Result\":{\"Result\":\"JobFailed\"}}",
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":10,\"Stage Name\":\"ok\",\"Number of Tasks\":2}}",
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":11,\"Stage Name\":\"failed\",\"Number of Tasks\":3}}",
                "{\"Event\":\"SparkListenerStageCompleted\","
                        + "\"Stage Info\":{\"Stage ID\":10,\"Stage Name\":\"ok\",\"Number of Tasks\":2}}",
                "{\"Event\":\"SparkListenerStageCompleted\","
                        + "\"Stage Info\":{\"Stage ID\":11,\"Stage Name\":\"failed\",\"Number of Tasks\":3,"
                        + "\"Failure Reason\":\"Fetch failed\"}}"));

        assertEquals(2, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().jobsCompleted());
        assertEquals(1, parsedEventLog.analysisSummary().jobsFailed());
        assertEquals(2, parsedEventLog.analysisSummary().stages());
        assertEquals(1, parsedEventLog.analysisSummary().stagesCompleted());
        assertEquals(1, parsedEventLog.analysisSummary().stagesFailed());
        assertEquals(2, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(2, parsedEventLog.stages().size());
        assertEquals("ok", parsedEventLog.stages().get(0).name());
        assertEquals("failed", parsedEventLog.stages().get(1).name());
        assertEquals(1, parsedEventLog.failedJobs().size());
        assertEquals(2, parsedEventLog.failedJobs().get(0).id());
        assertEquals("JobFailed", parsedEventLog.failedJobs().get(0).result());
        assertEquals(1, parsedEventLog.failedStages().size());
        assertEquals(11, parsedEventLog.failedStages().get(0).id());
        assertEquals("failed", parsedEventLog.failedStages().get(0).name());
        assertEquals("Fetch failed", parsedEventLog.failedStages().get(0).failureReason());
        assertEquals(2, parsedEventLog.bottlenecks().size());
        assertEquals("failed_job", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("failed_stage", parsedEventLog.bottlenecks().get(1).type());
        assertEquals(2, parsedEventLog.recommendations().size());
    }

    @Test
    void parsesStageDetailsFromStageSubmittedEvents() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"read parquet\","
                        + "\"Number of Tasks\":12}}"));

        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(4, parsedEventLog.stages().get(0).id());
        assertEquals("read parquet", parsedEventLog.stages().get(0).name());
        assertEquals(12, parsedEventLog.stages().get(0).taskCount());
        assertEquals(0, parsedEventLog.stages().get(0).completedTasks());
        assertNull(parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertNull(parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertNull(parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(0L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertNull(parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertNull(parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertNull(parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertNull(parsedEventLog.stages().get(0).p99TaskShuffleReadBytes());
        assertEquals(List.of(), parsedEventLog.stages().get(0).taskShuffleReadBytes());
        assertEquals(0L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(0L, parsedEventLog.stages().get(0).diskBytesSpilled());
        assertNull(parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertNull(parsedEventLog.stages().get(0).maxTaskDiskBytesSpilled());
    }

    @Test
    void parsesTaskDurationAggregatesByStage() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"read parquet\","
                        + "\"Number of Tasks\":2}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":4,"
                        + "\"Task Info\":{\"Task ID\":100,\"Launch Time\":1000,\"Finish Time\":2500}}",
                "{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":4,"
                        + "\"Task Info\":{\"Task ID\":101,\"Launch Time\":2000,\"Finish Time\":6000}}"));

        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(2, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(1500L, parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertEquals(4000L, parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertEquals(2750L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(2750L, parsedEventLog.stages().get(0).medianTaskDurationMillis());
        assertEquals(4000L, parsedEventLog.stages().get(0).p95TaskDurationMillis());
        assertEquals(4000L, parsedEventLog.stages().get(0).p99TaskDurationMillis());
        assertEquals(List.of(1500L, 4000L), parsedEventLog.stages().get(0).taskDurationMillis());
    }

    @Test
    void parsesShuffleReadBytesByStage() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"shuffle\","
                        + "\"Number of Tasks\":2}}",
                taskEndWithShuffleRead(4, 100, 0, 1000, 1000, 2000),
                taskEndWithShuffleRead(4, 101, 0, 1000, 4000, 1000)));

        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(8000L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(4000L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).p99TaskShuffleReadBytes());
        assertEquals(List.of(3000L, 5000L), parsedEventLog.stages().get(0).taskShuffleReadBytes());
    }

    @Test
    void parsesSpillBytesByStage() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"spill\","
                        + "\"Number of Tasks\":2}}",
                taskEndWithSpill(4, 100, 0, 1000, 1000, 500),
                taskEndWithSpill(4, 101, 0, 1000, 4000, 3000)));

        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(5000L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(3500L, parsedEventLog.stages().get(0).diskBytesSpilled());
        assertEquals(4000L, parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertEquals(3000L, parsedEventLog.stages().get(0).maxTaskDiskBytesSpilled());
        assertEquals(2500L, parsedEventLog.stages().get(0).medianTaskMemoryBytesSpilled());
        assertEquals(4000L, parsedEventLog.stages().get(0).p95TaskMemoryBytesSpilled());
        assertEquals(4000L, parsedEventLog.stages().get(0).p99TaskMemoryBytesSpilled());
        assertEquals(List.of(1000L, 4000L), parsedEventLog.stages().get(0).taskMemoryBytesSpilled());
        assertEquals(1750L, parsedEventLog.stages().get(0).medianTaskDiskBytesSpilled());
        assertEquals(3000L, parsedEventLog.stages().get(0).p95TaskDiskBytesSpilled());
        assertEquals(3000L, parsedEventLog.stages().get(0).p99TaskDiskBytesSpilled());
        assertEquals(List.of(500L, 3000L), parsedEventLog.stages().get(0).taskDiskBytesSpilled());
    }

    @Test
    void aggregatesOnlySuccessfulTaskAttemptsByStageAttemptAndTaskIndex() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Attempt ID\":0,\"Stage Name\":\"retry\","
                        + "\"Number of Tasks\":2}}",
                taskEndAttempt(4, 0, 100, 0, false, 0, 10000, 9000, 9000, 9000, 9000),
                taskEndAttempt(4, 0, 101, 0, true, 0, 1000, 1000, 100, 10, 1),
                taskEndAttempt(4, 0, 102, 1, true, 0, 2000, 2000, 200, 20, 2)));

        assertEquals(2, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(2, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(1000L, parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertEquals(2000L, parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertEquals(1500L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(3000L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(2000L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(List.of(1000L, 2000L), parsedEventLog.stages().get(0).taskShuffleReadBytes());
        assertEquals(300L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(30L, parsedEventLog.stages().get(0).diskBytesSpilled());
        assertEquals(200L, parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertEquals(20L, parsedEventLog.stages().get(0).maxTaskDiskBytesSpilled());
        assertEquals(List.of(100L, 200L), parsedEventLog.stages().get(0).taskMemoryBytesSpilled());
        assertEquals(List.of(10L, 20L), parsedEventLog.stages().get(0).taskDiskBytesSpilled());
        assertEquals(1, parsedEventLog.stages().get(0).failedTaskAttempts());
        assertEquals(10_000L, parsedEventLog.stages().get(0).failedTaskAttemptDurationMillis());
        assertEquals(List.of("ExceptionFailure"), parsedEventLog.stages().get(0).failedTaskAttemptReasons());
    }

    @Test
    void detectsTaskDurationSkewFromParsedTaskEvents() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                "{\"Event\":\"SparkListenerStageSubmitted\","
                        + "\"Stage Info\":{\"Stage ID\":4,\"Stage Name\":\"skewed shuffle\","
                        + "\"Number of Tasks\":10}}",
                taskEnd(4, 100, 0, 1000),
                taskEnd(4, 101, 0, 1000),
                taskEnd(4, 102, 0, 1000),
                taskEnd(4, 103, 0, 1000),
                taskEnd(4, 104, 0, 1000),
                taskEnd(4, 105, 0, 1000),
                taskEnd(4, 106, 0, 1000),
                taskEnd(4, 107, 0, 1000),
                taskEnd(4, 108, 0, 1000),
                taskEnd(4, 109, 0, 9000)));

        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-task-duration-skew", parsedEventLog.recommendations().get(0).id());
        assertEquals("task_duration_skew", parsedEventLog.bottlenecks().get(0).type());
        assertEquals(4, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(10, parsedEventLog.bottlenecks().get(0).evidence().get("completedTasks"));
        assertEquals(1800L, parsedEventLog.bottlenecks().get(0).evidence().get("avgTaskDurationMillis"));
        assertEquals(9000L, parsedEventLog.bottlenecks().get(0).evidence().get("maxTaskDurationMillis"));
        assertEquals(5.0, parsedEventLog.bottlenecks().get(0).evidence().get("skewRatio"));
    }

    @Test
    void parsesApplicationSummaryFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/minimal-eventlog.json");

        ApplicationSummary summary = parser.parseApplicationSummary(fixture);

        assertEquals("app-20260515120000-0001", summary.appId());
        assertEquals("daily_customer_etl", summary.appName());
        assertEquals(2832000L, summary.durationMillis().orElseThrow());
    }

    @Test
    void parsesSummaryCountsFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/minimal-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(2, parsedEventLog.analysisSummary().stages());
        assertEquals(3, parsedEventLog.analysisSummary().tasks());
        assertEquals(2, parsedEventLog.stages().size());
        assertEquals(0, parsedEventLog.stages().get(0).id());
        assertEquals("scan", parsedEventLog.stages().get(0).name());
        assertEquals(2, parsedEventLog.stages().get(0).taskCount());
        assertEquals(2, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(2000L, parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertEquals(3000L, parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertEquals(2500L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(8000L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(4000L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(5000L, parsedEventLog.stages().get(0).p99TaskShuffleReadBytes());
        assertEquals(List.of(3000L, 5000L), parsedEventLog.stages().get(0).taskShuffleReadBytes());
        assertEquals(300L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(30L, parsedEventLog.stages().get(0).diskBytesSpilled());
        assertEquals(200L, parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertEquals(20L, parsedEventLog.stages().get(0).maxTaskDiskBytesSpilled());
        assertEquals(1, parsedEventLog.stages().get(1).id());
        assertEquals("aggregate", parsedEventLog.stages().get(1).name());
        assertEquals(1, parsedEventLog.stages().get(1).taskCount());
        assertEquals(1, parsedEventLog.stages().get(1).completedTasks());
        assertEquals(3000L, parsedEventLog.stages().get(1).minTaskDurationMillis());
        assertEquals(3000L, parsedEventLog.stages().get(1).maxTaskDurationMillis());
        assertEquals(3000L, parsedEventLog.stages().get(1).avgTaskDurationMillis());
        assertEquals(8000L, parsedEventLog.stages().get(1).shuffleReadBytes());
        assertEquals(8000L, parsedEventLog.stages().get(1).maxTaskShuffleReadBytes());
        assertEquals(8000L, parsedEventLog.stages().get(1).medianTaskShuffleReadBytes());
        assertEquals(8000L, parsedEventLog.stages().get(1).p95TaskShuffleReadBytes());
        assertEquals(8000L, parsedEventLog.stages().get(1).p99TaskShuffleReadBytes());
        assertEquals(List.of(8000L), parsedEventLog.stages().get(1).taskShuffleReadBytes());
        assertEquals(500L, parsedEventLog.stages().get(1).memoryBytesSpilled());
        assertEquals(100L, parsedEventLog.stages().get(1).diskBytesSpilled());
        assertEquals(500L, parsedEventLog.stages().get(1).maxTaskMemoryBytesSpilled());
        assertEquals(100L, parsedEventLog.stages().get(1).maxTaskDiskBytesSpilled());
        assertEquals(0, parsedEventLog.bottlenecks().size());
    }

    @Test
    void parsesTaskDurationSkewFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/skewed-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-skewed-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("skewed_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(10, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(4, parsedEventLog.stages().get(0).id());
        assertEquals("skewed shuffle", parsedEventLog.stages().get(0).name());
        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(1000L, parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertEquals(9000L, parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertEquals(1800L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("task_duration_skew", parsedEventLog.bottlenecks().get(0).type());
        assertEquals(5.0, parsedEventLog.bottlenecks().get(0).evidence().get("skewRatio"));
        assertEquals(19000L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(10000L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(1000L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(10000L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(10000L, parsedEventLog.stages().get(0).p99TaskShuffleReadBytes());
        assertEquals(
                List.of(1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 1000L, 10000L),
                parsedEventLog.stages().get(0).taskShuffleReadBytes());
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-task-duration-skew", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesShufflePartitionSkewFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/shuffle-skewed-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-shuffle-skewed-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("shuffle_skewed_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(10, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(8, parsedEventLog.stages().get(0).id());
        assertEquals("skewed shuffle read", parsedEventLog.stages().get(0).name());
        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(408944640L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(10485760L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).p99TaskShuffleReadBytes());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("shuffle_partition_skew", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("high", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(8, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(30.0, parsedEventLog.bottlenecks().get(0).evidence().get("skewRatio"));
        assertEquals(10485760L, parsedEventLog.bottlenecks().get(0).evidence().get("medianTaskShuffleReadBytes"));
        assertEquals(314572800L, parsedEventLog.bottlenecks().get(0).evidence().get("maxTaskShuffleReadBytes"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("mitigate-shuffle-partition-skew", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesOversizedShufflePartitionsFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/oversized-shuffle-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-oversized-shuffle-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("oversized_shuffle_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(4, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(10, parsedEventLog.stages().get(0).id());
        assertEquals("wide shuffle", parsedEventLog.stages().get(0).name());
        assertEquals(4, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(1258291200L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(314572800L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("oversized_shuffle_partitions", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(10, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(314572800L, parsedEventLog.bottlenecks().get(0).evidence().get("p95TaskShuffleReadBytes"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("reduce-oversized-shuffle-partitions", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesLowShuffleParallelismFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/low-shuffle-parallelism-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-low-shuffle-parallelism-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("low_shuffle_parallelism_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(6, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(11, parsedEventLog.stages().get(0).id());
        assertEquals("low parallelism shuffle", parsedEventLog.stages().get(0).name());
        assertEquals(6, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(1258291200L, parsedEventLog.stages().get(0).shuffleReadBytes());
        assertEquals(209715200L, parsedEventLog.stages().get(0).maxTaskShuffleReadBytes());
        assertEquals(209715200L, parsedEventLog.stages().get(0).medianTaskShuffleReadBytes());
        assertEquals(209715200L, parsedEventLog.stages().get(0).p95TaskShuffleReadBytes());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("low_shuffle_parallelism", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(11, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(1258291200L, parsedEventLog.bottlenecks().get(0).evidence().get("shuffleReadBytes"));
        assertEquals(209715200L, parsedEventLog.bottlenecks().get(0).evidence().get("avgTaskShuffleReadBytes"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("increase-shuffle-parallelism", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesSpillPressureFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/spill-heavy-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-spill-heavy-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("spill_heavy_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(2, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(9, parsedEventLog.stages().get(0).id());
        assertEquals("spill-heavy aggregate", parsedEventLog.stages().get(0).name());
        assertEquals(2, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(134217728L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(314572800L, parsedEventLog.stages().get(0).diskBytesSpilled());
        assertEquals(67108864L, parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertEquals(209715200L, parsedEventLog.stages().get(0).maxTaskDiskBytesSpilled());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("spill_pressure", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(9, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(134217728L, parsedEventLog.bottlenecks().get(0).evidence().get("memoryBytesSpilled"));
        assertEquals(314572800L, parsedEventLog.bottlenecks().get(0).evidence().get("diskBytesSpilled"));
        assertEquals(67108864L, parsedEventLog.bottlenecks().get(0).evidence().get("maxTaskMemoryBytesSpilled"));
        assertEquals(209715200L, parsedEventLog.bottlenecks().get(0).evidence().get("maxTaskDiskBytesSpilled"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("reduce-spill-pressure", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesMemorySpillSkewFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/memory-spill-skew-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-memory-spill-skew-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("memory_spill_skew_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(10, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(14, parsedEventLog.stages().get(0).id());
        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(419430400L, parsedEventLog.stages().get(0).memoryBytesSpilled());
        assertEquals(314572800L, parsedEventLog.stages().get(0).maxTaskMemoryBytesSpilled());
        assertEquals(10485760L, parsedEventLog.stages().get(0).medianTaskMemoryBytesSpilled());
        assertEquals(314572800L, parsedEventLog.stages().get(0).p95TaskMemoryBytesSpilled());
        assertEquals(314572800L, parsedEventLog.stages().get(0).p99TaskMemoryBytesSpilled());
        assertEquals(10, parsedEventLog.stages().get(0).taskMemoryBytesSpilled().size());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("memory_spill_skew", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(14, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(
                10485760L,
                parsedEventLog.bottlenecks().get(0).evidence().get("medianTaskMemoryBytesSpilled"));
        assertEquals(
                314572800L,
                parsedEventLog.bottlenecks().get(0).evidence().get("maxTaskMemoryBytesSpilled"));
        assertEquals(30.0, parsedEventLog.bottlenecks().get(0).evidence().get("skewRatio"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-memory-spill-skew", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesTinyTasksFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/tiny-tasks-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-tiny-tasks-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("tiny_tasks_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(100, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(15, parsedEventLog.stages().get(0).id());
        assertEquals(100, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(200L, parsedEventLog.stages().get(0).minTaskDurationMillis());
        assertEquals(200L, parsedEventLog.stages().get(0).maxTaskDurationMillis());
        assertEquals(200L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
        assertEquals(200L, parsedEventLog.stages().get(0).medianTaskDurationMillis());
        assertEquals(200L, parsedEventLog.stages().get(0).p95TaskDurationMillis());
        assertEquals(200L, parsedEventLog.stages().get(0).p99TaskDurationMillis());
        assertEquals(100, parsedEventLog.stages().get(0).taskDurationMillis().size());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("too_many_tiny_tasks", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(15, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(100, parsedEventLog.bottlenecks().get(0).evidence().get("completedTasks"));
        assertEquals(200L, parsedEventLog.bottlenecks().get(0).evidence().get("avgTaskDurationMillis"));
        assertEquals(200L, parsedEventLog.bottlenecks().get(0).evidence().get("p95TaskDurationMillis"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("reduce-tiny-task-overhead", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesRetryWasteFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/retry-waste-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-retry-waste-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("retry_waste_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(3, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(16, parsedEventLog.stages().get(0).id());
        assertEquals(3, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(3, parsedEventLog.stages().get(0).failedTaskAttempts());
        assertEquals(30_000L, parsedEventLog.stages().get(0).failedTaskAttemptDurationMillis());
        assertEquals(
                List.of("ExceptionFailure", "ExecutorLostFailure"),
                parsedEventLog.stages().get(0).failedTaskAttemptReasons());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("retry_waste", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("medium", parsedEventLog.bottlenecks().get(0).severity());
        assertEquals(16, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(3, parsedEventLog.bottlenecks().get(0).evidence().get("failedTaskAttempts"));
        assertEquals(30_000L, parsedEventLog.bottlenecks().get(0).evidence().get("failedTaskAttemptDurationMillis"));
        assertEquals(
                List.of("ExceptionFailure", "ExecutorLostFailure"),
                parsedEventLog.bottlenecks().get(0).evidence().get("failedTaskAttemptReasons"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-retry-waste", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void parsesWorkerImbalanceFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/worker-imbalanced-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-worker-imbalanced-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("worker_imbalanced_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(10, parsedEventLog.analysisSummary().tasks());
        assertEquals(2, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(17, parsedEventLog.stages().get(0).id());
        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(2, parsedEventLog.stages().get(0).executorSummaries().size());
        assertEquals("executor-1", parsedEventLog.stages().get(0).executorSummaries().get(0).id());
        assertEquals(8, parsedEventLog.stages().get(0).executorSummaries().get(0).taskCount());
        assertEquals(8000L, parsedEventLog.stages().get(0).executorSummaries().get(0).taskDurationMillis());
        assertEquals(0.8, parsedEventLog.stages().get(0).executorSummaries().get(0).taskShare());
        assertEquals(0.8, parsedEventLog.stages().get(0).executorSummaries().get(0).durationShare());
        assertEquals(2, parsedEventLog.stages().get(0).hostSummaries().size());
        assertEquals("host-a", parsedEventLog.stages().get(0).hostSummaries().get(0).id());
        assertEquals(2, parsedEventLog.bottlenecks().size());
        assertEquals("executor_imbalance", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("host_imbalance", parsedEventLog.bottlenecks().get(1).type());
        assertEquals("executor-1", parsedEventLog.bottlenecks().get(0).evidence().get("executorId"));
        assertEquals("host-a", parsedEventLog.bottlenecks().get(1).evidence().get("host"));
        assertEquals(2, parsedEventLog.recommendations().size());
        assertEquals("investigate-executor-imbalance", parsedEventLog.recommendations().get(0).id());
        assertEquals("investigate-host-imbalance", parsedEventLog.recommendations().get(1).id());
    }

    @Test
    void parsesFailedJobAndStageFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/failed-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("app-failed-0001", parsedEventLog.applicationSummary().appId());
        assertEquals("failed_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.analysisSummary().jobs());
        assertEquals(0, parsedEventLog.analysisSummary().jobsCompleted());
        assertEquals(1, parsedEventLog.analysisSummary().jobsFailed());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(0, parsedEventLog.analysisSummary().stagesCompleted());
        assertEquals(1, parsedEventLog.analysisSummary().stagesFailed());
        assertEquals(0, parsedEventLog.analysisSummary().tasks());
        assertEquals(2, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.failedJobs().size());
        assertEquals(12, parsedEventLog.failedJobs().get(0).id());
        assertEquals("JobFailed", parsedEventLog.failedJobs().get(0).result());
        assertEquals(1, parsedEventLog.failedStages().size());
        assertEquals(13, parsedEventLog.failedStages().get(0).id());
        assertEquals("failed shuffle", parsedEventLog.failedStages().get(0).name());
        assertEquals(
                "Fetch failed: executor lost during shuffle read",
                parsedEventLog.failedStages().get(0).failureReason());
        assertEquals(2, parsedEventLog.failedStages().get(0).failedTaskAttempts());
        assertEquals(4500L, parsedEventLog.failedStages().get(0).failedTaskAttemptDurationMillis());
        assertEquals(
                List.of("FetchFailed", "ExecutorLostFailure"),
                parsedEventLog.failedStages().get(0).failedTaskAttemptReasons());
        assertEquals(2, parsedEventLog.bottlenecks().size());
        assertEquals("failed_job", parsedEventLog.bottlenecks().get(0).type());
        assertEquals("failed_stage", parsedEventLog.bottlenecks().get(1).type());
        assertEquals(2, parsedEventLog.recommendations().size());
        assertEquals("investigate-failed-job", parsedEventLog.recommendations().get(0).id());
        assertEquals("investigate-failed-stage", parsedEventLog.recommendations().get(1).id());
    }

    @Test
    void parsesRealSparkGeneratedFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/real-spark-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("local-sparkdoctor-fixture", parsedEventLog.applicationSummary().appId());
        assertEquals("sparkdoctor_real_fixture", parsedEventLog.applicationSummary().appName());
        assertEquals(4, parsedEventLog.analysisSummary().jobs());
        assertEquals(4, parsedEventLog.analysisSummary().jobsCompleted());
        assertEquals(0, parsedEventLog.analysisSummary().jobsFailed());
        assertEquals(4, parsedEventLog.analysisSummary().stages());
        assertEquals(4, parsedEventLog.analysisSummary().stagesCompleted());
        assertEquals(0, parsedEventLog.analysisSummary().stagesFailed());
        assertEquals(17, parsedEventLog.analysisSummary().tasks());
        assertEquals(0, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(4, parsedEventLog.stages().size());
        assertEquals(1, parsedEventLog.sqlExecutions().size());
        assertEquals(0L, parsedEventLog.sqlExecutions().get(0).id());
        assertEquals(0L, parsedEventLog.sqlExecutions().get(0).rootExecutionId());
        assertEquals(
                "collect at /tmp/sparkdoctor/scripts/generate-real-spark-eventlog-fixture.py:21",
                parsedEventLog.sqlExecutions().get(0).description());
        assertEquals(960L, parsedEventLog.sqlExecutions().get(0).durationMillis());
        assertTrue(parsedEventLog.sqlExecutions().get(0).physicalPlanDescription().contains("AdaptiveSparkPlan"));
        assertTrue(parsedEventLog.sqlExecutions().get(0).latestPhysicalPlanDescription().contains("Final Plan"));
        assertEquals("AdaptiveSparkPlan", parsedEventLog.sqlExecutions().get(0).sparkPlanInfo().path("nodeName").asText());
        assertEquals(
                "AdaptiveSparkPlan",
                parsedEventLog.sqlExecutions().get(0).latestSparkPlanInfo().path("nodeName").asText());
        assertTrue(parsedEventLog.sqlExecutions().get(0).operatorSummaries().stream()
                .anyMatch(operator -> "Exchange".equals(operator.name()) && operator.count() == 2));
        assertTrue(parsedEventLog.sqlExecutions().get(0).operatorSummaries().stream()
                .anyMatch(operator -> "AQEShuffleRead".equals(operator.name()) && operator.count() == 1));
        assertEquals("4", parsedEventLog.sqlExecutions().get(0).sqlMetricValues().get(136L));
        assertEquals("626", parsedEventLog.sqlExecutions().get(0).sqlMetricValues().get(204L));
        assertEquals(0, parsedEventLog.failedJobs().size());
        assertEquals(0, parsedEventLog.failedStages().size());
        assertEquals(0, parsedEventLog.bottlenecks().size());
        assertEquals(0, parsedEventLog.recommendations().size());
        assertTrue(parsedEventLog.applicationSummary().durationMillis().orElseThrow() > 0);
        assertTrue(parsedEventLog.stages().stream().anyMatch(stage -> stage.completedTasks() == 8));
        assertTrue(parsedEventLog.stages().stream().anyMatch(stage -> stage.shuffleReadBytes() > 0));
        assertTrue(parsedEventLog.stages().stream().anyMatch(stage -> !stage.taskShuffleReadBytes().isEmpty()));
    }

    @Test
    void detectsSqlPlanWithManyExchangeOperatorsFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/sql-many-exchanges-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("sql_many_exchanges_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(1, parsedEventLog.sqlExecutions().size());
        assertEquals(9L, parsedEventLog.sqlExecutions().get(0).id());
        assertTrue(parsedEventLog.sqlExecutions().get(0).operatorSummaries().stream()
                .anyMatch(operator -> "Exchange".equals(operator.name()) && operator.count() == 4));
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("sql_many_exchanges", parsedEventLog.bottlenecks().get(0).type());
        assertEquals(-1, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(9L, parsedEventLog.bottlenecks().get(0).evidence().get("sqlExecutionId"));
        assertEquals(4, parsedEventLog.bottlenecks().get(0).evidence().get("exchangeCount"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-sql-many-exchanges", parsedEventLog.recommendations().get(0).id());
    }

    @Test
    void detectsSpeculationHeavyStageFromFixtureFile() throws Exception {
        Path fixture = Path.of("src/test/resources/fixtures/speculation-heavy-eventlog.json");

        ParsedEventLog parsedEventLog = parser.parse(fixture);

        assertEquals("speculation_heavy_customer_etl", parsedEventLog.applicationSummary().appName());
        assertEquals(10, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().issuesDetected());
        assertEquals(1, parsedEventLog.stages().size());
        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(3, parsedEventLog.stages().get(0).speculativeTaskAttempts());
        assertEquals(3000L, parsedEventLog.stages().get(0).speculativeTaskAttemptDurationMillis());
        assertEquals(3, parsedEventLog.stages().get(0).duplicateSuccessfulTaskAttempts());
        assertEquals(1, parsedEventLog.bottlenecks().size());
        assertEquals("speculation_heavy", parsedEventLog.bottlenecks().get(0).type());
        assertEquals(18, parsedEventLog.bottlenecks().get(0).stageId());
        assertEquals(0.3, parsedEventLog.bottlenecks().get(0).evidence().get("speculativeAttemptShare"));
        assertEquals(1, parsedEventLog.recommendations().size());
        assertEquals("investigate-speculation-heavy-stage", parsedEventLog.recommendations().get(0).id());
    }

    private String taskEnd(int stageId, long taskId, long launchTimeMillis, long finishTimeMillis) {
        return ("{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":%d,"
                + "\"Task Info\":{\"Task ID\":%d,\"Launch Time\":%d,\"Finish Time\":%d}}"
        ).formatted(stageId, taskId, launchTimeMillis, finishTimeMillis);
    }

    private String taskEndWithShuffleRead(
            int stageId,
            long taskId,
            long launchTimeMillis,
            long finishTimeMillis,
            long localBytesRead,
            long remoteBytesRead) {
        return ("{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":%d,"
                + "\"Task Info\":{\"Task ID\":%d,\"Launch Time\":%d,\"Finish Time\":%d},"
                + "\"Task Metrics\":{\"Shuffle Read Metrics\":{"
                + "\"Local Bytes Read\":%d,\"Remote Bytes Read\":%d}}}"
        ).formatted(stageId, taskId, launchTimeMillis, finishTimeMillis, localBytesRead, remoteBytesRead);
    }

    private String taskEndWithSpill(
            int stageId,
            long taskId,
            long launchTimeMillis,
            long finishTimeMillis,
            long memoryBytesSpilled,
            long diskBytesSpilled) {
        return ("{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":%d,"
                + "\"Task Info\":{\"Task ID\":%d,\"Launch Time\":%d,\"Finish Time\":%d},"
                + "\"Task Metrics\":{\"Memory Bytes Spilled\":%d,\"Disk Bytes Spilled\":%d}}"
        ).formatted(stageId, taskId, launchTimeMillis, finishTimeMillis, memoryBytesSpilled, diskBytesSpilled);
    }

    private String taskEndAttempt(
            int stageId,
            int stageAttemptId,
            long taskId,
            long taskIndex,
            boolean successful,
            long launchTimeMillis,
            long finishTimeMillis,
            long shuffleReadBytes,
            long memoryBytesSpilled,
            long diskBytesSpilled,
            long remoteBytesRead) {
        String reason = successful ? "Success" : "ExceptionFailure";
        return ("{\"Event\":\"SparkListenerTaskEnd\",\"Stage ID\":%d,\"Stage Attempt ID\":%d,"
                + "\"Task Info\":{\"Task ID\":%d,\"Index\":%d,\"Launch Time\":%d,\"Finish Time\":%d,"
                + "\"Successful\":%s},"
                + "\"Task End Reason\":{\"Reason\":\"%s\"},"
                + "\"Task Metrics\":{\"Memory Bytes Spilled\":%d,\"Disk Bytes Spilled\":%d,"
                + "\"Shuffle Read Metrics\":{\"Local Bytes Read\":%d,\"Remote Bytes Read\":%d}}}"
        ).formatted(
                stageId,
                stageAttemptId,
                taskId,
                taskIndex,
                launchTimeMillis,
                finishTimeMillis,
                successful,
                reason,
                memoryBytesSpilled,
                diskBytesSpilled,
                shuffleReadBytes - remoteBytesRead,
                remoteBytesRead);
    }
}

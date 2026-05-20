package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
}

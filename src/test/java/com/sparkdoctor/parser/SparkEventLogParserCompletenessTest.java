package com.sparkdoctor.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sparkdoctor.model.ParsedEventLog;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class SparkEventLogParserCompletenessTest {
    private final SparkEventLogParser parser = new SparkEventLogParser();

    @Test
    void countsSuccessfulTaskWithoutDurationAsCompleted() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                applicationStart(),
                stageSubmitted(7, 1),
                successfulTaskWithoutTiming(7, 0, 100)));

        assertEquals(1, parsedEventLog.analysisSummary().tasks());
        assertEquals(1, parsedEventLog.analysisSummary().stages());
        StageAnalysis stage = parsedEventLog.stages().get(0);
        assertEquals(1, stage.completedTasks());
        assertTrue(stage.taskDurationMillis().isEmpty());
        assertNull(stage.avgTaskDurationMillis());
        assertEquals(100L, stage.shuffleReadBytes());
        assertEquals(10L, stage.memoryBytesSpilled());
        assertEquals(5L, stage.diskBytesSpilled());
    }

    @Test
    void includesStageDiscoveredOnlyFromTaskEvent() throws Exception {
        ParsedEventLog parsedEventLog = parser.parse(List.of(
                applicationStart(),
                successfulTask(11, 0, 0, 100)));

        assertEquals(1, parsedEventLog.analysisSummary().stages());
        assertEquals(1, parsedEventLog.analysisSummary().tasks());
        assertEquals(11, parsedEventLog.stages().get(0).id());
        assertEquals(1, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(100L, parsedEventLog.stages().get(0).avgTaskDurationMillis());
    }

    @Test
    void incompleteDurationSamplesDoNotTriggerDurationSkew() throws Exception {
        List<String> events = new ArrayList<>();
        events.add(applicationStart());
        events.add(stageSubmitted(12, 10));
        events.add(successfulTask(12, 0, 0, 100));
        events.add(successfulTask(12, 1, 0, 100));
        events.add(successfulTask(12, 2, 0, 100));
        events.add(successfulTask(12, 3, 0, 1000));
        for (int index = 4; index < 10; index++) {
            events.add(successfulTaskWithoutTiming(12, index, 0));
        }

        ParsedEventLog parsedEventLog = parser.parse(events);

        assertEquals(10, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(4, parsedEventLog.stages().get(0).taskDurationMillis().size());
        assertTrue(parsedEventLog.bottlenecks().stream()
                .noneMatch(bottleneck -> "task_duration_skew".equals(bottleneck.type())));
    }

    @Test
    void incompleteDurationSamplesDoNotTriggerTinyTaskFinding() throws Exception {
        List<String> events = new ArrayList<>();
        events.add(applicationStart());
        events.add(stageSubmitted(13, 100));
        for (int index = 0; index < 10; index++) {
            events.add(successfulTask(13, index, 0, 200));
        }
        for (int index = 10; index < 100; index++) {
            events.add(successfulTaskWithoutTiming(13, index, 0));
        }

        ParsedEventLog parsedEventLog = parser.parse(events);

        assertEquals(100, parsedEventLog.stages().get(0).completedTasks());
        assertEquals(10, parsedEventLog.stages().get(0).taskDurationMillis().size());
        assertTrue(parsedEventLog.bottlenecks().stream()
                .noneMatch(bottleneck -> "too_many_tiny_tasks".equals(bottleneck.type())));
    }

    private String applicationStart() {
        return """
                {"Event":"SparkListenerApplicationStart","App ID":"app-1","App Name":"test","Timestamp":0}
                """;
    }

    private String stageSubmitted(int stageId, int taskCount) {
        return """
                {"Event":"SparkListenerStageSubmitted","Stage Info":{"Stage ID":%d,"Stage Attempt ID":0,"Stage Name":"stage-%d","Number of Tasks":%d}}
                """.formatted(stageId, stageId, taskCount);
    }

    private String successfulTask(int stageId, int index, long launchTime, long finishTime) {
        return """
                {"Event":"SparkListenerTaskEnd","Stage ID":%d,"Stage Attempt ID":0,"Task Info":{"Task ID":%d,"Index":%d,"Launch Time":%d,"Finish Time":%d,"Successful":true},"Task End Reason":{"Reason":"Success"}}
                """.formatted(stageId, index, index, launchTime, finishTime);
    }

    private String successfulTaskWithoutTiming(int stageId, int index, long shuffleReadBytes) {
        return """
                {"Event":"SparkListenerTaskEnd","Stage ID":%d,"Stage Attempt ID":0,"Task Info":{"Task ID":%d,"Index":%d,"Successful":true},"Task End Reason":{"Reason":"Success"},"Task Metrics":{"Shuffle Read Metrics":{"Local Bytes Read":0,"Remote Bytes Read":%d},"Memory Bytes Spilled":10,"Disk Bytes Spilled":5}}
                """.formatted(stageId, index, index, shuffleReadBytes);
    }
}

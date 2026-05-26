package com.sparkdoctor.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.analysis.RecommendationEngine;
import com.sparkdoctor.analysis.ShufflePartitionSkewDetector;
import com.sparkdoctor.analysis.SpillPressureDetector;
import com.sparkdoctor.analysis.TaskDurationSkewDetector;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.ParsedEventLog;
import com.sparkdoctor.model.StageAnalysis;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class SparkEventLogParser {
    private static final String APPLICATION_START = "SparkListenerApplicationStart";
    private static final String APPLICATION_END = "SparkListenerApplicationEnd";
    private static final String JOB_START = "SparkListenerJobStart";
    private static final String JOB_END = "SparkListenerJobEnd";
    private static final String STAGE_SUBMITTED = "SparkListenerStageSubmitted";
    private static final String STAGE_COMPLETED = "SparkListenerStageCompleted";
    private static final String TASK_END = "SparkListenerTaskEnd";

    private final EventLogReader eventLogReader;
    private final ObjectMapper objectMapper;
    private final TaskDurationSkewDetector taskDurationSkewDetector;
    private final ShufflePartitionSkewDetector shufflePartitionSkewDetector;
    private final SpillPressureDetector spillPressureDetector;
    private final RecommendationEngine recommendationEngine;

    public SparkEventLogParser() {
        this(
                new EventLogReader(),
                new ObjectMapper(),
                new TaskDurationSkewDetector(),
                new ShufflePartitionSkewDetector(),
                new SpillPressureDetector(),
                new RecommendationEngine());
    }

    SparkEventLogParser(
            EventLogReader eventLogReader,
            ObjectMapper objectMapper,
            TaskDurationSkewDetector taskDurationSkewDetector,
            ShufflePartitionSkewDetector shufflePartitionSkewDetector,
            SpillPressureDetector spillPressureDetector,
            RecommendationEngine recommendationEngine) {
        this.eventLogReader = eventLogReader;
        this.objectMapper = objectMapper;
        this.taskDurationSkewDetector = taskDurationSkewDetector;
        this.shufflePartitionSkewDetector = shufflePartitionSkewDetector;
        this.spillPressureDetector = spillPressureDetector;
        this.recommendationEngine = recommendationEngine;
    }

    public ApplicationSummary parseApplicationSummary(Path eventLogPath) throws IOException {
        return parse(eventLogPath).applicationSummary();
    }

    public ApplicationSummary parseApplicationSummary(List<String> eventLines) throws IOException {
        return parse(eventLines).applicationSummary();
    }

    public ParsedEventLog parse(Path eventLogPath) throws IOException {
        try (Stream<String> lines = eventLogReader.lines(eventLogPath)) {
            return parse(lines);
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    public ParsedEventLog parse(List<String> eventLines) throws IOException {
        return parse(eventLines.stream());
    }

    public ParsedEventLog parse(Stream<String> eventLines) throws IOException {
        String appId = null;
        String appName = null;
        Long startTimeMillis = null;
        Long endTimeMillis = null;
        Set<Integer> jobIds = new HashSet<>();
        Set<Integer> completedJobIds = new HashSet<>();
        Set<Integer> failedJobIds = new HashSet<>();
        Set<Integer> stageIds = new HashSet<>();
        Set<Integer> completedStageIds = new HashSet<>();
        Set<Integer> failedStageIds = new HashSet<>();
        Set<String> taskKeys = new HashSet<>();
        Map<Integer, StageAccumulator> stages = new LinkedHashMap<>();
        Map<TaskAttemptKey, ParsedTaskAttempt> successfulTaskAttempts = new LinkedHashMap<>();

        var iterator = eventLines.iterator();
        while (iterator.hasNext()) {
            String eventLine = iterator.next();
            JsonNode event = objectMapper.readTree(eventLine);
            String eventType = event.path("Event").asText();

            if (APPLICATION_START.equals(eventType)) {
                appId = textOrNull(event, "App ID");
                appName = textOrNull(event, "App Name");
                startTimeMillis = longOrNull(event, "Timestamp");
            } else if (APPLICATION_END.equals(eventType)) {
                endTimeMillis = longOrNull(event, "Timestamp");
            } else if (JOB_START.equals(eventType)) {
                Integer jobId = intOrNull(event, "Job ID");
                if (jobId != null) {
                    jobIds.add(jobId);
                }
            } else if (JOB_END.equals(eventType)) {
                Integer jobId = intOrNull(event, "Job ID");
                if (jobId != null) {
                    jobIds.add(jobId);
                    if (isSuccessfulJobEnd(event)) {
                        completedJobIds.add(jobId);
                    } else {
                        failedJobIds.add(jobId);
                    }
                }
            } else if (STAGE_SUBMITTED.equals(eventType)) {
                JsonNode stageInfo = event.get("Stage Info");
                Integer stageId = stageId(event, stageInfo);
                if (stageId != null) {
                    stageIds.add(stageId);
                    stages.computeIfAbsent(stageId, StageAccumulator::new)
                            .updateDetails(
                                    textOrNull(stageInfo, "Stage Name"),
                                    intOrNull(stageInfo, "Number of Tasks"));
                }
            } else if (STAGE_COMPLETED.equals(eventType)) {
                JsonNode stageInfo = event.get("Stage Info");
                Integer stageId = stageId(event, stageInfo);
                if (stageId != null) {
                    stageIds.add(stageId);
                    stages.computeIfAbsent(stageId, StageAccumulator::new)
                            .updateDetails(
                                    textOrNull(stageInfo, "Stage Name"),
                                    intOrNull(stageInfo, "Number of Tasks"));
                    if (isSuccessfulStageCompleted(stageInfo)) {
                        completedStageIds.add(stageId);
                    } else {
                        failedStageIds.add(stageId);
                    }
                }
            } else if (TASK_END.equals(eventType)) {
                Integer stageId = stageId(event, event.get("Stage Info"));
                Integer stageAttemptId = stageAttemptId(event, event.get("Stage Info"));
                Long taskIndex = taskIndex(event);
                Long taskId = taskId(event);
                if (!isSuccessfulTaskEnd(event)) {
                    continue;
                }
                String taskKey = taskKey(stageId, stageAttemptId, taskIndex, taskId);
                if (taskKey != null) {
                    taskKeys.add(taskKey);
                }
                if (stageId == null || taskIndex == null) {
                    continue;
                }

                Long taskDurationMillis = taskDurationMillis(event);
                Long shuffleReadBytes = shuffleReadBytes(event);
                TaskSpillMetrics spillMetrics = spillMetrics(event);
                successfulTaskAttempts.put(
                        new TaskAttemptKey(stageId, stageAttemptId == null ? 0 : stageAttemptId, taskIndex),
                        new ParsedTaskAttempt(stageId, taskDurationMillis, shuffleReadBytes, spillMetrics));
            }
        }

        for (ParsedTaskAttempt taskAttempt : successfulTaskAttempts.values()) {
            StageAccumulator stage = stages.computeIfAbsent(taskAttempt.stageId(), StageAccumulator::new);
            if (taskAttempt.taskDurationMillis() != null) {
                stage.addTaskDuration(taskAttempt.taskDurationMillis());
            }
            if (taskAttempt.shuffleReadBytes() != null) {
                stage.addShuffleReadBytes(taskAttempt.shuffleReadBytes());
            }
            if (taskAttempt.spillMetrics() != null) {
                stage.addSpillBytes(
                        taskAttempt.spillMetrics().memoryBytesSpilled(),
                        taskAttempt.spillMetrics().diskBytesSpilled());
            }
        }

        List<StageAnalysis> stageAnalyses = stages.values().stream()
                .map(StageAccumulator::toStageAnalysis)
                .toList();
        List<Bottleneck> bottlenecks = new ArrayList<>();
        bottlenecks.addAll(taskDurationSkewDetector.detect(stageAnalyses));
        bottlenecks.addAll(shufflePartitionSkewDetector.detect(stageAnalyses));
        bottlenecks.addAll(spillPressureDetector.detect(stageAnalyses));

        return new ParsedEventLog(
                new ApplicationSummary(appId, appName, startTimeMillis, endTimeMillis),
                new AnalysisSummary(
                        jobIds.size(),
                        completedJobIds.size(),
                        failedJobIds.size(),
                        stageIds.size(),
                        completedStageIds.size(),
                        failedStageIds.size(),
                        taskKeys.size(),
                        bottlenecks.size()),
                stageAnalyses,
                bottlenecks,
                recommendationEngine.recommend(bottlenecks));
    }

    private String textOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    private Integer intOrNull(JsonNode node, String fieldName) {
        if (node == null || node.isNull()) {
            return null;
        }

        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asInt();
    }

    private Integer stageId(JsonNode event, JsonNode stageInfo) {
        Integer directStageId = intOrNull(event, "Stage ID");
        if (directStageId != null) {
            return directStageId;
        }

        if (stageInfo == null || stageInfo.isNull()) {
            return null;
        }

        return intOrNull(stageInfo, "Stage ID");
    }

    private Long taskId(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        return longOrNull(taskInfo, "Task ID");
    }

    private Long taskIndex(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        Long taskIndex = longOrNull(taskInfo, "Index");
        if (taskIndex != null) {
            return taskIndex;
        }

        return longOrNull(taskInfo, "Task ID");
    }

    private Integer stageAttemptId(JsonNode event, JsonNode stageInfo) {
        Integer directStageAttemptId = intOrNull(event, "Stage Attempt ID");
        if (directStageAttemptId != null) {
            return directStageAttemptId;
        }

        if (stageInfo == null || stageInfo.isNull()) {
            return null;
        }

        return intOrNull(stageInfo, "Stage Attempt ID");
    }

    private boolean isSuccessfulTaskEnd(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo != null && taskInfo.has("Successful") && !taskInfo.path("Successful").asBoolean()) {
            return false;
        }

        JsonNode taskEndReason = event.get("Task End Reason");
        if (taskEndReason == null || taskEndReason.isNull()) {
            return true;
        }

        String reason = textOrNull(taskEndReason, "Reason");
        return reason == null || "Success".equals(reason);
    }

    private boolean isSuccessfulJobEnd(JsonNode event) {
        JsonNode jobResult = event.get("Job Result");
        if (jobResult == null || jobResult.isNull()) {
            return false;
        }

        return "JobSucceeded".equals(textOrNull(jobResult, "Result"));
    }

    private boolean isSuccessfulStageCompleted(JsonNode stageInfo) {
        return textOrNull(stageInfo, "Failure Reason") == null;
    }

    private String taskKey(Integer stageId, Integer stageAttemptId, Long taskIndex, Long taskId) {
        if (stageId != null && taskIndex != null) {
            return stageId + ":" + (stageAttemptId == null ? 0 : stageAttemptId) + ":" + taskIndex;
        }
        if (taskId != null) {
            return "task-id:" + taskId;
        }

        return null;
    }

    private Long taskDurationMillis(JsonNode event) {
        JsonNode taskInfo = event.get("Task Info");
        if (taskInfo == null || taskInfo.isNull()) {
            return null;
        }

        Long launchTimeMillis = longOrNull(taskInfo, "Launch Time");
        Long finishTimeMillis = longOrNull(taskInfo, "Finish Time");
        if (launchTimeMillis == null || finishTimeMillis == null) {
            return null;
        }

        return finishTimeMillis - launchTimeMillis;
    }

    private Long shuffleReadBytes(JsonNode event) {
        JsonNode taskMetrics = event.get("Task Metrics");
        if (taskMetrics == null || taskMetrics.isNull()) {
            return null;
        }

        JsonNode shuffleReadMetrics = taskMetrics.get("Shuffle Read Metrics");
        if (shuffleReadMetrics == null || shuffleReadMetrics.isNull()) {
            return null;
        }

        Long localBytesRead = longOrNull(shuffleReadMetrics, "Local Bytes Read");
        Long remoteBytesRead = longOrNull(shuffleReadMetrics, "Remote Bytes Read");
        return valueOrZero(localBytesRead) + valueOrZero(remoteBytesRead);
    }

    private TaskSpillMetrics spillMetrics(JsonNode event) {
        JsonNode taskMetrics = event.get("Task Metrics");
        if (taskMetrics == null || taskMetrics.isNull()) {
            return null;
        }

        Long memoryBytesSpilled = longOrNull(taskMetrics, "Memory Bytes Spilled");
        Long diskBytesSpilled = longOrNull(taskMetrics, "Disk Bytes Spilled");
        if (memoryBytesSpilled == null && diskBytesSpilled == null) {
            return null;
        }

        return new TaskSpillMetrics(valueOrZero(memoryBytesSpilled), valueOrZero(diskBytesSpilled));
    }

    private long valueOrZero(Long value) {
        return value == null ? 0L : value;
    }

    private record TaskSpillMetrics(long memoryBytesSpilled, long diskBytesSpilled) {}

    private record TaskAttemptKey(int stageId, int stageAttemptId, long taskIndex) {}

    private record ParsedTaskAttempt(
            int stageId,
            Long taskDurationMillis,
            Long shuffleReadBytes,
            TaskSpillMetrics spillMetrics) {}
}

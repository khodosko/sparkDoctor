package com.sparkdoctor.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.AnalysisSummary;
import com.sparkdoctor.model.ApplicationSummary;
import com.sparkdoctor.model.ParsedEventLog;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SparkEventLogParser {
    private static final String APPLICATION_START = "SparkListenerApplicationStart";
    private static final String APPLICATION_END = "SparkListenerApplicationEnd";
    private static final String JOB_START = "SparkListenerJobStart";
    private static final String STAGE_SUBMITTED = "SparkListenerStageSubmitted";
    private static final String TASK_END = "SparkListenerTaskEnd";

    private final EventLogReader eventLogReader;
    private final ObjectMapper objectMapper;

    public SparkEventLogParser() {
        this(new EventLogReader(), new ObjectMapper());
    }

    SparkEventLogParser(EventLogReader eventLogReader, ObjectMapper objectMapper) {
        this.eventLogReader = eventLogReader;
        this.objectMapper = objectMapper;
    }

    public ApplicationSummary parseApplicationSummary(Path eventLogPath) throws IOException {
        return parseApplicationSummary(eventLogReader.readLines(eventLogPath));
    }

    public ApplicationSummary parseApplicationSummary(List<String> eventLines) throws IOException {
        return parse(eventLines).applicationSummary();
    }

    public ParsedEventLog parse(Path eventLogPath) throws IOException {
        return parse(eventLogReader.readLines(eventLogPath));
    }

    public ParsedEventLog parse(List<String> eventLines) throws IOException {
        String appId = null;
        String appName = null;
        Long startTimeMillis = null;
        Long endTimeMillis = null;
        Set<Integer> jobIds = new HashSet<>();
        Set<Integer> stageIds = new HashSet<>();
        Set<Long> taskIds = new HashSet<>();

        for (String eventLine : eventLines) {
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
            } else if (STAGE_SUBMITTED.equals(eventType)) {
                Integer stageId = stageId(event);
                if (stageId != null) {
                    stageIds.add(stageId);
                }
            } else if (TASK_END.equals(eventType)) {
                Long taskId = taskId(event);
                if (taskId != null) {
                    taskIds.add(taskId);
                }
            }
        }

        return new ParsedEventLog(
                new ApplicationSummary(appId, appName, startTimeMillis, endTimeMillis),
                new AnalysisSummary(jobIds.size(), stageIds.size(), taskIds.size(), 0));
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asText();
    }

    private Long longOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asLong();
    }

    private Integer intOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }

        return value.asInt();
    }

    private Integer stageId(JsonNode event) {
        Integer directStageId = intOrNull(event, "Stage ID");
        if (directStageId != null) {
            return directStageId;
        }

        JsonNode stageInfo = event.get("Stage Info");
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
}

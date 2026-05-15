package com.sparkdoctor.parser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sparkdoctor.model.ApplicationSummary;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public final class SparkEventLogParser {
    private static final String APPLICATION_START = "SparkListenerApplicationStart";
    private static final String APPLICATION_END = "SparkListenerApplicationEnd";

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
        String appId = null;
        String appName = null;
        Long startTimeMillis = null;
        Long endTimeMillis = null;

        for (String eventLine : eventLines) {
            JsonNode event = objectMapper.readTree(eventLine);
            String eventType = event.path("Event").asText();

            if (APPLICATION_START.equals(eventType)) {
                appId = textOrNull(event, "App ID");
                appName = textOrNull(event, "App Name");
                startTimeMillis = longOrNull(event, "Timestamp");
            } else if (APPLICATION_END.equals(eventType)) {
                endTimeMillis = longOrNull(event, "Timestamp");
            }
        }

        return new ApplicationSummary(appId, appName, startTimeMillis, endTimeMillis);
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
}


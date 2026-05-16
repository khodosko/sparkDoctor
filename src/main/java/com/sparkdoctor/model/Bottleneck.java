package com.sparkdoctor.model;

import java.util.Map;

public record Bottleneck(
        String type,
        String severity,
        int stageId,
        String message,
        Map<String, Object> evidence) {}


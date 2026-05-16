package com.sparkdoctor.model;

public record Recommendation(
        String id,
        String severity,
        String title,
        String description,
        String relatedBottleneckType,
        int stageId) {}


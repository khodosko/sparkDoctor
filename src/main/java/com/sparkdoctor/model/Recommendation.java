package com.sparkdoctor.model;

public record Recommendation(
        String id,
        String severity,
        String title,
        String description,
        String relatedBottleneckType,
        int stageId,
        String relatedBottleneckId) {
    public Recommendation(
            String id,
            String severity,
            String title,
            String description,
            String relatedBottleneckType,
            int stageId) {
        this(id, severity, title, description, relatedBottleneckType, stageId, null);
    }
}

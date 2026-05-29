package com.sparkdoctor.model;

public record WorkerSummary(
        String id,
        int taskCount,
        long taskDurationMillis,
        double taskShare,
        double durationShare) {}

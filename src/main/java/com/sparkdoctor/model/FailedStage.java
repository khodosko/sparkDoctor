package com.sparkdoctor.model;

import java.util.List;

public record FailedStage(
        int id,
        String name,
        String failureReason,
        int failedTaskAttempts,
        long failedTaskAttemptDurationMillis,
        List<String> failedTaskAttemptReasons) {
    public FailedStage {
        failedTaskAttemptReasons = failedTaskAttemptReasons == null ? List.of() : List.copyOf(failedTaskAttemptReasons);
    }

    public FailedStage(int id, String name, String failureReason) {
        this(id, name, failureReason, 0, 0L, List.of());
    }
}

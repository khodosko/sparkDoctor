package com.sparkdoctor.analysis;

import com.sparkdoctor.model.Bottleneck;
import com.sparkdoctor.model.StageAnalysis;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class RetryWasteDetector {
    private static final int MIN_FAILED_TASK_ATTEMPTS = 3;
    private static final long MEDIUM_FAILED_ATTEMPT_DURATION_MILLIS = 30_000L;
    private static final long HIGH_FAILED_ATTEMPT_DURATION_MILLIS = 300_000L;

    public List<Bottleneck> detect(List<StageAnalysis> stages) {
        List<Bottleneck> bottlenecks = new ArrayList<>();
        for (StageAnalysis stage : stages) {
            if (hasRetryWaste(stage)) {
                bottlenecks.add(new Bottleneck(
                        "retry_waste",
                        severity(stage),
                        stage.id(),
                        "Stage %d has retry waste from failed task attempts.".formatted(stage.id()),
                        Map.of(
                                "failedTaskAttempts", stage.failedTaskAttempts(),
                                "failedTaskAttemptDurationMillis", stage.failedTaskAttemptDurationMillis(),
                                "failedTaskAttemptReasons", stage.failedTaskAttemptReasons(),
                                "minFailedTaskAttempts", MIN_FAILED_TASK_ATTEMPTS,
                                "mediumFailedAttemptDurationMillis", MEDIUM_FAILED_ATTEMPT_DURATION_MILLIS,
                                "highFailedAttemptDurationMillis", HIGH_FAILED_ATTEMPT_DURATION_MILLIS)));
            }
        }

        return bottlenecks;
    }

    private boolean hasRetryWaste(StageAnalysis stage) {
        return stage.failedTaskAttempts() >= MIN_FAILED_TASK_ATTEMPTS
                && stage.failedTaskAttemptDurationMillis() >= MEDIUM_FAILED_ATTEMPT_DURATION_MILLIS;
    }

    private String severity(StageAnalysis stage) {
        if (stage.failedTaskAttemptDurationMillis() >= HIGH_FAILED_ATTEMPT_DURATION_MILLIS) {
            return "high";
        }

        return "medium";
    }
}
